package com.kintil555.football.client;

import com.kintil555.football.action.BallPhysics;
import com.kintil555.football.action.FootballAction;
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
 * Client input:
 * <ul>
 *   <li>Left click with the ball in reach = kick along the crosshair (dashed landing preview).</li>
 *   <li>Middle click on the ball = lift it; then hold middle click to charge, release to throw along the look direction (preview).</li>
 *   <li>Left click on a player while holding the ball = pass (thrower-only yellow-white outline on the target).</li>
 *   <li>Double SPACE near a ball = catch a falling ball, otherwise juggle / nutmeg.</li>
 *   <li>No jumping while the ball is at the feet; headers (ball above the waist) stay possible.</li>
 * </ul>
 */
public final class FootballInput {
    private static final double BALL_NEAR = 2.4;
    private static final double CATCH_SCAN = 4.0;
    /** Ball above this height over the player's feet counts as header range: jumping is allowed. */
    private static final double HEADER_HEIGHT = 1.0;
    private static final int DOUBLE_TAP_TICKS = 10;

    private static FootballEntity highlighted;
    /** Player the thrower is aiming at while holding the ball (outlined for the thrower only). */
    private static volatile Player passTarget;

    private static boolean holdPrev;
    private static boolean charging;
    private static int throwCharge;
    private static boolean spacePrev;
    private static int ticksSinceTap = 99;

    private FootballInput() {
    }

    public static Entity passTarget() {
        return passTarget;
    }

    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(FootballInput::startTick);
        ClientTickEvents.END_CLIENT_TICK.register(FootballInput::tick);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && entity instanceof Player target && target != player
                    && heldBall(Minecraft.getInstance()) != null) {
                send(FootballAction.PASS, target.getId());
                return InteractionResult.SUCCESS;
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

    private static boolean anyBallClose(Minecraft client) {
        return !client.level.getEntitiesOfClass(FootballEntity.class,
                client.player.getBoundingBox().inflate(CATCH_SCAN, CATCH_SCAN, CATCH_SCAN)).isEmpty();
    }

    /** Runs before vanilla key handling: kick consumes left click, and the ball at the feet blocks jumping. */
    private static void startTick(Minecraft client) {
        if (client.player == null || client.level == null || !client.mouseHandler.isMouseGrabbed() || client.gui.screen() != null) {
            return;
        }
        FootballEntity ball = nearestBall(client);
        if (ball == null || heldBall(client) != null) {
            return;
        }
        boolean kicked = false;
        while (client.options.keyAttack.consumeClick()) {
            send(FootballAction.KICK, 0);
            kicked = true;
        }
        if (kicked) {
            client.options.keyAttack.setDown(false);
        }
        if (ball.getY() - client.player.getY() <= HEADER_HEIGHT) {
            client.options.keyJump.setDown(false);
        }
    }

    private static void tick(Minecraft client) {
        updateHighlight(client);
        if (client.player == null || client.level == null || !client.mouseHandler.isMouseGrabbed()) {
            drain(FootballKeys.HOLD, FootballKeys.JUGGLE, FootballKeys.FEINT);
            charging = false;
            throwCharge = 0;
            holdPrev = false;
            passTarget = null;
            FootballPreview.clear();
            return;
        }

        FootballEntity held = heldBall(client);
        passTarget = held != null && client.crosshairPickEntity instanceof Player p && p != client.player ? p : null;

        // Middle click: press on the ball = grab; with the ball held, hold = charge, release = throw.
        boolean down = FootballKeys.HOLD.isDown();
        drain(FootballKeys.HOLD);
        if (down && !holdPrev) {
            if (held != null) {
                charging = true;
                throwCharge = 0;
            } else if (client.crosshairPickEntity instanceof FootballEntity) {
                send(FootballAction.GRAB, 0);
            }
        }
        if (charging) {
            if (down && held != null) {
                throwCharge = Math.min(throwCharge + 1, BallPhysics.THROW_CHARGE_TICKS);
            } else {
                if (held != null) {
                    send(FootballAction.THROW, throwCharge);
                }
                charging = false;
                throwCharge = 0;
            }
        }
        holdPrev = down;

        updatePreview(client, held);

        // Double-tap SPACE near a ball: catch / juggle (server decides which).
        boolean space = InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_SPACE);
        ticksSinceTap = Math.min(ticksSinceTap + 1, 99);
        if (space && !spacePrev) {
            if (ticksSinceTap <= DOUBLE_TAP_TICKS && anyBallClose(client)) {
                send(FootballAction.JUGGLE, 0);
                ticksSinceTap = 99;
            } else {
                ticksSinceTap = 0;
            }
        }
        spacePrev = space;

        while (FootballKeys.JUGGLE.consumeClick()) {
            send(FootballAction.JUGGLE, 0);
        }
        while (FootballKeys.FEINT.consumeClick()) {
            send(FootballAction.FEINT, feintSide(client));
        }
    }

    private static void updatePreview(Minecraft client, FootballEntity held) {
        if (held != null) {
            FootballPreview.setThrow(held, throwCharge, charging);
            return;
        }
        FootballEntity ball = nearestBall(client);
        if (ball != null) {
            FootballPreview.setKick(ball);
        } else {
            FootballPreview.clear();
        }
    }

    /** Strafe direction picks the feint side; with no strafe input it cuts to the right. */
    private static int feintSide(Minecraft client) {
        boolean left = client.options.keyLeft.isDown();
        boolean right = client.options.keyRight.isDown();
        return left && !right ? -1 : 1;
    }

    private static void send(FootballAction action, int arg) {
        int side = action == FootballAction.FEINT ? arg : 0;
        int actionArg = action == FootballAction.FEINT ? 0 : arg;
        ClientPlayNetworking.send(FootballActionPayload.of(action, side, 0, actionArg));
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
