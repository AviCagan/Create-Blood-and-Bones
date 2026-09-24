package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.carcass.Blood;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.CarcassArmourItem;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.avicagan.bloodandbones.parts.TraitEffects;
import com.avicagan.bloodandbones.parts.TraitEvents;
import com.avicagan.bloodandbones.parts.Trigger;
import com.avicagan.bloodandbones.registry.BBLang;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.EnderManAngerEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Motion: the flag effect (all 11 flags of docs/PARTS-AND-TRAITS.md section 5.6: climb, glide, bounce, powder_snow,
 * ender_mask, piglin_neutral, silent_steps, quick_draw, inverted_healing, trample, lava_walk), impulse, teleport,
 * deflect, detonate and visibility.
 * <p>
 * Everything the group adds goes through this file and its own, so the four groups never edit the same file
 * (docs/ARCHITECTURE-PROPOSAL.md section 15.8): its effect types ({@link #types}), what it registers
 * ({@link #registerContent}), its words ({@link #lang}), its payloads ({@link #payloads}), the goals it gives
 * minions ({@link #minionGoals}), the hooks the shared code calls, and its NeoForge-bus handlers, here (this class is
 * registered on {@code NeoForge.EVENT_BUS}) or in classes it registers itself. Its client side is
 * {@link com.avicagan.bloodandbones.client.effect.MotionClient}.
 */
public final class MotionEffects {
    /** Projectiles a host let by (dodged, or let pass through), so they fly on through it rather than hit it next tick. */
    private static final Map<Projectile, LivingEntity> LET_BY = Collections.synchronizedMap(new WeakHashMap<>());

    private MotionEffects() {
    }

    /**
     * Its trait effect types, into the {@code bloodandbones:trait_effect_type} registry, each a record in its own file in
     * this package: {@code types.register("impulse", () -> ImpulseEffect.CODEC);}.
     */
    public static void types(DeferredRegister<MapCodec<? extends TraitEffect.Effect>> types) {
        types.register("flag", () -> FlagEffect.CODEC);
        types.register("impulse", () -> ImpulseEffect.CODEC);
        types.register("teleport", () -> TeleportEffect.CODEC);
        types.register("deflect", () -> DeflectEffect.CODEC);
        types.register("detonate", () -> DetonateEffect.CODEC);
        types.register("visibility", () -> VisibilityEffect.CODEC);
    }

    /**
     * What it registers besides effect types (blocks, items, mob effects, entity types, sounds, loot condition or
     * enchantment effect types), through its own DeferredRegisters on the mod bus (or Registrate entries in its own
     * class, loaded from here). Called once, from the mod's constructor. Motion needs none: its one tag
     * ({@link MotionFlags#TRAMPLEABLE}) is data.
     */
    public static void registerContent(IEventBus modBus) {
    }

    /**
     * Its words: trait names and descriptions ({@code BBLang.trait}), and bloodless wording where the general rewording
     * would not read right ({@code BBLang.bloodless}). Called from {@code BBLang.register}, so datagen writes them.
     */
    public static void lang() {
        BBLang.trait("wall_climber", "Wall Climber", "Cling to walls, sliding slowly down; on two pieces, climb them while holding jump.");
        BBLang.trait("glider", "Glider", "The chestplate spreads like an elytra: glide by jumping in mid-air. Costs hunger, not durability.");
        BBLang.trait("bouncy", "Bouncy", "Bounce back up from a long fall instead of taking damage. Crouch to land flat.");
        BBLang.trait("powder_walker", "Powder Walker", "Walk on powder snow without sinking (worn on carcass boots).");
        BBLang.trait("silent_steps", "Silent Steps", "Your steps, landings and splashes make no vibrations for sculk or wardens to hear.");
        BBLang.trait("quick_draw", "Quick Draw", "Draw bows and load crossbows twice as fast.");
        BBLang.trait("trample", "Trample", "A minion breaks through leaves, grass and flowers in its way (where the server allows minions to break blocks).");
        BBLang.trait("lava_walk", "Lava Walker", "A minion walks on lava as a strider does, and fire does not hurt it.");
        BBLang.trait("ender_mask", "Ender Mask", "Endermen do not mind being looked at.");
        // ender_calm and piglin_kin are worded with the other kin traits, in SocialEffects.
        BBLang.trait("inverted_healing", "Inverted Healing", "Healing potions hurt you and harming potions heal you, as with the undead.");
        BBLang.trait("insulated", "Insulated", "Freezing does not hurt you, and you walk on powder snow (worn on carcass boots).");
        BBLang.trait("evasive", "Evasive", "A chance to twist aside from a blow: a tenth a level.");
        BBLang.trait("deflector", "Deflector", "A chance to send projectiles back the way they came: a quarter a level.");
        BBLang.trait("hiss", "Hiss", "Mobs notice you only half as far off.");
        BBLang.trait("leap", "Leap", "Ability: spring forward and up. Every 3 seconds.");
        BBLang.trait("dash", "Dash", "Ability: dash forward along the ground. 20 mB of blood, every 5 seconds.");
        BBLang.trait("charge", "Charge", "Ability: lunge forward, throwing aside everything around you. Every 10 seconds.");
        BBLang.trait("warp", "Warp", "Ability: blink up to 8 blocks where you look. 50 mB of blood, every 6 seconds.");
        BBLang.trait("wind_burst", "Wind Burst", "Ability: a burst of wind throws you high, and you take no fall damage until you land. 40 mB of blood, every 10 seconds.");
        BBLang.trait("blast", "Blast", "Ability, below half health: a blast around you that spares you. 50 mB of blood, every minute.");
        BBLang.trait("self_destruct", "Self-Destruct", "A minion close to its target hisses and blows up, sparing itself, then lies powered down until it is given blood again.");
        BBLang.trait("rift", "Rift", "When hurt, a 30% chance to blink a few blocks away.");
        BBLang.trait("blink", "Blink", "When hurt, an even chance to blink up to 16 blocks away.");
        BBLang.trait("fly_swat", "Fly Swat", "Whatever hits you from behind is swatted back.");
        BBLang.trait("loyal", "Loyal", "A minion following its maker blinks back to them when it falls more than 24 blocks behind.");
        // the organ behind the creeper's blast, and a core in bloodless mode, as the other organs are
        BBLang.raw("organ.bloodandbones.powder_sac", "Powder Sac");
        BBLang.bloodless("organ.bloodandbones.powder_sac", "Powder Core");
        BBLang.raw("bloodandbones.configuration.minion_block_damage", "Minions break blocks");
        BBLang.raw("bloodandbones.configuration.minion_block_damage.tooltip",
                "Whether minions and trait blasts may break blocks (a self-destruct, trampling), where the mobGriefing game rule also allows it.");
    }

    /** Its network payloads; a client-bound one's handler calls into {@code MotionClient} inside its lambda. */
    public static void payloads(PayloadRegistrar registrar) {
    }

    /**
     * Goals it gives every minion as it is made (they may look at the minion's traits each time they are asked): a
     * lava walker standing on lava keeps its footing, above the goal that would have it paddle up out of any fluid.
     */
    public static void minionGoals(MinionEntity minion, GoalSelector goals, GoalSelector targets) {
        goals.addGoal(-1, new LavaFooting(minion));
    }

    /**
     * While a lava walker stands on lava it holds the jump, so the float goal every minion has does not paddle it up and
     * down on the surface as if it were drowning. It does nothing else.
     */
    static final class LavaFooting extends Goal {
        private final MinionEntity minion;

        LavaFooting(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return MotionFlags.onLava(minion);
        }
    }

    // ---- hooks the shared code calls (carcass armour, minions)

    /**
     * The strength of a flag on this creature now, 0 for none (docs/PARTS-AND-TRAITS.md section 5.6): the strongest of
     * its passive flag effects of that name. A player's own client builds their traits from the armour it sees, for the
     * movement it predicts; the server reads what it built. A condition on a flag the client predicts is not read (see
     * {@link FlagEffect}).
     */
    public static int flag(LivingEntity host, String name) {
        if (ActiveTraits.contextOf(host) == null) {
            return 0;
        }
        ActiveTraits traits = host.level().isClientSide && host instanceof Player ? ActiveTraits.of(host) : ActiveTraits.peek(host);
        if (traits.isEmpty()) {
            return 0;
        }
        int best = 0;
        for (ActiveTraits.Found<FlagEffect> found : traits.find(FlagEffect.class)) {
            if (!found.effect().flag().equals(name) || found.facet().trigger() != Trigger.PASSIVE) {
                continue;
            }
            if (found.facet().requirements().isPresent() && !FlagEffect.PREDICTED.contains(name)
                    && !TraitEvents.holds(host, found.entry(), found.facet(), null)) {
                continue;
            }
            best = Math.max(best, (int) found.effect().strength().calculate(found.entry().level()));
        }
        return best;
    }

    /** A minion clings to the wall it is up against (the climb flag); its climbing legs are handled already. */
    public static boolean climbing(MinionEntity minion) {
        return minion.horizontalCollision && !minion.poweredDown() && flag(minion, FlagEffect.CLIMB) > 0;
    }

    /**
     * A minion walks on lava (lava_walk). Vanilla asks this with the lava itself; Sable's collision context
     * ({@code TheFasterEntityCollisionContext}) asks with the fluid above the lava's surface instead, so nothing is
     * ever above the lava there: over lava, that empty fluid counts as the lava it stands on.
     */
    public static boolean standsOn(MinionEntity minion, FluidState fluid) {
        if (fluid.is(FluidTags.LAVA) || fluid.isEmpty() && overLava(minion)) {
            return flag(minion, FlagEffect.LAVA_WALK) > 0;
        }
        return false;
    }

    /** Whether lava is where the creature stands or right under it. */
    private static boolean overLava(LivingEntity host) {
        net.minecraft.core.BlockPos at = host.blockPosition();
        return host.level().getFluidState(at).is(FluidTags.LAVA) || host.level().getFluidState(at.below()).is(FluidTags.LAVA);
    }

    /** A minion's steps make no vibrations (silent_steps). */
    public static boolean silent(MinionEntity minion) {
        return flag(minion, FlagEffect.SILENT_STEPS) > 0;
    }

    /** A carcass chestplate worn on the chest lets its wearer glide as an elytra does (the glide flag); asked on both sides. */
    public static boolean canGlide(ItemStack chestplate, LivingEntity wearer) {
        return chestplate.getItem() instanceof CarcassArmourItem item && item.getType() == ArmorItem.Type.CHESTPLATE
                && wearer.getItemBySlot(EquipmentSlot.CHEST) == chestplate && flag(wearer, FlagEffect.GLIDE) > 0;
    }

    /**
     * Each tick of a glide: whether it goes on. It costs hunger, not durability, and stops once the wearer is too hungry
     * to sprint (the same on both sides: the food level is the server's, sent to the player). Sculk hears it as an elytra's.
     */
    public static boolean glideTick(ItemStack chestplate, LivingEntity wearer, int flightTicks) {
        if (wearer instanceof Player player && !player.getAbilities().instabuild) {
            if (player.getFoodData().getFoodLevel() <= 6) {
                return false;
            }
            if (!player.level().isClientSide && (flightTicks + 1) % 10 == 0) {
                player.causeFoodExhaustion(0.2F);
            }
        }
        if (!wearer.level().isClientSide && (flightTicks + 1) % 10 == 0) {
            wearer.gameEvent(GameEvent.ELYTRA_GLIDE);
        }
        return true;
    }

    // ---- shared helpers for the group's effects

    /**
     * Whether a point is where an arc asks, from the host's facing: anywhere ("any"), ahead of it ("front") or behind
     * it ("behind"). No point is never in a named arc.
     */
    public static boolean inArc(LivingEntity host, @Nullable Vec3 point, String arc) {
        if ("any".equals(arc)) {
            return true;
        }
        if (point == null) {
            return false;
        }
        Vec3 facing = Vec3.directionFromRotation(0.0F, host.getYRot());
        double dot = facing.x * (point.x - host.getX()) + facing.z * (point.z - host.getZ());
        return "front".equals(arc) ? dot > 0.0 : "behind".equals(arc) && dot < 0.0;
    }

    /** The creatures within a radius of the host that its shoves and blasts may throw: not itself, nor its own side. */
    static List<LivingEntity> around(LivingEntity host, float radius) {
        if (radius <= 0.0F) {
            return List.of();
        }
        return host.level().getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(radius),
                e -> e != host && e.isAlive() && !e.isSpectator() && e.distanceToSqr(host) <= radius * radius && !ownSide(host, e));
    }

    /** A minion and its maker are on one side, and a maker's minions with each other. */
    static boolean ownSide(LivingEntity host, LivingEntity other) {
        java.util.UUID maker = host instanceof MinionEntity m ? m.makerId() : host instanceof Player p ? p.getUUID() : null;
        if (maker == null) {
            return false;
        }
        return other instanceof Player p ? p.getUUID().equals(maker) : other instanceof MinionEntity m && maker.equals(m.makerId());
    }

    /**
     * A wet burst out of the host: blood for flesh (bloodless mode draws none; the drops' own client check), sparks for
     * brass, nothing for a creature with no blood.
     */
    static void gore(ServerLevel level, LivingEntity host, Vec3 at, int amount) {
        if (host instanceof MinionEntity minion && minion.cybernetic()) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, amount, 0.3, 0.3, 0.3, 0.2);
        } else if (Blood.bleeds(host)) {
            Blood.burst(level, new Vector3d(at.x, at.y, at.z), amount);
        }
    }

    /** Scraps of meat thrown out of a flesh host (none in bloodless mode, none from brass). */
    static void gibs(ServerLevel level, LivingEntity host, Vec3 at, int amount) {
        if (!(host instanceof MinionEntity minion && minion.cybernetic()) && Blood.bleeds(host)) {
            Blood.gibs(level, new Vector3d(at.x, at.y, at.z), amount);
        }
    }

    // ---- NeoForge bus

    /** A player's movement flags on the server (their client runs the same through {@code MotionClient}). */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!event.getEntity().level().isClientSide) {
            MotionFlags.moveTick(event.getEntity(), false);
        }
    }

    /** A bounce owed and a cushion, for creatures other than players (a player's go with their movement flags). */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof LivingEntity living && !(living instanceof Player)) {
            MotionFlags.settle(living);
        }
    }

    /** After a minion moves: a lava walker floats on lava; a trampler breaks what it walks into, and through now and then. */
    @SubscribeEvent
    public static void onEntityTicked(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof MinionEntity minion && !minion.level().isClientSide) {
            MotionFlags.floatOnLava(minion);
            if (minion.horizontalCollision || (minion.tickCount + minion.getId()) % 5 == 0) {
                MotionFlags.trample(minion);
            }
        }
    }

    /**
     * A landing: a bouncer bounces back up instead (on both sides, the client moving a player itself), and a creature a
     * burst threw lands softly. Before the trait engine's own fall handling, which a bounce leaves out.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFall(LivingFallEvent event) {
        LivingEntity host = event.getEntity();
        if (ActiveTraits.contextOf(host) != null && MotionFlags.bounce(host, event.getDistance())) {
            MotionFlags.landCushioned(host);
            event.setCanceled(true);
            return;
        }
        if (MotionFlags.landCushioned(host)) {
            event.setDamageMultiplier(0.0F);
        }
    }

    /** The fuses of hissing hosts burn down. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DetonateEffect.tickFuses();
    }

    /** A host's own blast leaves it out of what it hits. */
    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!DetonateEffect.SPARED.isEmpty() && event.getExplosion().getDirectSourceEntity() instanceof LivingEntity host
                && DetonateEffect.SPARED.contains(host)) {
            event.getAffectedEntities().remove(host);
        }
    }

    /** A projectile about to hit a host that turns projectiles: reflected, dodged or let through. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity host) || ActiveTraits.contextOf(host) == null) {
            return;
        }
        if (LET_BY.get(projectile) == host) {
            event.setCanceled(true);
            return;
        }
        ActiveTraits traits = ActiveTraits.peek(host);
        if (traits.isEmpty()) {
            return;
        }
        Vec3 from = host.position().subtract(projectile.getDeltaMovement().scale(4.0));
        for (ActiveTraits.Found<DeflectEffect> found : traits.find(DeflectEffect.class)) {
            DeflectEffect deflect = found.effect();
            if (!deflect.turnsProjectiles() || !deflect.turns(projectile) || !inArc(host, from, deflect.arc()) || !turned(host, found, null)) {
                continue;
            }
            event.setCanceled(true);
            ServerLevel level = (ServerLevel) host.level();
            switch (deflect.mode()) {
                case "reflect" -> {
                    // back the way it came, and now the host's: what it hits is the host's doing
                    projectile.deflect(ProjectileDeflection.REVERSE, host, host, false);
                    level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.SHIELD_BLOCK, host.getSoundSource(), 0.8F, 1.3F);
                    level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.SLIME_BLOCK_HIT, host.getSoundSource(), 1.0F, 0.6F);
                }
                case "dodge" -> {
                    LET_BY.put(projectile, host);
                    Vec3 path = projectile.getDeltaMovement();
                    sidestep(host, new Vec3(-path.z, 0.0, path.x));
                }
                default -> {
                    LET_BY.put(projectile, host);
                    level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.SLIME_SQUISH, host.getSoundSource(), 0.8F, 0.4F);
                }
            }
            return;
        }
    }

    /** A creature's own blow at a host that dodges blows: it twists aside and takes nothing. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity host = event.getEntity();
        DamageSource source = event.getSource();
        if (host.level().isClientSide || ActiveTraits.contextOf(host) == null || !(source.getDirectEntity() instanceof LivingEntity attacker)
                || source.getDirectEntity() != source.getEntity() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        ActiveTraits traits = ActiveTraits.peek(host);
        if (traits.isEmpty()) {
            return;
        }
        for (ActiveTraits.Found<DeflectEffect> found : traits.find(DeflectEffect.class)) {
            if (found.effect().turnsMelee() && inArc(host, attacker.position(), found.effect().arc()) && turned(host, found, source)) {
                event.setCanceled(true);
                sidestep(host, new Vec3(attacker.getZ() - host.getZ(), 0.0, host.getX() - attacker.getX()));
                return;
            }
        }
    }

    /** Whether a deflection comes off now: off cooldown, its condition holding and its chance coming up; starts its cooldown. */
    private static boolean turned(LivingEntity host, ActiveTraits.Found<DeflectEffect> found, @Nullable DamageSource source) {
        if (TraitEvents.coolingDown(host, found.entry(), found.index()) || !TraitEvents.holds(host, found.entry(), found.facet(), source)
                || host.getRandom().nextFloat() >= found.effect().chance().calculate(found.entry().level())) {
            return false;
        }
        TraitEvents.startCooldown(host, found.entry(), found.index(), found.facet().cooldown());
        return true;
    }

    /** A quick twist to one side (either, at random) of a line, with a wet slither. */
    private static void sidestep(LivingEntity host, Vec3 across) {
        if (across.lengthSqr() < 1.0E-6) {
            across = Vec3.directionFromRotation(0.0F, host.getYRot() + 90.0F);
        }
        Vec3 step = across.normalize().scale(host.getRandom().nextBoolean() ? 0.35 : -0.35);
        host.push(step.x, 0.05, step.z);
        host.hurtMarked = true;
        host.level().playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, host.getSoundSource(), 0.8F, 1.2F);
        host.level().playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, host.getSoundSource(), 0.6F, 1.1F);
    }

    /** Mobs notice a host with a visibility effect less (or more), as a mob's own head worn does for its kind. */
    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        LivingEntity host = event.getEntity();
        if (ActiveTraits.contextOf(host) == null) {
            return;
        }
        ActiveTraits traits = ActiveTraits.peek(host);
        if (traits.isEmpty()) {
            return;
        }
        for (ActiveTraits.Found<VisibilityEffect> found : traits.find(VisibilityEffect.class)) {
            if (found.effect().against(event.getLookingEntity()) && TraitEvents.holds(host, found.entry(), found.facet(), null)) {
                float m = found.effect().multiplier().calculate(found.entry().level());
                event.modifyVisibility(Math.max(0.0, 1.0 + (m - 1.0) * TraitEffects.strength()));
            }
        }
    }

    /** Silent steps: the host's steps, landings and splashes make no vibrations. */
    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        if (event.getCause() instanceof LivingEntity host && ActiveTraits.contextOf(host) != null
                && (event.getVanillaEvent().is(GameEvent.STEP) || event.getVanillaEvent().is(GameEvent.HIT_GROUND) || event.getVanillaEvent().is(GameEvent.SPLASH))
                && flag(host, FlagEffect.SILENT_STEPS) > 0) {
            event.setCanceled(true);
        }
    }

    /** Ender mask from any piece, not only the helmet vanilla asks. */
    @SubscribeEvent
    public static void onEnderManAnger(EnderManAngerEvent event) {
        if (flag(event.getPlayer(), FlagEffect.ENDER_MASK) > 0) {
            event.setCanceled(true);
        }
    }

    /** Endermen never go for a minion with the ender mask (ender calm). */
    @SubscribeEvent
    public static void onTargeting(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof EnderMan && event.getNewAboutToBeSetTarget() instanceof MinionEntity minion
                && flag(minion, FlagEffect.ENDER_MASK) > 0) {
            event.setCanceled(true);
        }
    }

    /**
     * Quick draw: a bow or crossbow being drawn gains this many extra ticks each tick, on both sides alike (the client
     * shows the draw, the server fires it).
     */
    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (event.getItem().getItem() instanceof ProjectileWeaponItem && ActiveTraits.contextOf(event.getEntity()) != null) {
            int extra = flag(event.getEntity(), FlagEffect.QUICK_DRAW);
            if (extra > 0 && event.getDuration() > 0) {
                event.setDuration(Math.max(0, event.getDuration() - extra));
            }
        }
    }

    /** The server stopped: forget anything the group keeps per world. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LET_BY.clear();
        DetonateEffect.forgetAll();
        MotionFlags.forgetAll();
    }
}
