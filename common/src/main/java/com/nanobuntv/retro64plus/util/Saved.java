package com.nanobuntv.retro64plus.util;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
//? if >=1.21.6 {
/*import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
*///?} else {
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
//?}

/**
 * An entity's saved data on every Minecraft version (Porting.md): a CompoundTag up to 1.21.5, ValueInput / ValueOutput
 * from 1.21.6. The entities save through {@link Out} and load through {@link In}; only their two overrides differ.
 */
public final class Saved {
    private Saved() {}

    public interface In {
        int getInt(String key, int fallback);

        boolean getBoolean(String key, boolean fallback);

        Optional<String> getString(String key);

        Optional<BlockPos> getBlockPos(String key);

        Optional<UUID> getUuid(String key);
    }

    public interface Out {
        void putInt(String key, int value);

        void putBoolean(String key, boolean value);

        void putString(String key, String value);

        void putBlockPos(String key, BlockPos value);

        void putUuid(String key, UUID value);
    }

    //? if >=1.21.6 {
    /*public static In in(ValueInput input) {
        return new In() {
            public int getInt(String key, int fallback) {
                return input.getIntOr(key, fallback);
            }

            public boolean getBoolean(String key, boolean fallback) {
                return input.getBooleanOr(key, fallback);
            }

            public Optional<String> getString(String key) {
                return input.getString(key);
            }

            public Optional<BlockPos> getBlockPos(String key) {
                return input.read(key, BlockPos.CODEC);
            }

            public Optional<UUID> getUuid(String key) {
                return input.read(key, UUIDUtil.CODEC);
            }
        };
    }

    public static Out out(ValueOutput output) {
        return new Out() {
            public void putInt(String key, int value) {
                output.putInt(key, value);
            }

            public void putBoolean(String key, boolean value) {
                output.putBoolean(key, value);
            }

            public void putString(String key, String value) {
                output.putString(key, value);
            }

            public void putBlockPos(String key, BlockPos value) {
                output.store(key, BlockPos.CODEC, value);
            }

            public void putUuid(String key, UUID value) {
                output.store(key, UUIDUtil.CODEC, value);
            }
        };
    }
    *///?} else {
    public static In in(CompoundTag tag) {
        return new In() {
            public int getInt(String key, int fallback) {
                return tag.contains(key) ? tag.getInt(key) : fallback;
            }

            public boolean getBoolean(String key, boolean fallback) {
                return tag.contains(key) ? tag.getBoolean(key) : fallback;
            }

            public Optional<String> getString(String key) {
                return tag.contains(key) ? Optional.of(tag.getString(key)) : Optional.empty();
            }

            public Optional<BlockPos> getBlockPos(String key) {
                return tag.contains(key) ? BlockPos.CODEC.parse(NbtOps.INSTANCE, tag.get(key)).result() : Optional.empty();
            }

            public Optional<UUID> getUuid(String key) {
                return tag.contains(key) ? UUIDUtil.CODEC.parse(NbtOps.INSTANCE, tag.get(key)).result() : Optional.empty();
            }
        };
    }

    public static Out out(CompoundTag tag) {
        return new Out() {
            public void putInt(String key, int value) {
                tag.putInt(key, value);
            }

            public void putBoolean(String key, boolean value) {
                tag.putBoolean(key, value);
            }

            public void putString(String key, String value) {
                tag.putString(key, value);
            }

            public void putBlockPos(String key, BlockPos value) {
                BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, value).result().ifPresent(t -> tag.put(key, t));
            }

            public void putUuid(String key, UUID value) {
                UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, value).result().ifPresent(t -> tag.put(key, t));
            }
        };
    }
    //?}
}
