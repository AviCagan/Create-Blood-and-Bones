package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.avicagan.bloodandbones.parts.TraitEffects;
import com.avicagan.bloodandbones.parts.TraitEvents;
import com.avicagan.bloodandbones.parts.Trigger;
import com.avicagan.bloodandbones.registry.BBLang;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

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
 * <p>
 * What it keeps per world, all forgotten when the server stops: when a trait's carrier last hurt each mob (a kin or a
 * friendly reaction lets that mob fight back for a while), which mobs an aura calmed and until when, and when each aura
 * last went off.
 */
public final class SocialEffects {
    /** A friendly reaction lets a mob the carrier hurt fight back this long. */
    public static final int FRIENDLY_PROVOKED_SECONDS = 30;

    /** For each mob hurt by a trait's carrier: who hurt it (by UUID), and when (game time). */
    private static final Map<LivingEntity, Map<UUID, Long>> HURT_BY = Collections.synchronizedMap(new WeakHashMap<>());
    /** Mobs an aura calmed: whom they leave be (the carrier and its side), and until when. */
    private static final Map<Mob, Calm> CALMED = Collections.synchronizedMap(new WeakHashMap<>());
    /** When each carrier's auras last went off, by trait and effect. */
    private static final Map<LivingEntity, Map<String, Long>> PULSES = Collections.synchronizedMap(new WeakHashMap<>());

    private record Calm(UUID host, @Nullable UUID side, long until) {
    }

    private SocialEffects() {
    }

    /**
     * Its trait effect types, into the {@code bloodandbones:trait_effect_type} registry, each a record in its own file in
     * this package: {@code types.register("impulse", () -> ImpulseEffect.CODEC);}.
     */
    public static void types(DeferredRegister<MapCodec<? extends TraitEffect.Effect>> types) {
        types.register("kin", () -> KinEffect.CODEC);
        types.register("sense", () -> SenseEffect.CODEC);
        types.register("aura", () -> AuraEffect.CODEC);
        types.register("pack", () -> PackEffect.CODEC);
        types.register("glow", () -> GlowEffect.CODEC);
    }

    /**
     * What it registers besides effect types (blocks, items, mob effects, entity types, sounds, loot condition or
     * enchantment effect types), through its own DeferredRegisters on the mod bus (or Registrate entries in its own
     * class, loaded from here). Called once, from the mod's constructor. Social needs none: its sounds are vanilla's,
     * pitched and layered, and its reactions' mob lists are entity type tags.
     */
    public static void registerContent(IEventBus modBus) {
    }

