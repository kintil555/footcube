package com.kintil555.football.client;

import com.kintil555.football.action.FootballAction;
import com.kintil555.football.entity.FootballEntity;
import com.kintil555.football.net.FootballActionPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;

/** Client tick: turns key presses into action packets and tracks which ball the crosshair is on. */
public final class FootballInput {
    private static FootballEntity highlighted;

    private FootballInput() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(FootballInput::tick);
    }

    private static void tick(Minecraft client) {
        updateHighlight(client);
        if (client.player == null || !client.mouseHandler.isMouseGrabbed()) {
            drain(FootballKeys.KICK, FootballKeys.LONG_KICK, FootballKeys.THROW, FootballKeys.JUGGLE, FootballKeys.FEINT);
            return;
        }
        while (FootballKeys.KICK.consumeClick()) {
            send(FootballAction.KICK, 0);
        }
        while (FootballKeys.LONG_KICK.consumeClick()) {
            send(FootballAction.LONG_KICK, 0);
        }
        while (FootballKeys.THROW.consumeClick()) {
            send(FootballAction.THROW, 0);
        }
        while (FootballKeys.JUGGLE.consumeClick()) {
            send(FootballAction.JUGGLE, 0);
        }
        while (FootballKeys.FEINT.consumeClick()) {
            send(FootballAction.FEINT, feintSide(client));
        }
        if (FootballKeys.DRIBBLE.isDown()) {
            send(FootballAction.DRIBBLE, 0);
        }
    }

    /** Strafe direction picks the feint side; with no strafe input it cuts to the right. */
    private static int feintSide(Minecraft client) {
        boolean left = client.options.keyLeft.isDown();
        boolean right = client.options.keyRight.isDown();
        if (left && !right) {
            return -1;
        }
        return 1;
    }

    private static void send(FootballAction action, int side) {
        ClientPlayNetworking.send(FootballActionPayload.of(action, side));
    }

    private static void drain(KeyMapping... keys) {
        for (KeyMapping key : keys) {
            while (key.consumeClick()) {
                // discard clicks made while a screen is open or no player exists
            }
        }
    }

    private static void updateHighlight(Minecraft client) {
        Entity target = client.level == null ? null : client.crosshairPickEntity;
        FootballEntity now = target instanceof FootballEntity ball ? ball : null;
        if (now == highlighted) {
            return;
        }
        if (highlighted != null) {
            highlighted.setClientHighlighted(false);
        }
        if (now != null) {
            now.setClientHighlighted(true);
        }
        highlighted = now;
    }
}
