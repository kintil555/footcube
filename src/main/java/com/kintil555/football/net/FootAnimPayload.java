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
 * @param lean     FETCH/STEP: -1 (ball on the right) .. 1 (ball on the left), tilts and stretches the leg
 */
public record FootAnimPayload(int playerId, int state, int leg, float lean) implements CustomPacketPayload {
    public enum State {
        NONE, HOLD, THROW, FETCH, STEP;

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
            ByteBufCodecs.FLOAT, FootAnimPayload::lean,
            FootAnimPayload::new);

    public static FootAnimPayload of(int playerId, State state, int leg, float lean) {
        return new FootAnimPayload(playerId, state.ordinal(), leg, lean);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
