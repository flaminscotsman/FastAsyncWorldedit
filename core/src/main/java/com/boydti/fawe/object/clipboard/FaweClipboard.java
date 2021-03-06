package com.boydti.fawe.object.clipboard;

import com.boydti.fawe.jnbt.NBTStreamer;
import com.boydti.fawe.object.RunnableVal2;
import com.boydti.fawe.util.ReflectionUtils;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.IntTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.util.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;


import static com.google.common.base.Preconditions.checkNotNull;

public abstract class FaweClipboard {
    public abstract BaseBlock getBlock(int x, int y, int z);

    public abstract boolean setBlock(int x, int y, int z, BaseBlock block);

    public abstract void setId(int index, int id);

    public abstract void setData(int index, int data);

    public abstract void setAdd(int index, int id);

    public abstract boolean setTile(int x, int y, int z, CompoundTag tag);

    public abstract Entity createEntity(Extent world, double x, double y, double z, float yaw, float pitch, BaseEntity entity);

    public abstract List<? extends Entity> getEntities();

    public abstract boolean remove(ClipboardEntity clipboardEntity);

    public void setOrigin(Vector offset) {} // Do nothing

    public abstract void setDimensions(Vector dimensions);

    public abstract Vector getDimensions();

    /**
     * The locations provided are relative to the clipboard min
     * @param task
     * @param air
     */
    public abstract void forEach(final RunnableVal2<Vector,BaseBlock> task, boolean air);

    public void streamIds(final NBTStreamer.ByteReader task) {
        forEach(new RunnableVal2<Vector, BaseBlock>() {
            private int index = 0;
            @Override
            public void run(Vector pos, BaseBlock block) {
                task.run(index++, block.getId());
            }
        }, true);
    }

    public void streamDatas(final NBTStreamer.ByteReader task) {
        forEach(new RunnableVal2<Vector, BaseBlock>() {
            private int index = 0;
            @Override
            public void run(Vector pos, BaseBlock block) {
                task.run(index++, block.getData());
            }
        }, true);
    }

    public List<CompoundTag> getTileEntities() {
        final List<CompoundTag> tiles = new ArrayList<>();
        forEach(new RunnableVal2<Vector, BaseBlock>() {
            private int index = 0;
            @Override
            public void run(Vector pos, BaseBlock block) {
                CompoundTag tag = block.getNbtData();
                if (tag != null) {
                    Map<String, Tag> values = ReflectionUtils.getMap(tag.getValue());
                    values.put("x", new IntTag((int) pos.x));
                    values.put("y", new IntTag((int) pos.y));
                    values.put("z", new IntTag((int) pos.z));
                    tiles.add(tag);
                }
            }
        }, false);
        return tiles;
    }

    /**
     * Stores entity data.
     */
    public class ClipboardEntity implements Entity {
        private final BaseEntity entity;
        private final Extent world;
        private final double x,y,z;
        private final float yaw,pitch;

        public ClipboardEntity(Extent world, double x, double y, double z, float yaw, float pitch, BaseEntity entity) {
            checkNotNull(entity);
            checkNotNull(world);
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.entity = new BaseEntity(entity);
        }

        @Override
        public boolean remove() {
            return FaweClipboard.this.remove(this);
        }

        @Nullable
        @Override
        public <T> T getFacet(Class<? extends T> cls) {
            return null;
        }

        /**
         * Get the entity state. This is not a copy.
         *
         * @return the entity
         */
        BaseEntity getEntity() {
            return entity;
        }

        @Override
        public BaseEntity getState() {
            return new BaseEntity(entity);
        }

        @Override
        public Location getLocation() {
            return new Location(world, x, y, z, yaw, pitch);
        }

        @Override
        public Extent getExtent() {
            return world;
        }
    }
}
