package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.network.DragSyncPayload;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side record of who is dragging what, for drawing hooks and chains. */
public class ClientDragState {
    public record Drag(UUID subLevel, Vector3d anchor) {
    }

    private static final Map<UUID, Drag> DRAGS = new ConcurrentHashMap<>();

    public static void handle(DragSyncPayload payload) {
        if (payload.subLevel().isEmpty()) {
            DRAGS.remove(payload.player());
        } else {
            DRAGS.put(payload.player(), new Drag(payload.subLevel().get(), payload.anchor()));
        }
    }

    public static Map<UUID, Drag> all() {
        return DRAGS;
    }

    public static void clear() {
        DRAGS.clear();
    }
}
