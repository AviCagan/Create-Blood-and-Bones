package com.avicagan.bloodandbones.network;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.butchery.ButcheryTable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * The server's butchery tables, all in one packet whenever data packs (re)load, so the client's recipe
 * viewer can show what each mob gives. Replaces whatever the client had.
 */
public record ButcherySyncPayload(Map<ResourceLocation, ButcheryTable> tables) implements CustomPacketPayload {
    public static final Type<ButcherySyncPayload> TYPE = new Type<>(BloodAndBones.asResource("butchery_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ButcherySyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(java.util.HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.fromCodec(ButcheryTable.CODEC)), ButcherySyncPayload::tables,
            ButcherySyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
