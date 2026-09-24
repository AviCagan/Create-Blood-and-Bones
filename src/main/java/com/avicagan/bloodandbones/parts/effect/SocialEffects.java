package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Social: kin, sense, aura, pack and glow, and anything added to reaction (whose goals are handed out in
 * {@code TraitEvents#onJoin}).
 * <p>
 * Everything the group adds goes through this file and its own, so the four groups never edit the same file
 * (docs/ARCHITECTURE-PROPOSAL.md section 15.8): its effect types ({@link #types}), what it registers
 * ({@link #registerContent}), its words ({@link #lang}), its payloads ({@link #payloads}), the goals it gives
 * minions ({@link #minionGoals}), the hooks the shared code calls, and its NeoForge-bus handlers, here (this class is
 * registered on {@code NeoForge.EVENT_BUS}) or in classes it registers itself. Its client side is
 * {@link com.avicagan.bloodandbones.client.effect.SocialClient}.
 */
public final class SocialEffects {
    private SocialEffects() {
    }

    /**
     * Its trait effect types, into the {@code bloodandbones:trait_effect_type} registry, each a record in its own file in
     * this package: {@code types.register("impulse", () -> ImpulseEffect.CODEC);}.
     */
    public static void types(DeferredRegister<MapCodec<? extends TraitEffect.Effect>> types) {
    }

    /**
     * What it registers besides effect types (blocks, items, mob effects, entity types, sounds, loot condition or
     * enchantment effect types), through its own DeferredRegisters on the mod bus (or Registrate entries in its own
     * class, loaded from here). Called once, from the mod's constructor.
     */
    public static void registerContent(IEventBus modBus) {
    }

    /**
     * Its words: trait names and descriptions ({@code BBLang.trait}), and bloodless wording where the general rewording
     * would not read right ({@code BBLang.bloodless}). Called from {@code BBLang.register}, so datagen writes them.
     */
    public static void lang() {
    }

    /** Its network payloads; a client-bound one's handler calls into {@code SocialClient} inside its lambda. */
    public static void payloads(PayloadRegistrar registrar) {
    }

    /** Goals it gives every minion as it is made (they may look at the minion's traits each time they are asked). */
    public static void minionGoals(MinionEntity minion, GoalSelector goals, GoalSelector targets) {
    }

    // ---- NeoForge bus

    /** The server stopped: forget anything the group keeps per world. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
    }
}
