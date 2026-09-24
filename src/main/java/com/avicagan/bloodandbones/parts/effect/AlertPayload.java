package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * An alert sense's ping (docs/PARTS-AND-TRAITS.md section 5.4, type 11): something has set its sights on the player.
 * The client shows who, and which way to look, as a subtitle does.
 *
 * @param entity the mob taking aim, by entity id (the client follows it while it has it)
 * @param x      where it was, for when the client has not got it
 * @param name   its kind's name key, for the same
 */
public record AlertPayload(int entity, double x, double y, double z, String name) implements CustomPacketPayload {
    public static final Type<AlertPayload> TYPE = new Type<>(BloodAndBones.asResource("alert"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AlertPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AlertPayload decode(RegistryFriendlyByteBuf buf) {
            return new AlertPayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readUtf());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, AlertPayload p) {
            buf.writeVarInt(p.entity);
            buf.writeDouble(p.x);
            buf.writeDouble(p.y);
            buf.writeDouble(p.z);
            buf.writeUtf(p.name);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
