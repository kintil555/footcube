package com.kintil555.football.client;

import com.kintil555.football.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class FootballModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.FOOTBALL, FootballRenderer::new);
        FootAnimClient.init();
        FootballKeys.init();
        FootballInput.init();
        FootballMarkerRenderer.init();
        FootballPreviewRenderer.init();
    }
}
