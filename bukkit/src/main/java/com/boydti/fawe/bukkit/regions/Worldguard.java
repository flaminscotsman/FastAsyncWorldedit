package com.boydti.fawe.bukkit.regions;

import com.boydti.fawe.bukkit.FaweBukkit;
import com.boydti.fawe.object.FawePlayer;
import com.boydti.fawe.object.RegionWrapper;
import com.boydti.fawe.regions.FaweMask;
import com.boydti.fawe.regions.FaweMaskManager;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.massivecraft.massivecore.collections.Def;
import com.sk89q.worldedit.*;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.*;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class Worldguard extends BukkitMaskManager implements Listener {
    WorldGuardPlugin worldguard;
    FaweBukkit plugin;
    ResolutionOrder order = ResolutionOrder.FAIL_FIRST;

    enum ResolutionOrder {
        FAIL_FIRST,
        SUCCEED_FIRST
    }

    private WorldGuardPlugin getWorldGuard() {
        final Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldGuard");

        // WorldGuard may not be loaded
        if ((plugin == null) || !(plugin instanceof WorldGuardPlugin)) {
            return null; // Maybe you want throw an exception instead
        }

        return (WorldGuardPlugin) plugin;
    }

    public Worldguard(final Plugin p2, final FaweBukkit p3) {
        super(p2.getName());
        this.worldguard = this.getWorldGuard();
        this.plugin = p3;
    }

    private List<ProtectedRegion> getApplicableRegions(final FawePlayer<Player> player, final RegionManager manager) {
        Region raw_selection = player.getSelection();
        ProtectedCuboidRegion selection = new ProtectedCuboidRegion(
                "__internal__",
                raw_selection.getMinimumPoint().toBlockVector(),
                raw_selection.getMaximumPoint().toBlockVector()
        );

        ApplicableRegionSet applicableRegionSet = manager.getApplicableRegions(selection);
        List<ProtectedRegion> regions = new ArrayList<>(applicableRegionSet.size());
        for (ProtectedRegion r: applicableRegionSet) {
            regions.add(r);
        }

        Collections.sort(regions, Collections.reverseOrder(new RegionComparator()));

        return regions;
    }

    public FaweMask getWGRegionMaskMask(final FawePlayer<Player> fp, MaskType type) {
        RegionManager manager = this.worldguard.getRegionManager(fp.parent.getWorld());
        List<ProtectedRegion> regions = getApplicableRegions(fp, manager);
        if (regions.isEmpty()) {
            return null;
        }

        final RegionWrapper region = new WorldguardCompositeRegion(this.worldguard, fp, type, regions);
        return new FaweMask(region.getBottomVector().toBlockVector(), region.getTopVector().toBlockVector()) {
            @Override
            public String getName() {
                return "WorldGuard";
            }

            @Override
            public boolean contains(BlockVector loc) {
                return region.isIn(loc.getBlockX(), loc.getBlockZ());
            }

            @Override
            public HashSet<RegionWrapper> getRegions() {
                return Sets.newHashSet(region);
            }
        };
    }

    public FaweMask getWGNativeMask(final FawePlayer<Player> fp, MaskType type) {
        final RegionManager manager = this.worldguard.getRegionManager(fp.parent.getWorld());
        Region raw_selection = fp.getSelection();
        ProtectedCuboidRegion selection = new ProtectedCuboidRegion(
                "__internal__",
                raw_selection.getMinimumPoint().toBlockVector(),
                raw_selection.getMaximumPoint().toBlockVector()
        );

        final com.sk89q.worldguard.LocalPlayer localPlayer = worldguard.wrapPlayer(fp.parent);
        final ApplicableRegionSet applicableRegionSet = manager.getApplicableRegions(selection);
//        if (!applicableRegionSet.testState(localPlayer, DefaultFlag.BUILD)) {
//            return null;
//        }

        final WorldguardCompositeRegion region = new WorldguardCompositeRegion(this.worldguard, fp, type, Lists.newArrayList(applicableRegionSet.getRegions())) {
            @Override
            public boolean isIn(int x, int z) {
                ProtectedRegion rg = new ProtectedCuboidRegion("__contain_test__", new BlockVector(x, 0, z), new BlockVector(x, 255, z));
                return manager.getApplicableRegions(rg).testState(localPlayer, DefaultFlag.BUILD);
            }
        };

        return new FaweMask(region.getBottomVector().toBlockVector(), region.getTopVector().toBlockVector()) {
            @Override
            public String getName() {
                return "WorldGuard";
            }

            @Override
            public boolean contains(BlockVector loc) {
                return manager.getApplicableRegions(loc).testState(localPlayer, DefaultFlag.BUILD);
            }

            @Override
            public HashSet<RegionWrapper> getRegions() {
                return Sets.newHashSet(((RegionWrapper) region));
            }
        };
    }

    @Override
    public FaweMask getMask(FawePlayer<Player> player, MaskType type) {
        return this.getWGNativeMask(player, type);
    }

    private class RegionComparator implements Comparator<ProtectedRegion> {
        @Override
        public int compare(ProtectedRegion o1, ProtectedRegion o2) {
            int p1 = o1.getPriority();
            int p2 = o2.getPriority();

            return p1 == p2 ? o1.getId().compareTo(o2.getId()) : (new Integer(p1)).compareTo(p2);
        }
    }
}
