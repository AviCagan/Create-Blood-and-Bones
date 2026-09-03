package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.network.DragSyncPayload;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side record of who is dragging what, for drawing hooks and chains. */
public class ClientDragState {
    public record Drag(UUID subLevel, Vector3d anchor, Vector3d entry) {
    }

    private static final Map<UUID, Drag> DRAGS = new ConcurrentHashMap<>();

    public static void handle(DragSyncPayload payload) {
        if (payload.subLevel().isEmpty()) {
            DRAGS.remove(payload.player());
        } else {
            DRAGS.put(payload.player(), new Drag(payload.subLevel().get(), payload.anchor(), payload.entry()));
        }
    }

    public static Map<UUID, Drag> all() {
        return DRAGS;
    }

    /** The hook stuck in this limb, if any player is dragging it. */
    public static Drag hookIn(UUID subLevel) {
        for (Drag drag : DRAGS.values()) {
            if (drag.subLevel().equals(subLevel)) {
                return drag;
            }
        }
        return null;
    }

    public static void clear() {
        DRAGS.clear();
    }
}
