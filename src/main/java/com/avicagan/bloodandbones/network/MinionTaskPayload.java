package com.avicagan.bloodandbones.network;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The task screen's traffic (docs/NEXT.md 1.3). The server opens it on a minion with one row per task, worked out there
 * (task and disposition files never go to clients), and sends it again after each request; the maker's requests come back
 * to it (a task, where it is done and how far it reaches, and "Home here"), and it checks every one
 * ({@code MinionTasks.handle}).
 */
public final class MinionTaskPayload {
    private MinionTaskPayload() {
    }

    /**
     * One task's row: the task (its place in the list), its fitness, whether the body can do it at all, whether it would
     * wait for something, the lines shown over it (its reasons, in words), the anchors it allows (a bit each, home first),
     * and its own reach and the most its maker may set.
     */
    public record Row(int task, float fitness, boolean can, boolean waiting, List<Component> lines, int anchors, int reach, int maxReach) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.of((buf, row) -> {
            ByteBufCodecs.VAR_INT.encode(buf, row.task());
            buf.writeFloat(row.fitness());
            buf.writeBoolean(row.can());
            buf.writeBoolean(row.waiting());
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, row.lines());
            ByteBufCodecs.VAR_INT.encode(buf, row.anchors());
            ByteBufCodecs.VAR_INT.encode(buf, row.reach());
            ByteBufCodecs.VAR_INT.encode(buf, row.maxReach());
        }, buf -> new Row(ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readBoolean(), buf.readBoolean(),
                new ArrayList<>(ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

        /** Whether this task can be done at this anchor (its place in the anchors, home 0). */
        public boolean allows(int anchor) {
            return (anchors & (1 << anchor)) != 0;
        }
    }

    /**
     * Open the screen on a minion (by entity id), or, as a {@code refresh}, bring an open one up to date (a screen closed
     * meanwhile stays closed): its name, its task, anchor and the reach its maker set (0 for its task's own), and the rows.
     */
    public record Open(int minion, Component name, int task, int anchor, int reach, boolean refresh, List<Row> rows) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(BloodAndBones.asResource("minion_tasks"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.of((buf, open) -> {
            ByteBufCodecs.VAR_INT.encode(buf, open.minion());
            ComponentSerialization.STREAM_CODEC.encode(buf, open.name());
            ByteBufCodecs.VAR_INT.encode(buf, open.task());
            ByteBufCodecs.VAR_INT.encode(buf, open.anchor());
            ByteBufCodecs.VAR_INT.encode(buf, open.reach());
            buf.writeBoolean(open.refresh());
            Row.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, open.rows());
        }, buf -> new Open(ByteBufCodecs.VAR_INT.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), buf.readBoolean(), Row.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)));

        /** The same rows, to bring an open screen up to date. */
        public Open refreshed() {
            return new Open(minion, name, task, anchor, reach, true, rows);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * The maker's request: this task at this anchor (home 0, with them 1) reaching this far (0 for its own), and whether to
     * set home where the minion stands.
     */
    public record Set(int minion, int task, int anchor, int reach, boolean homeHere) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(BloodAndBones.asResource("minion_task_set"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Set> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Set::minion, ByteBufCodecs.VAR_INT, Set::task, ByteBufCodecs.VAR_INT, Set::anchor, ByteBufCodecs.VAR_INT, Set::reach,
                ByteBufCodecs.BOOL, Set::homeHere, Set::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
