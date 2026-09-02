package com.avicagan.bloodandbones.network;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** The server's rigs, sent whenever data packs (re)load, so the client can draw carcasses by rig. */
public record RigSyncPayload(Map<ResourceLocation, Rig> rigs) implements CustomPacketPayload {
    public static final Type<RigSyncPayload> TYPE = new Type<>(BloodAndBones.asResource("rig_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RigSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(java.util.HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.fromCodec(Rig.CODEC)), RigSyncPayload::rigs,
            RigSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
