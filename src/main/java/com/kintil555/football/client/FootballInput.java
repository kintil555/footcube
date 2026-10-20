package com.kintil555.football.client;

import com.kintil555.football.action.FootballAction;
import com.kintil555.football.action.FootballActions;
import com.kintil555.football.entity.FootballEntity;
import com.kintil555.football.net.FootballActionPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Client tick: SPACE (hold = charge, release = kick, direction from WASD), jump lock while the ball is at the
 * feet (headers stay possible), middle click hold/throw, left click on a player = pass, and the
 * thrower-only yellow-white outline on the aimed player.
 */
public final class FootballInput {
    private static final double BALL_NEAR = 2.4;
    /** Ball above this height over the player's feet counts as header range: jumping is allowed. */
    private static final double HEADER_HEIGHT = 1.0;

    private static FootballEntity highlighted;
    /** Player the thrower is aiming at while holding the ball (outlined for the thrower only). */
    private static volatile Player passTarget;
    private static int charge = -1;
    private static final int TAP_TICKS = 5;
    private static final int DOUBLE_TAP_WINDOW = 7;
    private static int pendingKick;
    private static int pendingSide;
    private static int pendingForward;
    private static int pendingCharge;
    private static boolean suppressUntilRelease;

    private FootballInput() {
    }

    public static Entity passTarget() {
        return passTarget;
    }

    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(FootballInput::lockJump);
        ClientTickEvents.END_CLIENT_TICK.register(FootballInput::tick);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && entity instanceof Player target && target != player) {
                FootballEntity held = heldBall(Minecraft.getInstance());
                if (held != null) {
                    send(FootballAction.PASS, 0, 0, target.getId());
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
    }

    private static FootballEntity nearestBall(Minecraft client) {
        if (client.player == null || client.level == null) {
            return null;
        }
        FootballEntity best = null;
        double bestDist = BALL_NEAR * BALL_NEAR;
        for (FootballEntity ball : client.level.getEntitiesOfClass(FootballEntity.class,
                client.player.getBoundingBox().inflate(BALL_NEAR, 1.5, BALL_NEAR))) {
            double d = ball.distanceToSqr(client.player);
            if (d <= bestDist && !ball.isHeld()) {
                best = ball;
                bestDist = d;
            }
        }
        return best;
    }

    private static FootballEntity heldBall(Minecraft client) {
        if (client.player == null || client.level == null) {
            return null;
        }
        for (FootballEntity ball : client.level.getEntitiesOfClass(FootballEntity.class,
                client.player.getBoundingBox().inflate(3.0))) {
            if (ball.getHolderId() == client.player.getId()) {
                return ball;
            }
        }
        return null;
    }

    /** No jumping while the ball is at the feet; once it is in the air near head height, jumping (heading) is allowed. */
    private static void lockJump(Minecraft client) {
        if (client.player == null || !client.mouseHandler.isMouseGrabbed()) {
            return;
        }
        FootballEntity ball = nearestBall(client);
        if (ball == null || heldBall(client) != null) {
            return;
        }
        if (ball.getY() - client.player.getY() <= HEADER_HEIGHT) {
            client.options.keyJump.setDown(false);
        }
    }

    private static void tick(Minecraft client) {
        updateHighlight(client);
        if (client.player == null || !client.mouseHandler.isMouseGrabbed()) {
            drain(FootballKeys.HOLD, FootballKeys.JUGGLE, FootballKeys.FEINT);
            charge = -1;
            passTarget = null;
            return;
        }

        FootballEntity held = heldBall(client);
        passTarget = held != null && client.crosshairPickEntity instanceof Player p && p != client.player ? p : null;

        // SPACE charge: only while a ball is within reach.
        boolean space = InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_SPACE);
        boolean ballNear = nearestBall(client) != null;
        if (pendingKick > 0) {
            if (space && ballNear && charge < 0) {
                pendingKick = 0;
                send(FootballAction.JUGGLE, 0, 0, 0);
                charge = 0;
                suppressUntilRelease = true;
            } else if (--pendingKick == 0) {
                send(FootballAction.KICK, pendingSide, pendingForward, pendingCharge);
            }
        }
        if (suppressUntilRelease) {
            if (space) {
                charge = -1;
                space = false;
            } else {
                suppressUntilRelease = false;
            }
        }
        if (space && ballNear) {
            charge = charge < 0 ? 0 : Math.min(charge + 1, FootballActions.MAX_CHARGE_TICKS);
        } else if (!space) {
            if (charge >= 0 && ballNear) {
                if (charge < TAP_TICKS) {
                    // Short tap: wait briefly in case a second tap follows (double tap = juggle over opponent).
                    pendingKick = DOUBLE_TAP_WINDOW;
                    pendingSide = strafe(client);
                    pendingForward = forward(client);
                    pendingCharge = charge;
                } else {
                    send(FootballAction.KICK, strafe(client), forward(client), charge);
                }
            }
            charge = -1;
        } else {
            charge = -1;
        }

        while (FootballKeys.HOLD.consumeClick()) {
            if (held != null || client.crosshairPickEntity instanceof FootballEntity) {
                send(FootballAction.HOLD, 0, 0, 0);
            }
        }
        while (FootballKeys.JUGGLE.consumeClick()) {
            send(FootballAction.JUGGLE, 0, 0, 0);
        }
        while (FootballKeys.FEINT.consumeClick()) {
            send(FootballAction.FEINT, strafe(client) == -1 ? -1 : 1, 0, 0);
        }
        if (FootballKeys.DRIBBLE.isDown()) {
            send(FootballAction.DRIBBLE, 0, 0, 0);
        }
    }

    private static int strafe(Minecraft client) {
        boolean left = client.options.keyLeft.isDown();
        boolean right = client.options.keyRight.isDown();
        return left == right ? 0 : (right ? 1 : -1);
    }

    private static int forward(Minecraft client) {
        boolean up = client.options.keyUp.isDown();
        boolean down = client.options.keyDown.isDown();
        return up == down ? 0 : (up ? 1 : -1);
    }

    private static void send(FootballAction action, int side, int forward, int arg) {
        ClientPlayNetworking.send(FootballActionPayload.of(action, side, forward, arg));
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
