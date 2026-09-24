package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.avicagan.bloodandbones.parts.TraitEffects;
import com.avicagan.bloodandbones.parts.TraitEvents;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.avicagan.bloodandbones.registry.BBLang;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * Upkeep: regen, mend, produce, storage, power, the rest of diet (graze is in {@code TraitEvents#onUseBlock}), and
 * lethal_save (an effect that is a {@code TraitEffect.DeathSaver}; {@code TraitEvents#onDeath} asks it). Also exposure,
 * the harm a drawback's surroundings do (sun, water, heat), and the {@code blood_upkeep} attribute (docs/PARTS-AND-TRAITS.md
 * section 5.7), which scales what implants and minions drink.
 * <p>
 * Everything the group adds goes through this file and its own, so the four groups never edit the same file
 * (docs/ARCHITECTURE-PROPOSAL.md section 15.8): its effect types ({@link #types}), what it registers
 * ({@link #registerContent}), its words ({@link #lang}), its payloads ({@link #payloads}), the goals it gives
 * minions ({@link #minionGoals}), the hooks the shared code calls, and its NeoForge-bus handlers, here (this class is
 * registered on {@code NeoForge.EVENT_BUS}) or in classes it registers itself. Its client side is
 * {@link com.avicagan.bloodandbones.client.effect.UpkeepClient}.
 */
public final class UpkeepEffects {
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, BloodAndBones.MOD_ID);

    /**
     * What the blood an implant or a minion drinks is multiplied by (1 as it comes; lean makes it less, the power effect's
     * {@code drain_mult} more or less).
     */
    public static final DeferredHolder<Attribute, Attribute> BLOOD_UPKEEP = ATTRIBUTES.register("blood_upkeep",
            () -> new RangedAttribute("attribute.bloodandbones.blood_upkeep", 1.0, 0.0, 16.0).setSyncable(true));

    /** The modifier a player's power effects put on their blood upkeep. */
    private static final net.minecraft.resources.ResourceLocation POWER_MODIFIER = BloodAndBones.asResource("upkeep/power");

    private UpkeepEffects() {
    }

    /**
     * Its trait effect types, into the {@code bloodandbones:trait_effect_type} registry, each a record in its own file in
     * this package: {@code types.register("impulse", () -> ImpulseEffect.CODEC);}.
     */
    public static void types(DeferredRegister<MapCodec<? extends TraitEffect.Effect>> types) {
        types.register("regen", () -> RegenEffect.CODEC);
        types.register("mend", () -> MendEffect.CODEC);
        types.register("produce", () -> ProduceEffect.CODEC);
        types.register("storage", () -> StorageEffect.CODEC);
        types.register("power", () -> PowerEffect.CODEC);
        types.register("lethal_save", () -> LethalSaveEffect.CODEC);
        types.register("exposure", () -> ExposureEffect.CODEC);
    }

    /**
     * What it registers besides effect types (blocks, items, mob effects, entity types, sounds, loot condition or
     * enchantment effect types), through its own DeferredRegisters on the mod bus (or Registrate entries in its own
     * class, loaded from here). Called once, from the mod's constructor.
     */
    public static void registerContent(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener((EntityAttributeModificationEvent event) -> {
            event.add(EntityType.PLAYER, BLOOD_UPKEEP);
            event.add(BBEntities.MINION.get(), BLOOD_UPKEEP);
            // a beast of burden's pull (hauler), for the jobs that drag carcasses
            event.add(BBEntities.MINION.get(), com.avicagan.bloodandbones.registry.BBAttributes.DRAG_STRENGTH);
        });
    }

    /**
     * Its words: trait names and descriptions ({@code BBLang.trait}), and bloodless wording where the general rewording
     * would not read right ({@code BBLang.bloodless}). Called from {@code BBLang.register}, so datagen writes them.
     */
    public static void lang() {
        BBLang.raw("attribute.bloodandbones.blood_upkeep", "Blood Upkeep");
        // produce
        BBLang.trait("milk_udder", "Milk Udder", "A minion fills an empty bucket it carries with milk every 5 minutes, for 50 mB of blood. "
                + "Use an empty bucket on it to milk it there and then, for the same.");
        BBLang.bloodless("trait.bloodandbones.milk_udder", "Milk Tap");
        BBLang.trait("stew_udder", "Stew Udder", "A minion fills a bowl it carries with mushroom stew every 5 minutes, for 50 mB of blood. "
                + "Use a bowl on it to have stew there and then, for the same.");
        BBLang.bloodless("trait.bloodandbones.stew_udder", "Stew Tap");
        BBLang.trait("egg_layer", "Egg Layer", "Lays an egg: a minion into what it carries every 5 minutes, for 10 mB of blood; "
                + "armour into your pack every 10 minutes.");
        BBLang.bloodless("trait.bloodandbones.egg_layer", "Egg Dispenser");
        BBLang.bloodless("trait.bloodandbones.egg_layer.desc", "Dispenses an egg: a minion into what it carries every 5 minutes, "
                + "for 10 mB of essence; armour into your pack every 10 minutes.");
        BBLang.trait("wool_regrowth", "Wool Regrowth", "A minion grows a fleece in the colour its sheep had (white if it had none) "
                + "and sheds it into what it carries every 5 minutes, for 20 mB of blood.");
        BBLang.trait("silk_gland", "Silk Gland", "A minion spins string into what it carries every 2 and a half minutes, for 10 mB of blood.");
        BBLang.bloodless("trait.bloodandbones.silk_gland", "Silk Spinner");
        BBLang.trait("ink_gland", "Ink Gland", "A minion squeezes out an ink sac every 10 minutes, for 15 mB of blood.");
        BBLang.bloodless("trait.bloodandbones.ink_gland", "Ink Reservoir");
        BBLang.bloodless("trait.bloodandbones.ink_gland.desc", "A minion presses out an ink sac every 10 minutes, for 15 mB of essence.");
        BBLang.trait("glow_gland", "Glow Gland", "A minion squeezes out a glow ink sac every 10 minutes, for 15 mB of blood.");
        BBLang.bloodless("trait.bloodandbones.glow_gland", "Glow Reservoir");
        BBLang.bloodless("trait.bloodandbones.glow_gland.desc", "A minion presses out a glow ink sac every 10 minutes, for 15 mB of essence.");
        BBLang.trait("honey_stomach", "Honey Stomach", "A minion brings up honey into a glass bottle it carries every minute, for 20 mB of blood. "
                + "Use a glass bottle on it to have honey there and then, for the same.");
        BBLang.bloodless("trait.bloodandbones.honey_stomach", "Honey Hopper");
        BBLang.bloodless("trait.bloodandbones.honey_stomach.desc", "A minion dispenses honey into a glass bottle it carries every minute, "
                + "for 20 mB of essence. Use a glass bottle on it to have honey there and then, for the same.");
        BBLang.trait("scute_shed", "Scute Shed", "A minion sheds an armadillo scute into what it carries every 5 minutes, for 20 mB of blood.");
        BBLang.trait("morning_gift", "Morning Gift", "At dawn a minion turns up a gift, as a cat does for its owner, and keeps it.");
        // diet
        BBLang.trait("four_chambers", "Four Chambers", "Plant foods fill you half as much again.");
        BBLang.trait("omnivore", "Omnivore", "Any food fills you a little longer.");
        BBLang.trait("seed_eater", "Seed Eater", "Seeds are food: use them while hungry. A minion eats seeds it carries for 25 mB of blood each, "
                + "until it is half full.");
        BBLang.trait("bamboo_gut", "Bamboo Gut", "Bamboo and sugar cane are food: use them while hungry. A minion eats them for 25 mB of blood "
                + "each, until it is half full.");
        BBLang.bloodless("trait.bloodandbones.bamboo_gut", "Bamboo Stomach");
        BBLang.trait("mycelial_gut", "Mycelial Gut", "Mushrooms are food, and mushrooms and stews give Regeneration for 5 seconds. A minion eats "
                + "mushrooms it carries or stands on for 50 mB of blood each, until it is half full.");
        BBLang.bloodless("trait.bloodandbones.mycelial_gut", "Mycelial Stomach");
        BBLang.trait("forager", "Forager", "A minion eats wheat and hay it carries, and grass where it stands (if mobs may trample it), "
                + "for 100 mB of blood each, until it is half full.");
        BBLang.trait("cookie_poison", "Cookie Poison", "Cookies poison you.");
        BBLang.raw("trait.bloodandbones.iron_gut.desc", "Hunger, rotten flesh and raw meat do you no harm. A minion turns them into blood, "
                + "25 mB each: fed by hand, or eaten from what it carries when it runs low.");
        // storage and power
        BBLang.trait("beast_of_burden", "Beast of Burden", "A minion drags carcasses more easily, and its maker can fit it with a chest "
                + "(use one on it) for 18 more slots.");
        BBLang.trait("saddlebags", "Saddlebags", "A minion carries 15 more stacks.");
        BBLang.trait("hump", "Hump", "A minion holds twice as much blood.");
        BBLang.trait("leaky", "Leaky", "A minion goes through half as much blood again.");
        BBLang.trait("marrow", "Marrow", "A minion goes through a fifth less blood.");
        BBLang.bloodless("trait.bloodandbones.marrow", "Lean Core");
        // mending, healing and saves
        BBLang.trait("repair_with_iron", "Iron Mending", "An iron ingot used on a minion mends 25 health, flesh or brass.");
        BBLang.trait("cleanse", "Cleanse", "Ability: clears every harmful effect on you. 50 mB of blood.");
        BBLang.trait("honey", "Honey", "Ability: cures poison and gives Regeneration II for 5 seconds. 30 mB of blood.");
        BBLang.trait("undying", "Undying", "A killing blow leaves you on 1 health instead: a fifth of the time at level I, every time at V; "
                + "then not again for 5 minutes. A minion collapses rather than dying.");
        BBLang.trait("second_wind", "Second Wind", "Hurt below 30% health: Speed II and Regeneration for 5 seconds. Once a minute.");
        BBLang.trait("adrenaline", "Adrenaline", "Hurt below 40% health: Strength and Speed for 6 seconds. Once a minute.");
        BBLang.trait("roll_up", "Roll Up", "Hurt below 30% health: curl up tight, Resistance III and Slowness V for 4 seconds.");
        // drawbacks
        BBLang.trait("sun_cursed", "Sun-Cursed", "Catches fire in daylight under open sky unless wet, helmet or no helmet.");
        BBLang.trait("water_hurts", "Water Hurts", "Water and rain burn: 1 damage a second.");
        BBLang.trait("dry_out", "Dry Out", "After a minute out of water: Slowness, and 2 armour less.");
        BBLang.trait("heat_hurts", "Heat Hurts", "Hot places hurt, 1 damage a second: deserts, badlands, savannas and the Nether.");
    }

    /** Its network payloads; a client-bound one's handler calls into {@code UpkeepClient} inside its lambda. */
    public static void payloads(PayloadRegistrar registrar) {
    }

    /** Goals it gives every minion as it is made (they may look at the minion's traits each time they are asked). */
    public static void minionGoals(MinionEntity minion, GoalSelector goals, GoalSelector targets) {
    }

    // ---- hooks the shared code calls (minions, carcass armour)

    /**
     * What a minion's blood use is multiplied by: its power effects' {@code drain_mult} (leaky, marrow) and its blood
     * upkeep attribute.
     */
    public static float drainMultiplier(MinionEntity minion) {
        return PowerEffect.drain(minion) * (float) upkeep(minion);
    }

    /** Inventory slots a minion's traits add to its torso's (storage). */
    public static int extraSlots(MinionEntity minion) {
        return StorageEffect.extra(minion);
    }

    /**
     * Someone uses an item on a minion: what its traits make of it, or null to leave it be. An item that mends it (hurt),
     * blood food (not full), a container one of its produce effects fills (milking), a chest it can take.
     */
    @Nullable
    public static InteractionResult interact(MinionEntity minion, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || minion.build().isEmpty()) {
            return null;
        }
        InteractionResult result = MendEffect.interact(minion, player, held);
        if (result == null) {
            result = PowerEffect.refuel(minion, player, held);
        }
        if (result == null) {
            result = ProduceEffect.byHand(minion, player, hand);
        }
        if (result == null) {
            result = StorageEffect.fitChest(minion, player, held);
        }
        return result;
    }

    /** Whether this mends a carcass piece on an anvil, besides its own mob's scraps (mend). */
    public static boolean repairsWith(ItemStack piece, ItemStack repair) {
        return MendEffect.repairsWith(piece, repair);
    }

    /** A creature's blood upkeep: 1 for one without the attribute. */
    public static double upkeep(LivingEntity host) {
        AttributeInstance instance = host.getAttribute(BLOOD_UPKEEP);
        return instance == null ? 1.0 : instance.getValue();
    }

    /**
     * Whole mB of blood a use of {@code mb} costs this creature, at its blood upkeep: a share of a drop left over is paid
     * that share of the time, so a lean body pays less over many seconds and not just when it rounds down.
     */
    public static int upkeep(LivingEntity host, int mb) {
        double scaled = mb * upkeep(host);
        int whole = (int) Math.floor(scaled);
        return host.getRandom().nextDouble() < scaled - whole ? whole + 1 : whole;
    }

    /** A multiplier from a trait at the server's trait strength: the change from 1 scaled (as damage multipliers are). */
    static float strengthened(float multiplier) {
        float strength = TraitEffects.strength();
        return strength == 1.0F ? multiplier : 1.0F + (multiplier - 1.0F) * strength;
    }

    /** Whether a found effect's condition holds now (always, with none; never on a client). */
    static boolean holds(LivingEntity host, ActiveTraits.Found<?> found) {
        return found.facet().requirements().isEmpty() || TraitEvents.holds(host, found.entry(), found.facet(), null);
    }

    // ---- NeoForge bus

    /**
     * A minion's upkeep, once a second, powered down or not: what no longer fits its slots comes out (into free slots, or
     * onto the ground); a chest it can no longer take falls off. Awake and made of flesh, it eats: blood food it carries
     * when it runs low (power), and what its diet forages until it is half full.
     */
    @SubscribeEvent
    public static void onMinionTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof MinionEntity minion) || minion.level().isClientSide || (minion.tickCount + minion.getId()) % 20 != 0
                || minion.build().isEmpty()) {
            return;
        }
        StorageEffect.settle(minion);
        if (!minion.poweredDown() && !minion.cybernetic()) {
            if (!PowerEffect.eatStores(minion)) {
                Diet.forage(minion);
            }
        }
    }

    /** A player's power effects on their blood upkeep, once a second: what their implants drink. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || (player.tickCount + player.getId()) % 20 != 0) {
            return;
        }
        AttributeInstance upkeep = player.getAttribute(BLOOD_UPKEEP);
        if (upkeep == null) {
            return;
        }
        float mult = PowerEffect.drain(player);
        var current = upkeep.getModifier(POWER_MODIFIER);
        if (mult == 1.0F) {
            if (current != null) {
                upkeep.removeModifier(POWER_MODIFIER);
            }
        } else if (current == null || current.amount() != mult - 1.0F) {
            upkeep.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(POWER_MODIFIER, mult - 1.0F,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Something finished eating: what its diet makes of the food. */
    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!event.getEntity().level().isClientSide && event.getItem().getFoodProperties(event.getEntity()) != null) {
            Diet.eaten(event.getEntity(), event.getItem());
        }
    }

    /** A use of an item a diet makes food (seeds, bamboo, mushrooms): eaten at once, while hungry. */
    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (Diet.eatEdible(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }

    /** A kill that stood: blood for a killer whose power effect feeds on kills. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onKill(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide && event.getSource().getEntity() instanceof LivingEntity killer && killer != event.getEntity()) {
            PowerEffect.fedOnKill(killer);
        }
    }

    /** A minion that dies (as a server may have them do) drops the chest it was fitted with. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof MinionEntity minion && StorageEffect.hasChest(minion)) {
            event.getDrops().add(new ItemEntity(minion.level(), minion.getX(), minion.getY(), minion.getZ(), new ItemStack(Items.CHEST)));
        }
    }

    /** The server stopped: forget anything the group keeps per world. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
    }
}
