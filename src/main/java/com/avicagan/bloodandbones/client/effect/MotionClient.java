package com.avicagan.bloodandbones.client.effect;

import net.neoforged.bus.api.IEventBus;

/**
 * Motion's client side ({@link com.avicagan.bloodandbones.parts.effect.MotionEffects}): movement a flag makes the
 * client predict (climb, glide, bounce), a teleport's or detonation's look. Never loaded on a dedicated server: only
 * the client branch of the mod's constructor calls {@link #init}, and a client-bound payload's handler reaches it
 * from inside its lambda.
 */
public final class MotionClient {
    private MotionClient() {
    }

    /**
     * Called once, from the mod's constructor on a client. Mod-bus events (renderers, layers, particles, key mappings)
     * go on {@code modBus}; game events (ticks, rendering the level, input) on {@code NeoForge.EVENT_BUS}.
     */
    public static void init(IEventBus modBus) {
    }
}
