package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * What one player's senses pick out (a sense effect's echolocate, tremor, reveal or see_invisible): the creatures to
 * outline, through walls, for this long. Sent to that player alone, so nobody else sees them marked, as they would with
 * vanilla Glowing.
 *
 * @param kind     the sense ("echolocate", "tremor", "reveal", "see_invisible"), which sets the outline's look
 * @param entities the creatures sensed, by entity id
 * @param ticks    how long the outlines last
 */
public record SensePayload(String kind, List<Integer> entities, int ticks) implements CustomPacketPayload {
    public static final Type<SensePayload> TYPE = new Type<>(BloodAndBones.asResource("sense"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SensePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SensePayload::kind,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), SensePayload::entities,
            ByteBufCodecs.VAR_INT, SensePayload::ticks,
            SensePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
