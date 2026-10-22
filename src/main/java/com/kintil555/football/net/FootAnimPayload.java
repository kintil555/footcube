package com.kintil555.football.net;

import com.kintil555.football.FootballMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: which body animation a player plays.
 *
 * @param playerId entity id of the animated player
 * @param state    ordinal of {@link State}
 * @param leg      FETCH/STEP: 0 = right leg, 1 = left leg
 * @param forward  FETCH: ball offset in blocks along the body direction, measured from the stepping hip
 * @param left     FETCH: ball offset in blocks to the player's left, measured from the stepping hip
 * @param power    KICK: strength of the kick, 0..1 (how far the leg swings); KICK also uses leg / forward / left like FETCH
 *
 * <p>TACKLED (the victim falling) carries the horizontal tackle direction in world axes: {@code forward} = x, {@code left} = z.
 * TACKLING (the tackler sliding) needs no extra data.
 */
public record FootAnimPayload(int playerId, int state, int leg, float forward, float left, float power) implements CustomPacketPayload {
    public enum State {
        NONE, HOLD, THROW, FETCH, STEP, TACKLED, TACKLING, KICK;

        private static final State[] VALUES = values();

        public static State byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
        }
    }

    public static final CustomPacketPayload.Type<FootAnimPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FootballMod.MOD_ID, "foot_anim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FootAnimPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FootAnimPayload::playerId,
            ByteBufCodecs.VAR_INT, FootAnimPayload::state,
            ByteBufCodecs.VAR_INT, FootAnimPayload::leg,
            ByteBufCodecs.FLOAT, FootAnimPayload::forward,
            ByteBufCodecs.FLOAT, FootAnimPayload::left,
            ByteBufCodecs.FLOAT, FootAnimPayload::power,
            FootAnimPayload::new);

    public static FootAnimPayload of(int playerId, State state, int leg, float forward, float left) {
        return of(playerId, state, leg, forward, left, 0.0F);
    }

    public static FootAnimPayload of(int playerId, State state, int leg, float forward, float left, float power) {
        return new FootAnimPayload(playerId, state.ordinal(), leg, forward, left, power);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
