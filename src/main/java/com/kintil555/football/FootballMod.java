package com.kintil555.football;

import com.kintil555.football.action.FootballActions;
import com.kintil555.football.entity.FootballEntity;
import com.kintil555.football.loot.ModLoot;
import com.kintil555.football.net.FootballActionPayload;
import com.kintil555.football.registry.ModEntities;
import com.kintil555.football.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public class FootballMod implements ModInitializer {
    public static final String MOD_ID = "football";

    @Override
    public void onInitialize() {
        ModItems.register();
        ModEntities.register();
        FabricDefaultAttributeRegistry.register(ModEntities.FOOTBALL, FootballEntity.createAttributes());
        ModLoot.register();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(FootballActions::tickServer);

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(com.kintil555.football.action.FootIdle::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(com.kintil555.football.action.FootTackle::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> com.kintil555.football.action.FootTackle.releaseAll());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                com.kintil555.football.action.FootTackle.unfreeze(mob);
            }
        });
        PayloadTypeRegistry.clientboundPlay().register(com.kintil555.football.net.FootAnimPayload.TYPE, com.kintil555.football.net.FootAnimPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(FootballActionPayload.TYPE, FootballActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FootballActionPayload.TYPE,
                (payload, context) -> FootballActions.handle(context.player(), payload));
    }
}
