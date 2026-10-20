package com.kintil555.football.client;

import com.kintil555.football.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class FootballModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRenderers.register(ModEntities.FOOTBALL, FootballRenderer::new);
    }
}