    /**
     * Its words: trait names and descriptions ({@code BBLang.trait}), and bloodless wording where the general rewording
     * would not read right ({@code BBLang.bloodless}). Called from {@code BBLang.register}, so datagen writes them.
     */
    public static void lang() {
        // kin and reactions
        BBLang.trait("dead_face", "Dead Face",
                "Zombies take you for one of their own and leave you be. One you hurt fights back for 30 seconds.");
        BBLang.trait("skeleton_kin", "Skeleton Kin",
                "Skeletons take you for one of their own and leave you be. One you hurt fights back for 30 seconds.");
        BBLang.trait("raider_kin", "Raider Kin",
                "Illagers, witches and ravagers take you for one of the raid and leave you be. One you hurt fights back for 30 seconds.");
        BBLang.trait("ender_calm", "Ender Calm",
                "Endermen take no notice of it, even looked at. One it hurts fights back for 30 seconds.");
        BBLang.trait("golem_trust", "Trustworthy",
                "Iron and snow golems trust you, whatever the villagers say of you. One you hurt fights back for 30 seconds.");
        BBLang.trait("beloved", "Beloved",
                "Golems trust you and come to your defence: whatever hurts you within 16 blocks, or whatever you fight, they go for.");
        // senses
        BBLang.trait("echo_sense", "Echo Sense",
                "Every 5 seconds a wet click shows you the creatures within 16 blocks (24 at II), through walls. A minion hunts by it through walls.");
        BBLang.bloodless("trait.bloodandbones.echo_sense.desc",
                "Every 5 seconds a sharp click shows you the creatures within 16 blocks (24 at II), through walls. A minion hunts by it through walls.");
        BBLang.trait("tremor_sense", "Tremor Sense",
                "You feel anything moving within 16 blocks through the ground, through walls. Still or sneaking things go unfelt. A minion hunts by it through walls.");
        BBLang.trait("spectral_sight", "Spectral Sight",
                "Invisible creatures within 16 blocks show as outlines, and a minion sees them as plain as day.");
        BBLang.trait("blood_scent", "Blood Scent",
                "You smell out anything below half its health within 16 blocks, through walls, and see it drip.");
        BBLang.bloodless("trait.bloodandbones.blood_scent", "Damage Sense");
        BBLang.bloodless("trait.bloodandbones.blood_scent.desc",
                "You sense anything below half its health within 16 blocks, through walls, and see it spark.");
        BBLang.trait("wide_eyes", "Wide Eyes",
                "A warning, and which way to look, whenever something within 12 blocks sets its sights on you.");
        // pack and auras
        BBLang.trait("pack_hunter", "Pack Hunter",
                "15% more damage for each ally within 8 blocks, up to 3: your minions, your pets and any wolf or fox.");
        BBLang.trait("item_magnet", "Item Magnet",
                "Loose items within 5 blocks hop to you (7 at II, 9 at III).");
        BBLang.trait("horde_call", "Horde Call",
                "Hurt, and your minions and every zombie within 16 blocks turn on whatever did it.");
        BBLang.trait("purr", "Purr",
                "Every 10 seconds, your minions and pets within 4 blocks get Regeneration I.");
        BBLang.trait("toxin_puff", "Toxin Puff",
                "Anything not on your side that comes within 2 blocks is poisoned.");
        BBLang.trait("glow_aura", "Glow Aura",
                "Hostile creatures within 8 blocks glow, for everyone to see.");
        BBLang.trait("wither_aura", "Wither Aura",
                "Anything not on your side within 4 blocks withers.");
        BBLang.trait("fatigue_aura", "Fatigue Aura",
                "Once a minute, hostile creatures within 16 blocks get Mining Fatigue II.");
        BBLang.trait("roar", "Roar",
                "Organ Ability: a slobbering roar that throws everything within 4 blocks back and slows it. Every 20 seconds.");
        BBLang.bloodless("trait.bloodandbones.roar.desc",
                "Core Ability: a grinding roar that throws everything within 4 blocks back and slows it. Every 20 seconds.");
        BBLang.trait("play_dead", "Play Dead",
                "Below a quarter health, a hit sets you playing dead: Regeneration II, and hostile creatures within 16 blocks lose you for 5 seconds. Once a minute.");
        // drawbacks read by the near condition
        BBLang.trait("cat_terror", "Cat Terror",
                "Within 8 blocks of a cat or an ocelot you cower: Slowness II and Weakness.");
        BBLang.trait("warped_dread", "Warped Dread",
                "Within 7 blocks of a warped fungus you shrink back: Slowness and Weakness.");
        // glow
        BBLang.trait("luminous", "Luminous",
                "The piece glows in the dark, as a glow squid does. A minion shines from head to foot.");
        // the alert sense's ping
        BBLang.raw("bloodandbones.sense.alert", "%s has its eye on you");
    }

