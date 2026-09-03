package com.avicagan.bloodandbones.network;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** The client punched a resting carcass where a drawn limb was (limbs have no cells while resting). */
public record PunchCarcassPayload(UUID carcass) implements CustomPacketPayload {
    public static final Type<PunchCarcassPayload> TYPE = new Type<>(BloodAndBones.asResource("punch_carcass"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PunchCarcassPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PunchCarcassPayload::carcass, PunchCarcassPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
