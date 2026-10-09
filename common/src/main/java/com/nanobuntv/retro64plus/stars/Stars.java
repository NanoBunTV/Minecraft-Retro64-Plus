package com.nanobuntv.retro64plus.stars;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The Power Star entity's registration and the star ids (D61, D63). An id names where a star came from, so each
 * counts once per player: {@code coins/<dimension>}, {@code boss/<entity type>}, {@code advancement/<advancement>}.
 */
public final class Stars {
    public static Supplier<EntityType<PowerStarEntity>> POWER_STAR;

    /** Mobs whose first kill gives the killer a star (D61). */
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, "star_bosses"));

    private Stars() {}

    /** Common init. */
    public static void register() {
        POWER_STAR = Services.PLATFORM.register(Registries.ENTITY_TYPE, "power_star",
                () -> Compat.build(EntityType.Builder.<PowerStarEntity>of(PowerStarEntity::new, MobCategory.MISC)
                        .sized(0.8f, 0.8f).clientTrackingRange(8).updateInterval(20).fireImmune(), "power_star"));
    }

    public static String coinStar(ResourceLocation dimension) {
        return "coins/" + dimension;
    }

    public static String bossStar(ResourceLocation entityType) {
        return "boss/" + entityType;
    }

    public static String advancementStar(ResourceLocation advancement) {
        return "advancement/" + advancement;
    }
}
