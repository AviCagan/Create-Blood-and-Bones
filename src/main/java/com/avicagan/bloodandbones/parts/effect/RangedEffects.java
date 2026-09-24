package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.carcass.Blood;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.avicagan.bloodandbones.parts.TraitEvents;
import com.avicagan.bloodandbones.parts.Trigger;
import com.avicagan.bloodandbones.registry.BBLang;
import com.mojang.serialization.MapCodec;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ranged: the vanilla adapter (any enchantment entity effect) and our 7 actions in vanilla's registry (launch, pull,
 * web, bleed, steal_item, blink_target, ink_cloud; docs/PARTS-AND-TRAITS.md section 5.5), the Bleeding mob effect
 * ("Leaking" in bloodless mode), the temporary_web and cooled_crust blocks, projectile, and hitscan with its beam.
 * <p>
 * Everything the group adds goes through this file and its own, so the four groups never edit the same file
 * (docs/ARCHITECTURE-PROPOSAL.md section 15.8): its effect types ({@link #types}), what it registers
 * ({@link #registerContent}), its words ({@link #lang}), its payloads ({@link #payloads}), the goals it gives
 * minions ({@link #minionGoals}), the hooks the shared code calls, and its NeoForge-bus handlers, here (this class is
 * registered on {@code NeoForge.EVENT_BUS}) or in classes it registers itself. Its client side is
 * {@link com.avicagan.bloodandbones.client.effect.RangedClient}.
 */
public final class RangedEffects {
    /** Work to do once the tick is over (a throw that must land after the knockback of the hit that caused it). */
    private static final List<Runnable> AFTER_TICK = new ArrayList<>();
    /** A minion's shot landing this tick: while it does, its minion may not break or light blocks. */
    @Nullable
    private static Projectile landing;

    private RangedEffects() {
    }

    /**
     * Its trait effect types, into the {@code bloodandbones:trait_effect_type} registry, each a record in its own file in
     * this package: {@code types.register("impulse", () -> ImpulseEffect.CODEC);}.
     */
    public static void types(DeferredRegister<MapCodec<? extends TraitEffect.Effect>> types) {
        types.register("vanilla", () -> VanillaEffect.CODEC);
        types.register("projectile", () -> ProjectileEffect.CODEC);
        types.register("hitscan", () -> HitscanEffect.CODEC);
    }

    /**
     * What it registers besides effect types (blocks, items, mob effects, entity types, sounds, loot condition or
     * enchantment effect types), through its own DeferredRegisters on the mod bus (or Registrate entries in its own
     * class, loaded from here). Called once, from the mod's constructor.
     */
    public static void registerContent(IEventBus modBus) {
        RangedContent.register(modBus);
    }

    /**
     * Its words: trait names and descriptions ({@code BBLang.trait}), and bloodless wording where the general rewording
     * would not read right ({@code BBLang.bloodless}). Called from {@code BBLang.register}, so datagen writes them.
     */
    public static void lang() {
        // actives: fired with the Organ Ability key (a minion fires them at its target)
        BBLang.trait("fireball", "Fireball", "Ability: three small fireballs, where you look.");
        BBLang.trait("great_fireball", "Great Fireball", "Ability: a ghast's great fireball, bursting where it lands (it breaks blocks only where mobs may, and never a minion's).");
        BBLang.trait("web_shot", "Web Shot", "Ability: a flick of silk at what you look at, stinging, leaving it stuck in a web for 5 seconds.");
        BBLang.trait("spit", "Spit", "Ability: a gob of spit, where you look.");
        BBLang.trait("shulker_bolt", "Shulker Bolt", "Ability: a shulker's homing bolt at what you look at; what it hits floats up.");
        BBLang.trait("sonic_boom", "Sonic Boom", "Ability: a warden's blast of sound, charged for a moment, tearing through armour at what you look at.");
        BBLang.trait("guardian_beam", "Guardian Beam", "Ability: a guardian's beam locked on what you look at, burning into it three seconds later.");
        BBLang.trait("fangs", "Fangs", "Ability: evoker fangs snap up out of the ground under what you look at.");
        BBLang.trait("snowball_volley", "Snowball Volley", "Ability: a spray of snowballs. A minion keeps its distance and pelts what it fights.");
        BBLang.trait("tongue", "Tongue", "Ability: a sticky tongue yanks what you look at, up to 6 blocks off, over to you.");
        BBLang.trait("bowman", "Bowman", "A minion's arms loose arrows at what it fights, keeping their distance, a drop of blood a shot.");
        // on hit
        BBLang.trait("webbing", "Webbing", "A fifth of your blows leave what you hit stuck in a web for 3 seconds.");
        BBLang.trait("bleeding", "Bleeding", "Your blows open wounds that bleed for 3 seconds a level.");
        BBLang.bloodless("trait.bloodandbones.bleeding", "Leaking");
        BBLang.bloodless("trait.bloodandbones.bleeding.desc", "Your blows spring leaks that last 3 seconds a level.");
        BBLang.trait("mauler", "Mauler", "Your blows tear: 1 more damage, and the wound bleeds for 3 seconds.");
        BBLang.bloodless("trait.bloodandbones.mauler.desc", "Your blows tear: 1 more damage, and the breach leaks for 3 seconds.");
        BBLang.trait("flinger", "Flinger", "Your blows fling what you hit up into the air, higher each level.");
        BBLang.trait("displacer", "Displacer", "Now and then a blow blinks what you hit off somewhere within 8 blocks.");
        BBLang.trait("thief", "Thief", "Now and then a blow snatches what a mob holds (never a player's).");
        BBLang.trait("searing", "Searing", "Your blows set what you hit alight, 2 seconds a level.");
        // when hurt
        BBLang.trait("barbed", "Barbed", "Whatever hits you up close takes 1 damage a level from your spikes.");
        BBLang.trait("ember_skin", "Ember Skin", "Whatever hits you up close burns for 2 seconds.");
        BBLang.raw("trait.bloodandbones.ink_cloud.desc", "Hurt, a minion squirts a cloud of ink that blinds everything within 4 blocks for 3 seconds; worn, it does when you are below half health.");
        // underfoot
        BBLang.trait("lava_wader", "Lava Wader", "Lava crusts over under your feet and melts back behind you; fire hurts you a quarter less.");
        BBLang.trait("frost_path", "Frost Path", "Water freezes under your feet, as with Frost Walker.");
        // the Bleeding effect: Leaking in bloodless mode
        BBLang.raw("effect.bloodandbones.bleeding", "Bleeding");
        BBLang.bloodless("effect.bloodandbones.bleeding", "Leaking");
        BBLang.raw("death.attack.bloodandbones.bleeding", "%1$s bled out");
        BBLang.bloodless("death.attack.bloodandbones.bleeding", "%1$s leaked dry");
        BBLang.raw("death.attack.bloodandbones.bleeding.player", "%1$s bled out whilst fighting %2$s");
        BBLang.bloodless("death.attack.bloodandbones.bleeding.player", "%1$s leaked dry whilst fighting %2$s");
    }

    /** Its network payloads; a client-bound one's handler calls into {@code RangedClient} inside its lambda. */
    public static void payloads(PayloadRegistrar registrar) {
        registrar.playToClient(BeamPayload.TYPE, BeamPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.avicagan.bloodandbones.client.effect.RangedClient.receive(payload)));
    }

    /** Goals it gives every minion as it is made (they may look at the minion's traits each time they are asked). */
    public static void minionGoals(MinionEntity minion, GoalSelector goals, GoalSelector targets) {
        // ahead of the melee goal, so a minion with a shot keeps its distance (it gives way within 4 blocks, and to hunger)
        goals.addGoal(1, new MinionRangedGoal(minion));
    }

    // ---- a minion's ranged attack

    /** A minion's shots: its passive projectile and hitscan entries (its arms' or organ's), which it fires as its ranged attack. */
    public static List<ActiveTraits.Found<?>> shots(ActiveTraits traits) {
        List<ActiveTraits.Found<?>> out = new ArrayList<>();
        for (ActiveTraits.Found<ProjectileEffect> found : traits.find(ProjectileEffect.class)) {
            if (found.facet().trigger() == Trigger.PASSIVE) {
                out.add(found);
            }
        }
        for (ActiveTraits.Found<HitscanEffect> found : traits.find(HitscanEffect.class)) {
            if (found.facet().trigger() == Trigger.PASSIVE) {
                out.add(found);
            }
        }
        return out;
    }

    /**
     * The minion's ranged attack goes off (vanilla's RangedAttackGoal calls this through {@code MinionEntity}): each of its
     * shots within range of the target, off cooldown, its condition holding, its chance come up and its blood cost paid
     * (never the last drop), fires at the target.
     */
    public static void rangedAttack(MinionEntity minion, LivingEntity target, float velocity) {
        ActiveTraits traits = ActiveTraits.of(minion);
        for (ActiveTraits.Found<?> shot : shots(traits)) {
            float range = shot.facet().range();
            if (minion.distanceToSqr(target) > range * range || minion.power() - shot.facet().costMb() < 1.0F
                    || TraitEvents.coolingDown(minion, shot.entry(), shot.index()) || !TraitEvents.holds(minion, shot.entry(), shot.facet(), null)
                    || !TraitEvents.ready(minion, shot.entry(), shot.index(), shot.facet()) || !minion.usePower(shot.facet().costMb())) {
                continue;
            }
            shot.effect().run(new TraitContext(minion, traits, shot.entry(), shot.index(), shot.facet(), shot.facet().trigger(), target, null, 0.0F));
        }
    }

    // ---- shots in flight

    /** Mark a projectile as one of ours; with a damage, it does that to what it hits in place of its own. */
    static void markShot(Projectile shot, @Nullable Float damage) {
        shot.setData(RangedContent.SHOT, damage == null ? -1.0F : Math.max(0.0F, damage));
    }

    /** Whether this was fired by one of our effects. */
    public static boolean isShot(@Nullable Entity entity) {
        return entity instanceof Projectile && entity.hasData(RangedContent.SHOT);
    }

    /** A shot of ours hitting: the damage its effect gave it, in place of its own (not its blast's, which is left alone). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (isShot(direct) && !event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            float damage = direct.getData(RangedContent.SHOT);
            if (damage >= 0.0F) {
                event.setAmount(damage);
            }
        }
    }

    /** A shot of ours passes through its own side; one of a minion's that lands is noted, so it breaks and lights nothing. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onImpact(ProjectileImpactEvent event) {
        Projectile shot = event.getProjectile();
        if (!isShot(shot) || shot.level().isClientSide) {
            return;
        }
        if (event.getRayTraceResult() instanceof EntityHitResult hit && RangedAim.friendly(shot.getOwner(), hit.getEntity())) {
            event.setCanceled(true);
            return;
        }
        if (shot.getOwner() instanceof MinionEntity) {
            landing = shot;
        }
    }

    /**
     * A minion's blasts and fires break and light nothing (the design's minion_block_damage, off, on top of mobGriefing):
     * asked of the shot itself (a great fireball's or wither skull's blast) or of its minion while its shot lands (a small
     * fireball's fire).
     */
    @SubscribeEvent
    public static void onGrief(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (isShot(entity) && ((Projectile) entity).getOwner() instanceof MinionEntity
                || entity instanceof MinionEntity && landing != null && landing.getOwner() == entity) {
            event.setCanGrief(false);
        }
    }

    /** Once a tick: throws land, charged hitscans fire. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        landing = null;
        if (!AFTER_TICK.isEmpty()) {
            List<Runnable> due = new ArrayList<>(AFTER_TICK);
            AFTER_TICK.clear();
            due.forEach(Runnable::run);
        }
        HitscanEffect.tickCharging();
    }

    /** Do this once the tick is over, on the server thread. */
    public static void afterTick(Runnable work) {
        AFTER_TICK.add(work);
    }

    /** What has no blood leaks grey sparks rather than dripping (its drops, on a client in bloodless mode, are sparks too). */
    @SubscribeEvent
    public static void onEffectParticles(EffectParticleModificationEvent event) {
        if (event.getEffect().is(RangedContent.BLEEDING) && !Blood.bleeds(event.getEntity())) {
            event.setParticleOptions(RangedContent.LEAK_SPARK.get());
        }
    }

    // ---- NeoForge bus

    /** The server stopped: forget anything the group keeps per world. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        AFTER_TICK.clear();
        HitscanEffect.forget();
        landing = null;
    }
}
