package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * Upkeep: regen, mend, produce, storage, power, the rest of diet (graze is in {@code TraitEvents#onUseBlock}), and
 * lethal_save (an effect that is a {@code TraitEffect.DeathSaver}; {@code TraitEvents#onDeath} asks it).
 * <p>
 * Everything the group adds goes through this file and its own, so the four groups never edit the same file
 * (docs/ARCHITECTURE-PROPOSAL.md section 15.8): its effect types ({@link #types}), what it registers
 * ({@link #registerContent}), its words ({@link #lang}), its payloads ({@link #payloads}), the goals it gives
 * minions ({@link #minionGoals}), the hooks the shared code calls, and its NeoForge-bus handlers, here (this class is
 * registered on {@code NeoForge.EVENT_BUS}) or in classes it registers itself. Its client side is
 * {@link com.avicagan.bloodandbones.client.effect.UpkeepClient}.
 */
public final class UpkeepEffects {
    private UpkeepEffects() {
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

    /** Its network payloads; a client-bound one's handler calls into {@code UpkeepClient} inside its lambda. */
    public static void payloads(PayloadRegistrar registrar) {
    }

    /** Goals it gives every minion as it is made (they may look at the minion's traits each time they are asked). */
    public static void minionGoals(MinionEntity minion, GoalSelector goals, GoalSelector targets) {
    }

    // ---- hooks the shared code calls (minions, carcass armour); all neutral until Upkeep fills them in

    /** What a minion's blood use is multiplied by (power traits: leaky, marrow). */
    public static float drainMultiplier(MinionEntity minion) {
        return 1.0F;
    }

    /** Inventory slots a minion's traits add to its torso's (storage). */
    public static int extraSlots(MinionEntity minion) {
        return 0;
    }

    /** Someone uses an item on a minion: what its traits make of it (mend, refuel, milk), or null to leave it be. */
    @Nullable
    public static InteractionResult interact(MinionEntity minion, Player player, InteractionHand hand) {
        return null;
    }

    /** Whether this mends a carcass piece on an anvil, besides its own mob's scraps (mend). */
    public static boolean repairsWith(ItemStack piece, ItemStack repair) {
        return false;
    }

    // ---- NeoForge bus

    /** The server stopped: forget anything the group keeps per world. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
    }
}
