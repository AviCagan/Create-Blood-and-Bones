package com.avicagan.bloodandbones.client.effect;

import com.avicagan.bloodandbones.parts.effect.MotionFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Motion's client side ({@link com.avicagan.bloodandbones.parts.effect.MotionEffects}): movement a flag makes the
 * client predict (climb, bounce; glide goes through the chestplate's elytra hooks, asked on both sides), a teleport's or
 * detonation's look. Never loaded on a dedicated server: only the client branch of the mod's constructor calls
 * {@link #init}, and a client-bound payload's handler reaches it from inside its lambda.
 */
public final class MotionClient {
    private MotionClient() {
    }

    /**
     * Called once, from the mod's constructor on a client. Mod-bus events (renderers, layers, particles, key mappings)
     * go on {@code modBus}; game events (ticks, rendering the level, input) on {@code NeoForge.EVENT_BUS}.
     */
    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(MotionClient::onPlayerTick);
    }

    /**
     * The local player's own movement flags, before they move this tick: the same code the server runs for them, with
     * the one thing only the client knows, whether jump is held.
     */
    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof LocalPlayer player && player == Minecraft.getInstance().player) {
            MotionFlags.moveTick(player, player.input.jumping);
        }
    }
}
