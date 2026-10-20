package com.kintil555.football;

import com.kintil555.football.entity.FootballEntity;
import com.kintil555.football.loot.ModLoot;
import com.kintil555.football.registry.ModEntities;
import com.kintil555.football.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public class FootballMod implements ModInitializer {
    public static final String MOD_ID = "football";

    @Override
    public void onInitialize() {
        ModItems.register();
        ModEntities.register();
        FabricDefaultAttributeRegistry.register(ModEntities.FOOTBALL, FootballEntity.createAttributes());
        ModLoot.register();
    }
}
