package com.avicagan.bloodandbones.network;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.joml.Vector3d;

import java.util.Optional;
import java.util.UUID;

/**
 * Tells clients that a player is (or is no longer) dragging a carcass limb, and where on the limb the hook sits.
 *
 * @param player   the dragging player
 * @param subLevel the hooked limb's sub-level, empty when the drag ended
 * @param anchor   hook point in the limb's plot space
 */
public record DragSyncPayload(UUID player, Optional<UUID> subLevel, Vector3d anchor) implements CustomPacketPayload {
    public static final Type<DragSyncPayload> TYPE = new Type<>(BloodAndBones.asResource("drag_sync"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Vector3d> VEC3D = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, v -> v.x, ByteBufCodecs.DOUBLE, v -> v.y, ByteBufCodecs.DOUBLE, v -> v.z, Vector3d::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, DragSyncPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, DragSyncPayload::player,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), DragSyncPayload::subLevel,
            VEC3D, DragSyncPayload::anchor,
            DragSyncPayload::new);

    public static DragSyncPayload ended(UUID player) {
        return new DragSyncPayload(player, Optional.empty(), new Vector3d());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
