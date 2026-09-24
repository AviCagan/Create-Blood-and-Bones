package com.avicagan.bloodandbones.network;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.parts.Activation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/** The client pressed the Organ Ability key: fire the next ready activate effect of the armour worn. */
public record OrganActivatePayload() implements CustomPacketPayload {
    public static final OrganActivatePayload INSTANCE = new OrganActivatePayload();
    public static final Type<OrganActivatePayload> TYPE = new Type<>(BloodAndBones.asResource("organ_activate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrganActivatePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** On the server, for the player who pressed it. */
    public static void handle(Player player) {
        Activation.press(player);
    }
}
