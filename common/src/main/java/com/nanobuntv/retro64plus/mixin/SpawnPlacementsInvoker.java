package com.nanobuntv.retro64plus.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's spawn placement registration is private; Fabric has no API for it (NeoForge has its event). */
@Mixin(SpawnPlacements.class)
public interface SpawnPlacementsInvoker {
    @Invoker("register")
    static <T extends Mob> void retro64plus$register(EntityType<T> type, SpawnPlacementType placement,
            Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<T> predicate) {
        throw new AssertionError();
    }
}
