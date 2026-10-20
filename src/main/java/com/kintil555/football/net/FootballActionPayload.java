package com.kintil555.football.net;

import com.kintil555.football.FootballMod;
import com.kintil555.football.action.FootballAction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server request to perform a ball action.
 *
 * @param actionId ordinal of {@link FootballAction}
 * @param side     strafe input: -1 = left, 0 = none, 1 = right
 * @param forward  forward input: -1 = back, 0 = none, 1 = forward
 * @param arg      KICK: charge ticks held; PASS: target entity id
 */
public record FootballActionPayload(int actionId, int side, int forward, int arg) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FootballActionPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FootballMod.MOD_ID, "action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FootballActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FootballActionPayload::actionId,
            ByteBufCodecs.VAR_INT, FootballActionPayload::side,
            ByteBufCodecs.VAR_INT, FootballActionPayload::forward,
            ByteBufCodecs.VAR_INT, FootballActionPayload::arg,
            FootballActionPayload::new);

    public static FootballActionPayload of(FootballAction action, int side, int forward, int arg) {
        return new FootballActionPayload(action.ordinal(), side, forward, arg);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
