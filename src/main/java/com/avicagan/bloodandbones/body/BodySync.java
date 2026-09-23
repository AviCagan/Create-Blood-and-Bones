package com.avicagan.bloodandbones.body;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends a player's body to them and to everyone who can see them, so it is drawn and felt the same everywhere. */
public final class BodySync {
    private BodySync() {
    }

    public record Payload(int entity, Body body) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(BloodAndBones.asResource("body"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Payload::entity, Body.STREAM_CODEC, Payload::body, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new Payload(player.getId(), BodyEffects.body(player).copy()));
    }

    public static void sendTo(ServerPlayer watcher, Player target) {
        PacketDistributor.sendToPlayer(watcher, new Payload(target.getId(), BodyEffects.body(target).copy()));
    }
}