    /** Its network payloads; a client-bound one's handler calls into {@code SocialClient} inside its lambda. */
    public static void payloads(PayloadRegistrar registrar) {
        registrar.playToClient(SensePayload.TYPE, SensePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.avicagan.bloodandbones.client.effect.SocialClient.receive(payload)));
        registrar.playToClient(AlertPayload.TYPE, AlertPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.avicagan.bloodandbones.client.effect.SocialClient.receive(payload)));
    }

    /** Goals it gives every minion as it is made: hunting by echolocation or tremor, through walls. */
    public static void minionGoals(MinionEntity minion, GoalSelector goals, GoalSelector targets) {
        targets.addGoal(3, new SocialGoals.SenseTarget(minion));
    }

    // ---- what the effects share

    /** Whether the host hurt this mob within the last so many seconds (so it may fight back despite kin). */
    public static boolean provoked(LivingEntity mob, LivingEntity host, int seconds) {
        Map<UUID, Long> by = HURT_BY.get(mob);
        Long at = by == null ? null : by.get(host.getUUID());
        return at != null && host.level().getGameTime() - at <= seconds * 20L;
    }

    /** A mob gives up whatever it was going for: its target, its brain's attack target, its anger. */
    public static void callOff(Mob mob) {
        mob.setTarget(null);
        if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
        if (mob instanceof NeutralMob neutral) {
            neutral.stopBeingAngry();
        }
    }

    /** An aura calmed this mob: it leaves the host and the host's side be for this many ticks. */
    public static void calm(Mob mob, LivingEntity host, int ticks) {
        if (ticks > 0) {
            CALMED.put(mob, new Calm(host.getUUID(), SocialFilter.side(host), host.level().getGameTime() + ticks));
        }
    }

    /** Whether this mob is calm towards this target now. */
    public static boolean calmed(Mob mob, LivingEntity target) {
        Calm calm = CALMED.get(mob);
        if (calm == null) {
            return false;
        }
        if (mob.level().getGameTime() >= calm.until()) {
            CALMED.remove(mob);
            return false;
        }
        return target.getUUID().equals(calm.host()) || calm.side() != null && calm.side().equals(SocialFilter.side(target));
    }

    /** Whether an aura may go off now (not in the last second); notes that it does. */
    public static boolean pulseReady(LivingEntity host, ActiveTraits.Entry entry, int index) {
        long now = host.level().getGameTime();
        Map<String, Long> own = PULSES.computeIfAbsent(host, h -> new HashMap<>());
        String key = entry.id() + "#" + index;
        Long last = own.get(key);
        if (last != null && now - last < 20 && now >= last) {
            return false;
        }
        own.put(key, now);
        return true;
    }

    // ---- NeoForge bus

    /**
     * A mob setting its sights on a trait's carrier: called off if an aura calmed it (until the carrier hurts it again),
     * or if the carrier is its kin or its reaction to the carrier is friendly, unless the carrier hurt that very mob lately.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null || event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (calmed(mob, target) || KinEffect.spares(mob, target) || friendly(mob, target)) {
            event.setCanceled(true);
        }
    }

    /** A reaction in "friendly" mode on the target for mobs of this kind, and the target has not hurt this one lately. */
    private static boolean friendly(Mob mob, LivingEntity target) {
        ActiveTraits traits = ActiveTraits.peek(target);
        if (traits.isEmpty()) {
            return false;
        }
        for (ActiveTraits.Found<TraitEffects.ReactionEffect> found : traits.find(TraitEffects.ReactionEffect.class)) {
            if ("friendly".equals(found.effect().mode()) && mob.getType().is(found.effect().entities())
                    && !provoked(mob, target, FRIENDLY_PROVOKED_SECONDS)) {
                return true;
            }
        }
        return false;
    }

    /** Something set its sights on a carrier of an alert sense (and was let): a ping, if it is within the sense's reach. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTargeted(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null || event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob) || mob.getTarget() == target) {
            return;
        }
        for (ActiveTraits.Found<SenseEffect> found : SenseEffect.of(target, "alert")) {
            if (found.facet().trigger() == Trigger.PASSIVE && found.effect().senses(target, mob, found.entry().level())
                    && TraitEvents.holds(target, found.entry(), found.facet(), null)) {
                SenseEffect.alert(target, mob);
                return;
            }
        }
    }

    /** A trait's carrier hurt something: noted, so that one may fight back despite kin, for a while, and is calm no more. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F || event.getEntity().level().isClientSide
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == event.getEntity() || ActiveTraits.peek(attacker).isEmpty()) {
            return;
        }
        HURT_BY.computeIfAbsent(event.getEntity(), m -> new HashMap<>()).put(attacker.getUUID(), attacker.level().getGameTime());
        // a calmed mob hurt by the one it was calmed towards is calm no longer
        if (event.getEntity() instanceof Mob mob && calmed(mob, attacker)) {
            CALMED.remove(mob);
        }
    }

    /** A pack hunter's hit, harder for its allies near. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && !attacker.level().isClientSide) {
            float multiplier = PackEffect.multiplier(attacker, event.getEntity(), event.getSource());
            if (multiplier != 1.0F) {
                event.setAmount(event.getAmount() * multiplier);
            }
        }
    }

    /** A minion that sees the invisible (see_invisible) finds them as plain as anything else within its sense's reach. */
    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        LivingEntity seen = event.getEntity();
        if (!seen.isInvisible() || !(event.getLookingEntity() instanceof LivingEntity looker) || ActiveTraits.peek(looker).isEmpty()) {
            return;
        }
        for (ActiveTraits.Found<SenseEffect> found : SenseEffect.of(looker, "see_invisible")) {
            if (found.effect().senses(looker, seen, found.entry().level()) && TraitEvents.holds(looker, found.entry(), found.facet(), null)) {
                // undo what being invisible took off (LivingEntity#getVisibilityPercent)
                event.modifyVisibility(1.0 / (0.7 * Math.max(0.1F, seen.getArmorCoverPercentage())));
                return;
            }
        }
    }

    /** The server stopped: forget anything the group keeps per world. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HURT_BY.clear();
        CALMED.clear();
        PULSES.clear();
        SenseEffect.clear();
    }
}
