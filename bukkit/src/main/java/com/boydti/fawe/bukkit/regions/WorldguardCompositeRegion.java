package com.boydti.fawe.bukkit.regions;

import com.boydti.fawe.object.FawePlayer;
import com.boydti.fawe.object.RegionWrapper;
import com.boydti.fawe.regions.FaweMaskManager;
import com.sk89q.worldedit.BlockVector2D;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class WorldguardCompositeRegion extends RegionWrapper {
    private final LocalPlayer localPlayer;
    private final boolean isGloballyAllowed;
    private final List<ProtectedRegion> regions;
    private final FaweMaskManager.MaskType type;

    public WorldguardCompositeRegion(WorldGuardPlugin worldguard, FawePlayer<Player> player, FaweMaskManager.MaskType type, List<ProtectedRegion> regions) {
        super(
                calculateLowerBounds(worldguard, player, type, regions),
                calculateUpperBounds(worldguard, player, type, regions)
        );

        GlobalProtectedRegion global = getGlobalRegion(regions);

        this.regions = regions;
        this.type = type;
        this.localPlayer = worldguard.wrapPlayer(player.parent);
        this.isGloballyAllowed = global != null && isAllowed(localPlayer, global, type);
    }

    @Override
    public boolean isIn(int x, int z) {
        if (!super.isIn(x, z)) {
            return false;
        }

        BlockVector2D vector = new BlockVector2D(x, z);

        for (ProtectedRegion region : this.regions) {
            if (region.contains(vector) && !isAllowed(this.localPlayer, region, this.type)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean intersects(RegionWrapper other) {
        if (!super.intersects(other)) {
            return false;
        }

        ProtectedCuboidRegion fakeRegion = new ProtectedCuboidRegion(
                "__fake_region__", other.getBottomVector().toBlockVector(), other.getTopVector().toBlockVector()
        );

        // Return whether any of the applicable regions intersect with "other"
        List<ProtectedRegion> intersectingRegions = fakeRegion.getIntersectingRegions(this.regions);

        for (ProtectedRegion region : intersectingRegions) {
            if (isAllowed(this.localPlayer, region, this.type)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isGlobal() {
        return this.isGloballyAllowed;
    }

    private static boolean isAllowed(LocalPlayer localplayer, ProtectedRegion region, FaweMaskManager.MaskType type) {
        if (type == FaweMaskManager.MaskType.OWNER && (region.isOwner(localplayer) || region.isOwner(localplayer.getName()))) {
            return true;
        } else if (type == FaweMaskManager.MaskType.MEMBER && (region.isMember(localplayer) || region.isMember(localplayer.getName()))) {
            return true;
        } else if (region.getId().toLowerCase().equals(localplayer.getName().toLowerCase())) {
            return true;
        } else if (region.getId().toLowerCase().contains(localplayer.getName().toLowerCase() + "//")) {
            return true;
        } else if (type == FaweMaskManager.MaskType.OWNER && region.isOwner("*")) {
            return true;
        } else if (type == FaweMaskManager.MaskType.MEMBER && region.isMember("*")) {
            return true;
        }
        return false;
    }

    private static GlobalProtectedRegion getGlobalRegion(List<ProtectedRegion> regions) {
        // Scan through supplied regions for any of type global.  Cast *should* be safe, as unless people add their own
        //   custom region, only GlobalProtectedRegion returns GLOBAL as it's type.
        List<GlobalProtectedRegion> globals = new ArrayList<>();
        for (ProtectedRegion region : regions) {
            if (region.getType() == RegionType.GLOBAL) {
                globals.add((GlobalProtectedRegion) region);
            }
        }

        // If there are no regions, return null; else if only one, return that; else return the highest priority one.
        //   Falls back to "first" ordered ID if there are multiple regions with the same priority.
        if (globals.size() == 0) {
            return null;
        } else if (globals.size() == 1) {
            return globals.get(0);
        }

        int priority = Integer.MIN_VALUE;
        GlobalProtectedRegion global = null;

        for (GlobalProtectedRegion region : globals) {
            if (global == null || region.getPriority() > priority) {
                global = region;
                priority = region.getPriority();
            } else if (region.getPriority() == priority && region.getId().compareToIgnoreCase(global.getId()) < 0) {
                global = region;
            }
        }

        return  global;
    }

    // region Bounds calculation
    private static Vector calculateLowerBounds(WorldGuardPlugin worldguard, FawePlayer<Player> player, FaweMaskManager.MaskType type, List<ProtectedRegion> regions) {
        return calculateBounds(worldguard, player, type, regions, true);
    }

    private static Vector calculateUpperBounds(WorldGuardPlugin worldguard, FawePlayer<Player> player, FaweMaskManager.MaskType type, List<ProtectedRegion> regions) {
        return calculateBounds(worldguard, player, type, regions, false);
    }

    private static Vector calculateBounds(WorldGuardPlugin worldguard, FawePlayer<Player> player, FaweMaskManager.MaskType type, List<ProtectedRegion> regions, boolean calculateLower) {
        LocalPlayer localPlayer = worldguard.wrapPlayer(player.parent);

        GlobalProtectedRegion global = getGlobalRegion(regions);

        if (global != null && isAllowed(localPlayer, global, type)) {
            if (calculateLower) {
                return new Vector(Integer.MIN_VALUE, 0, Integer.MIN_VALUE);
            } else {
                return new Vector(Integer.MAX_VALUE, 255, Integer.MAX_VALUE);
            }
        }

        int x = calculateLower ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        int z = calculateLower ? Integer.MAX_VALUE : Integer.MIN_VALUE;

        for (ProtectedRegion region : regions) {
            if (region.getId().equals("__global__")) {
                continue;
            }

            if (calculateLower && region.getMinimumPoint().getBlockX() < x) {
                x = region.getMinimumPoint().getBlockX();
            } else if (!calculateLower && region.getMaximumPoint().getBlockX() > x) {
                x = region.getMaximumPoint().getBlockX();
            }

            if (calculateLower && region.getMinimumPoint().getBlockZ() < z) {
                z = region.getMinimumPoint().getBlockZ();
            } else if (!calculateLower && region.getMaximumPoint().getBlockZ() > z) {
                z = region.getMaximumPoint().getBlockZ();
            }
        }

        return new Vector(x, calculateLower ? 0 : 255, z);
    }
    // endregion
}