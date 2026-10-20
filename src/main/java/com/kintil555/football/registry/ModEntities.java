package com.kintil555.football.registry;

import com.kintil555.football.FootballMod;
import com.kintil555.football.entity.FootballEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    private static final ResourceKey<EntityType<?>> FOOTBALL_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(FootballMod.MOD_ID, "football"));

    public static final EntityType<FootballEntity> FOOTBALL = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            FOOTBALL_KEY,
            EntityType.Builder.of(FootballEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(FOOTBALL_KEY));

    private ModEntities() {
    }

    public static void register() {
        // Referencing this class triggers the static registration above.
    }
}
