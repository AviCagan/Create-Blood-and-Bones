package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A hitscan's beam, for the clients watching its host (the host too): drawn from the host's eyes to its target, or along
 * its look as far as {@code range} reaches, for {@code ticks} (a short flash for a shot with no windup).
 *
 * @param host   the entity firing
 * @param target the entity it is locked on, or -1 to follow the host's look
 * @param beam   "guardian_beam", "sonic_boom" or "strand"
 */
public record BeamPayload(int host, int target, int ticks, float range, String beam) implements CustomPacketPayload {
    public static final Type<BeamPayload> TYPE = new Type<>(BloodAndBones.asResource("beam"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BeamPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BeamPayload::host,
            ByteBufCodecs.VAR_INT, BeamPayload::target,
            ByteBufCodecs.VAR_INT, BeamPayload::ticks,
            ByteBufCodecs.FLOAT, BeamPayload::range,
            ByteBufCodecs.STRING_UTF8, BeamPayload::beam,
            BeamPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
