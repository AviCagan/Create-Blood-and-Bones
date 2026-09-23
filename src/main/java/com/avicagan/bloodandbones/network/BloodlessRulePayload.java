package com.avicagan.bloodandbones.network;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Whether the server's bloodandbonesBloodless game rule forces bloodless presentation. */
public record BloodlessRulePayload(boolean forced) implements CustomPacketPayload {
    public static final Type<BloodlessRulePayload> TYPE = new Type<>(BloodAndBones.asResource("bloodless_rule"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BloodlessRulePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BloodlessRulePayload::forced, BloodlessRulePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
