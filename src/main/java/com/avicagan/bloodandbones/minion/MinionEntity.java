package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.config.BBServerConfig;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A minion (docs/PARTS-AND-TRAITS.md section 6): a torso with pieces stitched into its sockets on the Surgery
 * Table, woken with blood. What it is comes from its build ({@link MinionStats}): its size, health, speed, how it
 * moves, how hard it bites, how well it does each task ({@link MinionFitness}). Its maker gives it any task from one
 * list ({@link MinionTask}), at home or with them, and how far it reaches (docs/NEXT.md 1.3). It runs on the blood in
 * it, a little all the time and more when it moves, works or fights; low, it walks to a Blood Trough to drink; empty,
 * it powers down where it is and lies on its side, alive, until it gets blood again. Neglect never destroys it.
 */
public class MinionEntity extends PathfinderMob implements net.minecraft.world.entity.Saddleable, net.minecraft.world.entity.monster.RangedAttackMob,
        net.minecraft.world.entity.ItemSteerable, com.avicagan.bloodandbones.carcass.CarcassDrag.Dragger {
    private static final EntityDataAccessor<Optional<MinionBuild>> BUILD = SynchedEntityData.defineId(MinionEntity.class, MinionSerializers.BUILD.get());
    /** Its task, by id (docs/NEXT.md 1.1). */
    private static final EntityDataAccessor<String> TASK = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.STRING);
    /** Where its task is centred: 0 at home (a sentry's post, a surgeon's table), 1 with its maker. */
    private static final EntityDataAccessor<Byte> ANCHOR = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.BYTE);
    /** How far from there it works, as its maker set it; 0 for its task's own reach. */
    private static final EntityDataAccessor<Integer> REACH = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.INT);
    /**
     * Its fitness at its task now, and, on the Surgeon task where the surgeon file's switch lets it do the ritual's cutting,
     * what a stump it cuts costs in buckets (0: it does not cut), worked out on the server once a second for clients: the
     * surgery screen names the surgeon, its fitness and its stumps' price, and task and disposition files never go to clients.
     */
    private static final EntityDataAccessor<Float> TASK_FITNESS = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> STUMP = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DOWN = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.BOOLEAN);
    /** A brass minion's one module, by name ("" for none). */
    private static final EntityDataAccessor<String> MODULE = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.STRING);
    /** Up against a wall on climbing legs: synced, as the spider's is, so clients predict the climb. */
    private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.BOOLEAN);
    /** It walks on lava (the lava_walk flag): synced, so its client does not show it catching alight from the lava either. */
    private static final EntityDataAccessor<Boolean> LAVA_WALKER = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.BOOLEAN);
    /** How long a spur from its rider's stick lasts (a pig's carrot, a strider's fungus), as theirs does. */
    private static final EntityDataAccessor<Integer> BOOST_TIME = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.INT);
    /** How far past its reach of home a farmer picks up what lies about: what it reaped rolls off the crop (its own 8: 10). */
    public static final double RANGE = 2.0;
    /** A task done with its maker is done round them while they are in the same world and this near; else at home. */
    public static final double WITH_ME = 64.0;
    /** mB of blood a minute: idle, moving, working, fighting (docs/PARTS-AND-TRAITS.md section 6.7). */
    public static final float IDLE = 3.0F;
    public static final float MOVING = 15.0F;
    public static final float WORKING = 25.0F;
    public static final float FIGHTING = 40.0F;
    /** Below this share of its blood it goes to drink. */
    public static final float HUNGRY = 0.25F;
    /** Brass runs on a quarter of what flesh does. */
    public static final float BRASS_DRAIN = 0.25F;
    /** Flesh mends: a heart every five seconds, for 5 mB of blood each, while it has more than a tenth left. */
    public static final int REGEN_TICKS = 100;
    public static final float REGEN_COST = 5.0F;
    /** What a brass sheet mends on a brass minion. */
    public static final float SHEET_REPAIR = 10.0F;
    /** A bucket by hand goes in only when half of it fits (or half of all it holds, if it holds less), as a canister does. */
    public static final float HALF_BUCKET = 500.0F;
    /** Flesh keeps the hide traits of this many different mobs among its pieces (docs/PARTS-AND-TRAITS.md section 6.6). */
    public static final int HIDES = 3;

    @Nullable
    private UUID maker;
    /** The maker who woke it, while that very player is still about (a test's stand-in maker is never in the level). */
    @Nullable
    private Player makerEntity;
    private BlockPos home = BlockPos.ZERO;
    private boolean findingPath;
    /** Room for the most a minion can carry (spec 6.4: up to 54 with storage traits); {@link #slots} says how much it may use. */
    public final SimpleContainer inventory = new SimpleContainer(54);
    /** Brass only: what it may pick up, reap or go for (a Create filter, or any item). */
    private final MinionFilter filter = new MinionFilter();
    @Nullable
    private MinionStats stats;
    private int statsGeneration = -1;
    /** What its build brings to every task (docs/NEXT.md 1.2), worked out once with its stats; server side. */
    @Nullable
    private MinionFitness.Body fitnessBody;
    /** The task a data reload took from its body, and why (a lang key), for its status line until it is given another. */
    @Nullable
    private MinionTask lostTask;
    @Nullable
    private String lostReason;
    /** Why its task's work stands still just now ("no still water within 8 of home"), and until when that holds. */
    @Nullable
    private Component idle;
    private int idleUntil;
    /** Its fitness at its task, read a second at a time for the blood it uses at work. */
    private float workFitness = 1.0F;
    /** Where a rider sits, on its back where the saddle is drawn, before its turn: worked out with its stats. */
    private net.minecraft.world.phys.Vec3 seat = net.minecraft.world.phys.Vec3.ZERO;
    /** Where each of several riders sits, front first (a camel's two): the same frame. */
    private List<net.minecraft.world.phys.Vec3> seats = List.of();
    /** Blood not yet taken off a whole mB. */
    private float owed;
    /** A minion saved before minions were built of carcass parts: it falls apart on its first tick. */
    private boolean legacy;
    /** Set by task goals while they are doing something, for the drain. */
    boolean working;
    /** Which way of getting about its navigation is set up for: ground, climb, swim or fly. */
    private String movedBy = "";
    /** A sinker short of breath going up for air, until it has its fill again (see {@link #sinking}). */
    private boolean surfacing;
    /** Which arm strikes next: they take turns. */
    private int nextStrike;
    /** Crouch-held clicks by the maker on it powered down, toward folding it up. */
    private int foldClicks;
    private long lastFoldClick;
    /** A rider's spur from an item on a stick, as a pig's or a strider's (vanilla's own steering, on its saddle). */
    private final net.minecraft.world.entity.ItemBasedSteering steering = new net.minecraft.world.entity.ItemBasedSteering(entityData, BOOST_TIME, SADDLED);

    public MinionEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        // the follow range also sizes its path finding: as far as the trough search reaches by default, so a trough
        // round a wall or two is still found
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 1.0).add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.0)
                .add(Attributes.FLYING_SPEED, 0.4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BUILD, Optional.empty());
        builder.define(TASK, MinionTask.IDLE.id.toString());
        builder.define(ANCHOR, (byte) 0);
        builder.define(REACH, 0);
        builder.define(TASK_FITNESS, 1.0F);
        builder.define(STUMP, (byte) 0);
        builder.define(DOWN, false);
        builder.define(POWER, 0.0F);
        builder.define(SADDLED, false);
        builder.define(MODULE, "");
        builder.define(CLIMBING, false);
        builder.define(BOOST_TIME, 0);
        builder.define(LAVA_WALKER, false);
    }

    /** Just woken: its maker, where it was made, what it is built of, and the blood it was woken with. */
    public void setup(@Nullable Player maker, BlockPos home, MinionBuild build, float power) {
        this.maker = maker == null ? null : maker.getUUID();
        this.makerEntity = maker;
        this.home = home.immutable();
        setBuild(build);
        setHealth(getMaxHealth());
        entityData.set(POWER, Math.min(power, stats().reservoir()));
        // its fittest task that waits on nothing it lacks, at home (docs/NEXT.md 1.3), named on its maker's action bar
        MinionTask woke = MinionTasks.wakeTask(this);
        entityData.set(TASK, woke.id.toString());
        // at home, unless a datapack has its task done only with its maker
        entityData.set(ANCHOR, (byte) PartsData.of(level()).task(woke).anchorFor(MinionTask.Anchor.HOME).ordinal());
        entityData.set(REACH, 0);
        refreshFitness();
        if (maker != null) {
            maker.displayClientMessage(MinionTasks.woke(this), true);
        }
    }

    public Optional<MinionBuild> build() {
        return entityData.get(BUILD);
    }

    public void setBuild(MinionBuild build) {
        entityData.set(BUILD, Optional.of(build));
        stats = null;
        applyStats();
        if (!level().isClientSide) {
            // its parts' traits, and their attribute changes, go on with it; then how it gets about, which they may change
            // (lava walking)
            com.avicagan.bloodandbones.parts.ActiveTraits.rebuild(this);
            moveBy(stats());
            refreshFitness();
        }
    }

    /** What its build adds up to, worked out again when data changes. */
    public MinionStats stats() {
        PartsData.Store store = PartsData.of(level());
        if (stats == null || statsGeneration != store.generation()) {
            MinionBuild build = build().orElse(null);
            stats = build == null ? MinionStats.NOTHING : MinionStats.of(store, build);
            fitnessBody = null;
            // the saddle's place, turned into the frame a passenger's place is given in (the renderer turns it a half turn more)
            MinionBody.Layout layout = build == null ? null : MinionBody.layout(store, build);
            org.joml.Vector3f saddle = layout == null ? new org.joml.Vector3f(0.0F, stats.height(), 0.0F) : MinionBody.saddlePoint(layout);
            seat = new net.minecraft.world.phys.Vec3(-saddle.x, saddle.y, -saddle.z);
            seats = layout == null ? List.of(seat) : MinionBody.seats(layout, stats.mount().seats()).stream()
                    .map(p -> new net.minecraft.world.phys.Vec3(-p.x, p.y, -p.z)).toList();
            statsGeneration = store.generation();
            refreshDimensions();
        }
        return stats;
    }

    /** Put its stats on its attributes. */
    private void applyStats() {
        MinionStats s = stats();
        base(Attributes.MAX_HEALTH, s.health());
        base(Attributes.MOVEMENT_SPEED, s.speed());
        base(Attributes.KNOCKBACK_RESISTANCE, s.knockbackResistance());
        base(Attributes.FLYING_SPEED, Math.max(0.1, s.speed() * 2.0));
        // a sinker's legs walk the bottom of water as they walk land
        base(Attributes.WATER_MOVEMENT_EFFICIENCY, s.sinks() ? 1.0 : 0.0);
        float arm = (float) s.strikes().stream().mapToDouble(MinionStats.Strike::damage).max().orElse(0.0);
        base(Attributes.ATTACK_DAMAGE, Math.max(arm, s.biteDamage()));
        if (getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
        if (!level().isClientSide) {
            moveBy(s);
            if (isSaddled() && !s.rideable()) {
                // its horse legs came off: the saddle comes off with them
                entityData.set(SADDLED, false);
                ejectPassengers();
                spawnAtLocation(Items.SADDLE);
            }
        }
        refreshDimensions();
    }

    /**
     * Set its navigation to the way it gets about (docs/PARTS-AND-TRAITS.md section 5.4, movement): climbing legs
     * path up walls as a spider does, a flying torso (or wings strong enough, or floating legs) flies, a swimmer takes
     * to water, a lava walker paths over lava as a strider does; the rest walk (a sinker along the bottom of water).
     * Changed only when the build or its traits do.
     */
    private void moveBy(MinionStats s) {
        boolean lava = MinionMoves.lavaWalker(this);
        String kind = s.flies() ? "fly" : s.climbs() ? "climb" : "swim".equals(s.mode()) || "amphibious".equals(s.mode()) ? "swim" : lava ? "lava" : "ground";
        boolean floats = "fly".equals(kind) && !poweredDown();
        if (isNoGravity() != floats) {
            setNoGravity(floats);
        }
        MinionMoves.lavaMalus(this, lava);
        if (entityData.get(LAVA_WALKER) != lava) {
            entityData.set(LAVA_WALKER, lava);
        }
        if (kind.equals(movedBy)) {
            return;
        }
        movedBy = kind;
        navigation.stop();
        switch (kind) {
            case "fly" -> {
                var flying = new net.minecraft.world.entity.ai.navigation.FlyingPathNavigation(this, level());
                flying.setCanOpenDoors(false);
                flying.setCanFloat(true);
                navigation = flying;
                moveControl = new net.minecraft.world.entity.ai.control.FlyingMoveControl(this, 20, true);
            }
            case "climb" -> {
                navigation = new net.minecraft.world.entity.ai.navigation.WallClimberNavigation(this, level()) {
                    @Override
                    protected net.minecraft.world.level.pathfinder.Path createPath(java.util.Set<BlockPos> targets, int regionOffset, boolean offsetUpward,
                                                                                    int accuracy, float followRange) {
                        return pathing(() -> super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange));
                    }
                };
                moveControl = new net.minecraft.world.entity.ai.control.MoveControl(this);
            }
            case "swim" -> {
                navigation = new net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation(this, level()) {
                    @Override
                    protected net.minecraft.world.level.pathfinder.Path createPath(java.util.Set<BlockPos> targets, int regionOffset, boolean offsetUpward,
                                                                                    int accuracy, float followRange) {
                        return pathing(() -> super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange));
                    }
                };
                moveControl = new net.minecraft.world.entity.ai.control.MoveControl(this);
            }
            case "lava" -> {
                navigation = new MinionMoves.LavaNavigation(this, level());
                moveControl = new net.minecraft.world.entity.ai.control.MoveControl(this);
            }
            default -> {
                navigation = createNavigation(level());
                moveControl = new net.minecraft.world.entity.ai.control.MoveControl(this);
            }
        }
    }

    /** It walks, until its build says otherwise; see {@link #pathing}. */
    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(net.minecraft.world.level.Level level) {
        return new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(this, level) {
            @Override
            protected net.minecraft.world.level.pathfinder.Path createPath(java.util.Set<BlockPos> targets, int regionOffset, boolean offsetUpward,
                                                                            int accuracy, float followRange) {
                return pathing(() -> super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange));
            }
        };
    }

    /** Whether it is working out a path just now (see {@link #pathing}). */
    public boolean findingPath() {
        return findingPath;
    }

    /**
     * Work out a path. While it does, walking on lava is asked only of the lava itself: a path's start climbs up through
     * whatever it can stand on, and the empty fluid over lava that counts for Sable's collisions would have it climb
     * through the air forever.
     */
    <T> T pathing(java.util.function.Supplier<T> path) {
        boolean was = findingPath;
        findingPath = true;
        try {
            return path.get();
        } finally {
            findingPath = was;
        }
    }

    /** Climbing legs cling to the wall it is up against, as a spider's do. */
    @Override
    public boolean onClimbable() {
        return entityData.get(CLIMBING) || com.avicagan.bloodandbones.parts.effect.MotionEffects.climbing(this) || super.onClimbable();
    }

    /** A trait may let it walk on a fluid (lava_walk). */
    @Override
    public boolean canStandOnFluid(net.minecraft.world.level.material.FluidState fluid) {
        return com.avicagan.bloodandbones.parts.effect.MotionEffects.standsOn(this, fluid) || super.canStandOnFluid(fluid);
    }

    /** A trait may quiet its steps (silent_steps): sculk does not hear it. */
    @Override
    public boolean dampensVibrations() {
        return com.avicagan.bloodandbones.parts.effect.MotionEffects.silent(this) || super.dampensVibrations();
    }

    /** A flier does not take fall damage from its own landings; a climber none from the walls it climbs. */
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        com.avicagan.bloodandbones.cyber.Module module = module();
        if (module == com.avicagan.bloodandbones.cyber.Module.GYROSCOPIC_STABILIZER || module == com.avicagan.bloodandbones.cyber.Module.BAROMETRIC_VENT) {
            return false;
        }
        // wings too weak to fly it still slow its fall, as a chicken's do, and it lands unhurt
        return !stats().flies() && !stats().slowFalls() && super.causeFallDamage(distance, multiplier, source);
    }

    /** A lava walker is neither set alight nor burnt by the lava it walks on, as a strider is not. */
    @Override
    public void lavaHurt() {
        if (!entityData.get(LAVA_WALKER)) {
            super.lavaHurt();
        }
    }

    /**
     * Whether it walks the bottom of water now: it has sink legs (a drowned's, an iron golem's) and breath enough. Sink
     * legs do not breathe water for the body on them: one that cannot (a cow's torso) goes up for air when it runs short,
     * paddling as any minion does, and back down once it has its fill; one with gills (a drowned's lungs, the undead
     * horses' legs) or of brass never runs short.
     */
    public boolean sinking() {
        return stats().sinks() && !surfacing;
    }

    @Override
    public void baseTick() {
        super.baseTick();
        // (both sides: a rider's own client moves the steed it rides, and its air is synced)
        if (getAirSupply() < getMaxAirSupply() / 3) {
            surfacing = true;
        } else if (getAirSupply() >= getMaxAirSupply()) {
            surfacing = false;
        }
    }

    /** A sinker drops through water to walk its bottom (a drowned's legs, an iron golem's), never paddling up while it has breath. */
    @Override
    public void travel(net.minecraft.world.phys.Vec3 input) {
        if (!poweredDown() && sinking() && isInWater() && !onGround() && !isNoGravity()) {
            setDeltaMovement(getDeltaMovement().add(0.0, -MinionMoves.SINK, 0.0));
        }
        super.travel(input);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // wings too weak to fly it: it drifts down as a chicken does
        net.minecraft.world.phys.Vec3 motion = getDeltaMovement();
        if (stats().slowFalls() && !onGround() && motion.y < 0.0) {
            setDeltaMovement(motion.multiply(1.0, 0.6, 1.0));
        }
    }

    /** Only a sinker's rider stays on under water (a seafloor steed); the rest throw theirs off, as a horse does. */
    @Override
    public boolean canBeRiddenUnderFluidType(net.neoforged.neoforge.fluids.FluidType type, net.minecraft.world.entity.Entity rider) {
        if (type == net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value()) {
            return stats().sinks();
        }
        return super.canBeRiddenUnderFluidType(type, rider);
    }

    // ---- riding (horse legs, a saddle, a torso heavy enough)

    @Override
    public boolean isSaddleable() {
        return isAlive() && !poweredDown() && stats().rideable();
    }

    @Override
    public void equipSaddle(ItemStack stack, @Nullable SoundSource source) {
        entityData.set(SADDLED, true);
        if (source != null) {
            level().playSound(null, this, SoundEvents.HORSE_SADDLE, source, 0.5F, 1.0F);
        }
    }

    @Override
    public boolean isSaddled() {
        return entityData.get(SADDLED);
    }

    /**
     * Saddled, its front rider steers it: with nothing but the saddle, or holding what its head or legs are steered with
     * (a pig's head a carrot on a stick, strider legs a warped fungus on a stick), as a pig or a strider is.
     */
    @Nullable
    @Override
    public net.minecraft.world.entity.LivingEntity getControllingPassenger() {
        return isSaddled() && !poweredDown() && getFirstPassenger() instanceof Player player && MinionMoves.steers(this, player) ? player
                : super.getControllingPassenger();
    }

    /**
     * The seats behind are its maker's to share: when the maker gets off, whoever rode behind gets off too, so nobody else
     * is left in front to steer it away, and its maker can climb back on.
     */
    @Override
    protected void removePassenger(net.minecraft.world.entity.Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && passenger instanceof Player player && isMaker(player) && isVehicle()) {
            ejectPassengers();
        }
    }

    @Override
    protected void tickRidden(Player player, net.minecraft.world.phys.Vec3 travel) {
        super.tickRidden(player, travel);
        setRot(player.getYRot(), player.getXRot() * 0.5F);
        yRotO = yBodyRot = yHeadRot = getYRot();
        steering.tickBoost();
    }

    /**
     * As a horse: forward and back (back slowly), and half-speed sideways. Steered with an item on a stick, it goes on
     * where its rider looks, as a pig or strider does.
     */
    @Override
    protected net.minecraft.world.phys.Vec3 getRiddenInput(Player player, net.minecraft.world.phys.Vec3 travel) {
        if (stats().mount().steer().isPresent()) {
            return new net.minecraft.world.phys.Vec3(0.0, 0.0, 1.0);
        }
        float forward = player.zza <= 0.0F ? player.zza * 0.25F : player.zza;
        return new net.minecraft.world.phys.Vec3(player.xxa * 0.5F, 0.0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return riddenSpeed();
    }

    /** How fast its rider takes it: its legs' speed, spurred on by a stick's boost for a while after each use. */
    public float riddenSpeed() {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED) * (stats().mount().steer().isPresent() ? steering.boostFactor() : 1.0F);
    }

    /** A spur from its rider's stick (vanilla's FoodOnAStickItem asks this of pigs and striders; MinionMoves of it). */
    @Override
    public boolean boost() {
        return stats().mount().steer().isPresent() && steering.boost(getRandom());
    }

    /** As many riders as its torso has seats (a camel's or a ravager's two). */
    @Override
    protected boolean canAddPassenger(net.minecraft.world.entity.Entity passenger) {
        return getPassengers().size() < stats().mount().seats();
    }

    /**
     * Its riders sit on its back where the saddle is, not on top of its whole hitbox (a raised head and all): one in the
     * middle, or two along its back, the first in front.
     */
    @Override
    protected net.minecraft.world.phys.Vec3 getPassengerAttachmentPoint(net.minecraft.world.entity.Entity passenger, EntityDimensions dimensions, float scale) {
        stats();
        int seated = getPassengers().size();
        net.minecraft.world.phys.Vec3 at = seat;
        if (seated > 1 && seats.size() > 1) {
            at = seats.get(Math.max(0, Math.min(seats.size() - 1, getPassengers().indexOf(passenger))));
        }
        return at.yRot(-getYRot() * net.minecraft.util.Mth.DEG_TO_RAD);
    }

    /**
     * A rider is taking it somewhere: the server holds a ridden mob's own motion at nothing and moves it from the rider's
     * packets, so it asks the rider. One steered with an item on a stick (a pig's head, strider legs) goes on by itself
     * while its rider holds the stick, pressing nothing, as a pig does (getRiddenInput); a saddle's alone only while its
     * rider presses on.
     */
    public boolean steered() {
        return getControllingPassenger() instanceof Player rider && (stats().mount().steer().isPresent() || rider.zza != 0.0F || rider.xxa != 0.0F);
    }

    // ---- strikes (its arms take turns; with none, it bites)

    /**
     * One blow at its target, in the style of the arm whose turn it is (docs/PARTS-AND-TRAITS.md section 5.6): a
     * punch, a kick that throws them back, a fling that throws them up, a grab that holds them, a sting that
     * poisons, a slam that hits everything round them, a claw or hook that tears. With no arm that hurts (folded arms,
     * wings), the head bites, as the task screen's Blow reads it, and wings buffet them as it does. With no head either, the
     * wings only buffet: such a body has nothing to fight with ({@link MinionStats#fights}), and its fight goals never ask.
     */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        MinionStats s = stats();
        List<MinionStats.Strike> strikes = s.strikes().stream().filter(MinionStats.Strike::hurts).toList();
        swing(InteractionHand.MAIN_HAND);
        boolean buffets = strikes.isEmpty() && s.flaps();
        if (strikes.isEmpty() && s.mindless()) {
            knock(target, buffets ? FLAP_KNOCKBACK : 0.0F);
            return buffets;
        }
        MinionStats.Strike strike = strikes.isEmpty() ? new MinionStats.Strike("bite", s.biteDamage()) : strikes.get(Math.floorMod(nextStrike++, strikes.size()));
        DamageSource source = damageSources().mobAttack(this);
        // a berserk head's blows land half again as hard
        float damage = strike.damage() * (s.berserk() ? MinionStats.BERSERK_DAMAGE : 1.0F);
        if (!target.hurt(source, Math.max(0.5F, damage))) {
            return false;
        }
        setLastHurtMob(target);
        net.minecraft.world.entity.LivingEntity living = target instanceof net.minecraft.world.entity.LivingEntity l ? l : null;
        switch (strike.style()) {
            case "kick" -> knock(target, 1.4F);
            case "ram" -> knock(target, 1.1F);
            case "fling" -> {
                target.push(0.0, 0.9, 0.0);
                target.hurtMarked = true;
            }
            case "grab" -> {
                if (living != null) {
                    living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
                }
            }
            case "sting" -> {
                if (living != null) {
                    living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100, 0), this);
                }
            }
            case "slam" -> {
                for (net.minecraft.world.entity.LivingEntity near : level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        target.getBoundingBox().inflate(2.0), e -> e != this && e != target && e != maker() && !(e instanceof MinionEntity))) {
                    near.hurt(source, strike.damage() * 0.5F);
                }
            }
            case "claw", "hook" -> com.avicagan.bloodandbones.carcass.Blood.burst((ServerLevel) level(),
                    new org.joml.Vector3d(target.getX(), target.getY(0.6), target.getZ()), 6, false);
            case "pounce" -> {
                net.minecraft.world.phys.Vec3 at = target.position().subtract(position()).normalize();
                setDeltaMovement(at.x * 0.4, 0.3, at.z * 0.4);
            }
            default -> knock(target, s.biteKnockback());
        }
        if (buffets) {
            knock(target, FLAP_KNOCKBACK);
        }
        if (module() == com.avicagan.bloodandbones.cyber.Module.PISTON_RAM) {
            knock(target, MinionModules.RAM_KNOCKBACK);
        }
        return true;
    }

    /** How hard its wings' flap throws back what it bites. */
    private static final float FLAP_KNOCKBACK = 0.8F;

    /** A ranged shot from its arms or organ, when vanilla's ranged goal fires (the Ranged group's trait effects). */
    @Override
    public void performRangedAttack(net.minecraft.world.entity.LivingEntity target, float velocity) {
        com.avicagan.bloodandbones.parts.effect.RangedEffects.rangedAttack(this, target, velocity);
    }

    private void knock(net.minecraft.world.entity.Entity target, float strength) {
        if (strength > 0.0F && target instanceof net.minecraft.world.entity.LivingEntity living) {
            living.knockback(strength, Math.sin(getYRot() * Math.PI / 180.0), -Math.cos(getYRot() * Math.PI / 180.0));
            target.hurtMarked = true;
        }
    }

    private void base(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null && instance.getBaseValue() != value) {
            instance.setBaseValue(value);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (BUILD.equals(key) || DOWN.equals(key)) {
            stats = null;
            refreshDimensions();
        }
        if (BOOST_TIME.equals(key) && level().isClientSide) {
            steering.onSynced();
        }
    }

    /** Its size is its build's, lying on its side while powered down. */
    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        // asked once while the entity is still being made, before it has any data
        if (entityData == null || level() == null) {
            return super.getDefaultDimensions(pose);
        }
        MinionStats s = build().isPresent() ? stats() : null;
        if (s == null) {
            return super.getDefaultDimensions(pose);
        }
        return poweredDown() ? EntityDimensions.scalable(s.lyingWidth(), s.lyingHeight()) : EntityDimensions.scalable(s.width(), s.height());
    }

    /** Its task (docs/NEXT.md 1.1); Idle for one no longer known. */
    public MinionTask task() {
        MinionTask task = MinionTask.byId(ResourceLocation.tryParse(entityData.get(TASK)));
        return task == null ? MinionTask.IDLE : task;
    }

    public boolean hasTask(MinionTask task) {
        return task() == task;
    }

    /** Where its maker set its task to be centred: at home, or with them. */
    public MinionTask.Anchor anchor() {
        return entityData.get(ANCHOR) == 1 ? MinionTask.Anchor.MAKER : MinionTask.Anchor.HOME;
    }

    /** The reach its maker set, 0 for its task's own. */
    public int reachSet() {
        return entityData.get(REACH);
    }

    /** How far from where its task is centred it works: what its maker set, else its task's own reach (docs/NEXT.md 1.1). */
    public int reach() {
        MinionTask.Data data = PartsData.of(level()).task(task());
        int set = reachSet();
        return set <= 0 ? data.reach() : Math.max(MinionTasks.LEAST_REACH, Math.min(data.maxReach(), set));
    }

    /**
     * At home (or, where a datapack has the task done only with its maker, with them), with its task's own reach: see
     * {@link #setTask(MinionTask, MinionTask.Anchor, int)}.
     */
    public boolean setTask(MinionTask task) {
        return setTask(task, PartsData.of(level()).task(task).anchorFor(MinionTask.Anchor.HOME), 0);
    }

    /**
     * Its maker's task for it (docs/NEXT.md 1.3): centred at home or on its maker, where the task allows it, reaching as far
     * as {@code reach} (0 for the task's own; else from {@link MinionTasks#LEAST_REACH} to the task's most). False, and
     * nothing changed, if its body cannot do the task, the task is not done there, or the reach is out of bounds. A new task
     * (or a new anchor) is a fresh start; a sentry takes its post where it stands.
     */
    public boolean setTask(MinionTask task, MinionTask.Anchor anchor, int reach) {
        MinionTask.Data data = PartsData.of(level()).task(task);
        if (!data.allows(anchor) || reach != 0 && (reach < MinionTasks.LEAST_REACH || reach > data.maxReach()) || !row(task, anchor).can()) {
            return false;
        }
        boolean fresh = task != task() || anchor != anchor();
        boolean post = task == MinionTask.SENTRY && task != task();
        entityData.set(TASK, task.id.toString());
        entityData.set(ANCHOR, (byte) anchor.ordinal());
        entityData.set(REACH, reach);
        lostTask = null;
        lostReason = null;
        idle = null;
        if (fresh) {
            setTarget(null);
            getNavigation().stop();
        }
        if (post) {
            setHome(blockPosition());
        }
        refreshFitness();
        return true;
    }

    /** Its task taken from it (a data reload left its body unable to do it): Idle at home, remembering why for its status line. */
    void loseTask(MinionTask task, String reason) {
        entityData.set(TASK, MinionTask.IDLE.id.toString());
        entityData.set(ANCHOR, (byte) PartsData.of(level()).task(MinionTask.IDLE).anchorFor(MinionTask.Anchor.HOME).ordinal());
        entityData.set(REACH, 0);
        lostTask = task;
        lostReason = reason;
        setTarget(null);
        getNavigation().stop();
        refreshFitness();
    }

    /** The task a data reload took from it, if it has not been given another since. */
    @Nullable
    public MinionTask lostTask() {
        return lostTask;
    }

    @Nullable
    String lostReason() {
        return lostReason;
    }

    /**
     * Its maker, while it works with them: its task is done with its maker and they are in the same world within
     * {@link #WITH_ME} blocks (docs/NEXT.md 1.1). Null otherwise, and it works at home.
     */
    @Nullable
    public Player workingMaker() {
        if (anchor() != MinionTask.Anchor.MAKER || !PartsData.of(level()).task(task()).allows(MinionTask.Anchor.MAKER)) {
            return null;
        }
        Player maker = maker();
        return maker != null && maker.isAlive() && !maker.isSpectator() && maker.level() == level() && maker.distanceToSqr(this) <= WITH_ME * WITH_ME
                ? maker : null;
    }

    public boolean withMaker() {
        return workingMaker() != null;
    }

    /** Where its task is centred now: at its maker's feet while it works with them, else at home. */
    public net.minecraft.world.phys.Vec3 centre() {
        Player maker = workingMaker();
        return maker != null ? maker.position() : net.minecraft.world.phys.Vec3.atBottomCenterOf(home);
    }

    // ---- how well it does each task (docs/NEXT.md 1.2), worked out on the server

    /** What its build brings to every task, worked out again with its stats; null before it has a build. */
    @Nullable
    public MinionFitness.Body fitnessBody() {
        MinionStats s = stats();
        if (fitnessBody == null && build().isPresent()) {
            fitnessBody = MinionFitness.body(PartsData.of(level()), build().get(), s);
        }
        return fitnessBody;
    }

    /**
     * What it holds and carries, whether it is night, the mobGriefing rule and a fitted chest, for its fitness now at this
     * anchor (the time of day and what it holds are read each time; docs/NEXT.md 1.4).
     */
    public MinionFitness.Context fitnessContext(MinionTask.Anchor at) {
        List<ItemStack> carried = new java.util.ArrayList<>();
        for (int i = 0; i < slots(); i++) {
            if (!inventory.getItem(i).isEmpty()) {
                carried.add(inventory.getItem(i));
            }
        }
        boolean griefing = level().isClientSide || net.neoforged.neoforge.event.EventHooks.canEntityGrief(level(), this);
        return new MinionFitness.Context(getMainHandItem(), carried, level().isNight(), at, griefing,
                com.avicagan.bloodandbones.parts.effect.StorageEffect.hasChest(this));
    }

    /** Its row for a task done at this anchor (where the task is done instead, if not there: {@link MinionTask.Data#anchorFor}). */
    public MinionFitness.Row row(MinionTask task, MinionTask.Anchor at) {
        MinionFitness.Body body = fitnessBody();
        MinionTask.Anchor where = PartsData.of(level()).task(task).anchorFor(at);
        if (body == null) {
            // no build (one saved before minions were built of parts): it can do nothing but stand
            return new MinionFitness.Row(task, 1.0F, 1.0F, task.rated() ? Optional.ofNullable(task.cannotKey()) : Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), 1.0F, List.of(), 1.0F, MinionDisposition.MINDLESS);
        }
        return MinionFitness.row(PartsData.of(level()), body, task, fitnessContext(where));
    }

    /** How well it does a task now: its own at the anchor it works at, any other at home (docs/NEXT.md 1.2). */
    public float fitness(MinionTask task) {
        return row(task, task == task() && withMaker() ? MinionTask.Anchor.MAKER : MinionTask.Anchor.HOME).fitness();
    }

    /**
     * Its fitness at its task now, as its task's goals read it for their levers (docs/NEXT.md 1.2): worked out once a second
     * and when its task is set, not on every tick a goal asks.
     */
    public float taskFitness() {
        return workFitness;
    }

    /** Its fitness at its task as its clients know it, from the server once a second. */
    public float shownFitness() {
        return entityData.get(TASK_FITNESS);
    }

    /**
     * What a stump it cuts at a Surgery Table costs, in buckets of blood (docs/NEXT.md 1.5), as its clients know it: 0 when it
     * does not cut (not a surgeon, or the surgeon file's switch passes its head over).
     */
    public int shownStump() {
        return entityData.get(STUMP);
    }

    /** Its fitness at its task worked out again, for its goals' levers and for its clients. */
    private void refreshFitness() {
        if (level().isClientSide) {
            return;
        }
        workFitness = fitness(task());
        entityData.set(TASK_FITNESS, workFitness);
        MinionFitness.Body body = fitnessBody();
        MinionTask.Data surgeon = PartsData.of(level()).task(MinionTask.SURGEON);
        entityData.set(STUMP, (byte) (hasTask(MinionTask.SURGEON) && body != null && MinionFitness.mayCut(surgeon, body)
                ? MinionFitness.stumpBuckets(surgeon, workFitness) : 0));
    }

    /**
     * A hauler towing a carcass (docs/NEXT.md 1.2): a player's slowdown at 100% and over, never less; below, a player's ÷ its
     * fitness at hauling, at most 90%. Its drag strength counts already, in its pull.
     */
    @Override
    public float dragSlowdown(float playerSlowdown) {
        return MinionFitness.towing(PartsData.of(level()).task(MinionTask.HAULER), playerSlowdown, fitness(MinionTask.HAULER));
    }

    /** At work, as a task's goal sets it while it does something (or a test standing in for one): the blood it uses follows its fitness. */
    public void setWorking(boolean working) {
        this.working = working;
    }

    /** Why its task's work stands still (nothing to do there), said by its goal for its status line, for the next few seconds. */
    public void idle(Component why) {
        idle = why;
        idleUntil = tickCount + 100;
    }

    /** What its task's work waits on in the world just now, if anything. */
    @Nullable
    public Component idleReason() {
        return idle != null && tickCount < idleUntil ? idle : null;
    }

    public BlockPos home() {
        return home;
    }

    /** Where it works from now (a surgeon moved to another table). */
    public void setHome(BlockPos home) {
        this.home = home.immutable();
    }

    @Nullable
    public UUID makerId() {
        return maker;
    }

    @Nullable
    public Player maker() {
        if (maker == null) {
            return null;
        }
        Player found = level().getPlayerByUUID(maker);
        if (makerEntity != null && makerEntity.isRemoved()) {
            // logged out or respawned as another: only the id is kept from now on
            makerEntity = null;
        }
        if (found == null && makerEntity != null && makerEntity.level() == level()) {
            found = makerEntity;
        }
        return found;
    }

    /**
     * Whether it has a ranged attack: a bow, crossbow or trident in a hand that fights (docs/PARTS-AND-TRAITS.md
     * section 6.4). Innate shots from its traits join this when they come.
     */
    public boolean hasRangedAttack() {
        return MinionTasks.heldWeapon(this) != null;
    }

    /** Arrows (or rockets) for the bow or crossbow in its hand, from what it carries: the stack itself, so a shot uses one up. */
    @Override
    public ItemStack getProjectile(ItemStack weapon) {
        return MinionTasks.ammo(this, weapon);
    }

    public boolean isMaker(Player player) {
        return player.getUUID().equals(maker);
    }

    /** It never turns on its maker, nor on its maker's other minions (a stray sweep of the maker's sword included). */
    @Override
    public boolean canAttack(net.minecraft.world.entity.LivingEntity target) {
        if (maker != null && (maker.equals(target.getUUID()) || target instanceof MinionEntity other && maker.equals(other.makerId()))) {
            return false;
        }
        return super.canAttack(target);
    }

    /**
     * The mobs whose hide it keeps, and their hide traits with them (docs/PARTS-AND-TRAITS.md section 6.6, flesh only):
     * each different mob among its pieces fitted with the hide on, torso first, up to three.
     */
    public List<ResourceLocation> hides() {
        return hidePieces().stream().map(PieceRef::entity).toList();
    }

    /**
     * The first piece of each of those mobs: whose hide it is, and what that mob's carcass kept (a snow fox's white coat
     * is warmer), for its data's variants.
     */
    public List<PieceRef> hidePieces() {
        return build().map(MinionData::hidePieces).orElse(List.of());
    }

    public boolean cybernetic() {
        return build().map(MinionBuild::cybernetic).orElse(false);
    }

    /** Its filter slot (brass only; flesh's stays empty and lets everything through). */
    public MinionFilter filter() {
        return filter;
    }

    /** How many of its inventory's slots it can use: its torso's, and what its traits add (storage). */
    public int slots() {
        return Math.max(0, Math.min(inventory.getContainerSize(),
                stats().slots() + com.avicagan.bloodandbones.parts.effect.UpkeepEffects.extraSlots(this)));
    }

    /** Whether it has room for this in the slots its torso gives it. */
    public boolean canCarry(ItemStack stack) {
        int slots = slots();
        for (int i = 0; i < slots; i++) {
            ItemStack in = inventory.getItem(i);
            if (in.isEmpty() || ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /** Put this in its slots; what did not fit. */
    public ItemStack carry(ItemStack stack) {
        int slots = slots();
        for (int i = 0; i < slots && !stack.isEmpty(); i++) {
            ItemStack in = inventory.getItem(i);
            if (in.isEmpty()) {
                inventory.setItem(i, stack.copy());
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameComponents(in, stack)) {
                int moved = Math.min(stack.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(moved);
                stack.shrink(moved);
            }
        }
        return stack;
    }

    // ---- power

    public float power() {
        return entityData.get(POWER);
    }

    public float powerShare() {
        return power() / Math.max(1.0F, stats().reservoir());
    }

    public boolean poweredDown() {
        return entityData.get(DOWN);
    }

    /** Pour blood in (from a bucket, a trough); wakes it if it was down. How much went in. */
    public float feed(float amount) {
        float room = stats().reservoir() - power();
        float in = Math.max(0.0F, Math.min(room, amount));
        entityData.set(POWER, power() + in);
        if (in > 0.0F && poweredDown()) {
            entityData.set(DOWN, false);
            refreshDimensions();
            level().playSound(null, blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.NEUTRAL, 0.5F, 1.4F);
        }
        return in;
    }

    /**
     * Pay for an organ or an innate shot out of its own blood (brass: its canister's soul blood). False, and nothing
     * taken, if it has too little; it never pays with its last drop, so an ability never powers it down.
     */
    public boolean usePower(float mb) {
        if (mb <= 0.0F) {
            return true;
        }
        if (poweredDown() || power() - mb < 1.0F) {
            return false;
        }
        entityData.set(POWER, power() - mb);
        return true;
    }

    /** Out of blood: it stops where it is and lies down (a flier drops), throwing off anyone riding it. */
    public void powerDown() {
        entityData.set(POWER, 0.0F);
        if (!poweredDown()) {
            entityData.set(DOWN, true);
            ejectPassengers();
            setNoGravity(false);
            if (!level().isClientSide) {
                com.avicagan.bloodandbones.cyber.Coupler.release(this);
                // a hauler lets go of what it drags
                com.avicagan.bloodandbones.carcass.CarcassDrag.stop((ServerLevel) level(), this);
            }
            getNavigation().stop();
            setTarget(null);
            refreshDimensions();
            level().playSound(null, blockPosition(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.NEUTRAL, 1.0F, 0.6F);
        }
    }

    /** Blood used this tick: a little just to be awake, more moving (or ridden), working (driving a shaft too), fighting. */
    private void drain() {
        float rate = IDLE;
        if (getTarget() != null) {
            rate = FIGHTING;
        } else if (working || com.avicagan.bloodandbones.cyber.Coupler.coupled(this)) {
            // 25 mB a minute at 100%, less for a fitter minion and more for a poorer one (docs/NEXT.md 1.2)
            rate = working ? MinionFitness.workingDrain(workFitness) : WORKING;
        } else if (getDeltaMovement().horizontalDistanceSqr() > 1.0E-4 || !getNavigation().isDone() || steered()) {
            // keeping itself up in the air costs twice as much
            rate = stats().flies() ? MOVING * 2.0F : MOVING;
        }
        if (cybernetic()) {
            rate *= BRASS_DRAIN;
        }
        rate *= com.avicagan.bloodandbones.parts.effect.UpkeepEffects.drainMultiplier(this);
        owed += rate * BBServerConfig.powerDrain() / 1200.0F;
        if (owed >= 0.05F) {
            float left = power() - owed;
            owed = 0.0F;
            if (left <= 0.0F) {
                powerDown();
            } else {
                entityData.set(POWER, left);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (legacy) {
            // built the old way (a humanoid with implants): it falls apart, dropping what it carried, what it wore (a
            // Fluid Backtank, fluid and all) and the implants that were in it, as its death did then
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                spawnAtLocation(inventory.removeItemNoUpdate(i));
            }
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                spawnAtLocation(getItemBySlot(slot));
                setItemSlot(slot, ItemStack.EMPTY);
            }
            com.avicagan.bloodandbones.body.Body body = com.avicagan.bloodandbones.body.BodyEffects.body(this);
            for (com.avicagan.bloodandbones.body.BodyPart part : com.avicagan.bloodandbones.body.BodyPart.values()) {
                if (body.state(part) == com.avicagan.bloodandbones.body.Body.State.IMPLANT) {
                    spawnAtLocation(body.unclip(part));
                }
            }
            discard();
            return;
        }
        if (tickCount % 20 == 0 || movedBy.isEmpty()) {
            // its traits, built again if its build or the data changed (before its stats, whose way of getting about
            // they may change: lava walking)
            com.avicagan.bloodandbones.parts.ActiveTraits.of(this);
            applyStats();
            // a task a data reload took from its body gives way to Idle at home (a missing tool never does: it waits)
            MinionTasks.keepPossible(this);
            refreshFitness();
        }
        if ((tickCount + getId()) % 10 == 0) {
            // its traits' tick, staggered as players' are (tick effects wait while it is down)
            com.avicagan.bloodandbones.parts.TraitEvents.tick(this);
        }
        entityData.set(CLIMBING, stats().climbs() && horizontalCollision && !poweredDown());
        if (poweredDown()) {
            // down: a trough right beside it tops it up (so a guard fallen by its trough gets up again)
            if (tickCount % 20 == 0) {
                MinionGoals.drinkNearby(this);
            }
        } else {
            if (working) {
                // at work again: whatever it waited on in the world is there now
                idle = null;
            }
            drain();
            MinionModules.tick(this, module());
            // flesh mends itself on its blood; brass never does (a brass sheet, or a cradle stocked with them)
            if (!cybernetic() && tickCount % REGEN_TICKS == 0 && getHealth() < getMaxHealth() && powerShare() > 0.1F) {
                heal(1.0F);
                entityData.set(POWER, Math.max(0.0F, power() - REGEN_COST));
            }
        }
    }

    /** Its module, if it is brass and has one fitted. */
    @Nullable
    public com.avicagan.bloodandbones.cyber.Module module() {
        String name = entityData.get(MODULE);
        if (name.isEmpty() || !cybernetic()) {
            return null;
        }
        for (com.avicagan.bloodandbones.cyber.Module module : com.avicagan.bloodandbones.cyber.Module.values()) {
            if (module.getSerializedName().equals(name)) {
                return module;
            }
        }
        return null;
    }

    public void setModule(@Nullable com.avicagan.bloodandbones.cyber.Module module) {
        entityData.set(MODULE, module == null ? "" : module.getSerializedName());
    }

    /** An Analytical Lens sees its targets through walls. */
    @Override
    public boolean hasLineOfSight(net.minecraft.world.entity.Entity entity) {
        if (module() == com.avicagan.bloodandbones.cyber.Module.ANALYTICAL_LENS && distanceToSqr(entity) < MinionModules.LENS_RANGE * MinionModules.LENS_RANGE) {
            return true;
        }
        return super.hasLineOfSight(entity);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            com.avicagan.bloodandbones.cyber.Coupler.release(this);
        }
        super.remove(reason);
    }

    /** A soul canister's worth into a brass minion (a cradle or its maker's hand): wakes it if it was down. */
    public boolean charge() {
        if (!cybernetic() || stats().reservoir() - power() < MinionStats.CANISTER / 2.0F) {
            return false;
        }
        feed(MinionStats.CANISTER);
        return true;
    }

    /** Brass is not poisoned, withered or starved. */
    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
        if (cybernetic() && (effect.is(net.minecraft.world.effect.MobEffects.POISON) || effect.is(net.minecraft.world.effect.MobEffects.WITHER)
                || effect.is(net.minecraft.world.effect.MobEffects.HUNGER))) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    /** Nor does it drown. */
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return !cybernetic() && super.canDrownInFluidType(type);
    }

    /** Powered down, nothing runs: no goals, no moving, no looking round. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || poweredDown();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // a hopper's legs bound it along
        if ("hop".equals(stats().mode()) && onGround() && !getNavigation().isDone() && tickCount % 8 == 0) {
            getJumpControl().jump();
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this) {
            /** A sinker never paddles up while it has breath: it walks the bottom. */
            @Override
            public boolean canUse() {
                return !(sinking() && isInWater()) && super.canUse();
            }
        });
        goalSelector.addGoal(1, new MinionGoals.SeekBlood(this));
        goalSelector.addGoal(1, new MinionGoals.SeekCradle(this));
        goalSelector.addGoal(2, new MinionGoals.UseOrgan(this));
        goalSelector.addGoal(2, new MinionGoals.Bite(this));
        goalSelector.addGoal(2, new MinionGoals.Feel(this));
        goalSelector.addGoal(3, new MinionGoals.Farm(this));
        goalSelector.addGoal(3, new MinionGoals.AttendTable(this));
        goalSelector.addGoal(4, new MinionGoals.Deposit(this));
        goalSelector.addGoal(5, new MinionGoals.Collect(this));
        goalSelector.addGoal(6, new MinionGoals.FollowMaker(this));
        goalSelector.addGoal(7, new MinionGoals.StayNearHome(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        MinionGoals.targets(this, targetSelector);
        // each task's goals (docs/NEXT.md 1.1): sentry, courier, herder, fisher, hunter...
        MinionTasks.goals(this, goalSelector, targetSelector);
        // what each group of trait effects adds (a ranged minion's keep-away, say)
        com.avicagan.bloodandbones.parts.effect.MotionEffects.minionGoals(this, goalSelector, targetSelector);
        com.avicagan.bloodandbones.parts.effect.RangedEffects.minionGoals(this, goalSelector, targetSelector);
        com.avicagan.bloodandbones.parts.effect.SocialEffects.minionGoals(this, goalSelector, targetSelector);
        com.avicagan.bloodandbones.parts.effect.UpkeepEffects.minionGoals(this, goalSelector, targetSelector);
    }

    // ---- never destroyed by neglect

    /**
     * Powered down, only a player (or the void, /kill) can hurt it. A machine's stand-in player (a Deployer's punch)
     * never hurts it, down or up, whoever placed the machine.
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.isInvulnerableTo(source);
        }
        if (source.getEntity() instanceof net.neoforged.neoforge.common.util.FakePlayer
                || poweredDown() && !(source.getEntity() instanceof Player)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    /** Mobs leave a powered-down minion alone. */
    @Override
    public boolean canBeSeenAsEnemy() {
        return !poweredDown() && super.canBeSeenAsEnemy();
    }

    /**
     * Fallen out of the world (by default): set down on solid ground, where it was made if there is ground there, and
     * powered down. The void is a lethal blow like any other.
     */
    @Override
    protected void onBelowWorld() {
        if (level() instanceof ServerLevel level && BBServerConfig.minionDeath() == BBServerConfig.MinionDeath.COLLAPSE) {
            BlockPos ground = safeGround(level, home);
            powerDown();
            teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            resetFallDistance();
            return;
        }
        super.onBelowWorld();
    }

    /**
     * Solid ground to set a minion (or its folded form) down on, near here: the top of the world at this spot, or else
     * at the world's spawn, or else the End's platform.
     */
    public static BlockPos safeGround(ServerLevel level, BlockPos near) {
        BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near);
        if (top.getY() > level.getMinBuildHeight()) {
            return top;
        }
        top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level.getSharedSpawnPos());
        if (top.getY() > level.getMinBuildHeight()) {
            return top;
        }
        return level.dimension() == Level.END ? ServerLevel.END_SPAWN_POINT : level.getSharedSpawnPos();
    }

    /** A lethal blow collapses it (by default): 1 health, no blood, powered down. It is never destroyed that way. */
    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && BBServerConfig.minionDeath() == BBServerConfig.MinionDeath.COLLAPSE) {
            setHealth(1.0F);
            powerDown();
            return;
        }
        super.die(source);
        if (isDeadOrDying()) {
            // a lethal save (a trait) may have called the death off
            MinionCensus.forget(this);
        }
    }

    /**
     * Killed (where minions may die): what it carried and wore, and, scattering, its parts. Dropped as a horse drops
     * its chest, with its equipment, so a world without mob loot still gives them back.
     */
    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            spawnAtLocation(inventory.removeItemNoUpdate(i));
        }
        MinionTasks.dropHeld(this);
        if (isSaddled()) {
            spawnAtLocation(Items.SADDLE);
        }
        if (module() != null) {
            spawnAtLocation(BBItems.module(module()));
        }
        // a Create filter in its slot (a plain item there was only a copy)
        spawnAtLocation(filter.takeOut());
        if (BBServerConfig.minionDeath() == BBServerConfig.MinionDeath.SCATTER && build().isPresent()) {
            // it falls apart into what it was built of, half gone off
            MinionBuild build = build().get();
            spawnAtLocation(MinionAssembly.pieceItem(build.torso(), 0.5F));
            for (MinionBuild.Fitted fitted : build.parts()) {
                spawnAtLocation(MinionAssembly.pieceItem(fitted.piece(), 0.5F));
            }
            build.organ().map(com.avicagan.bloodandbones.parts.CarcassArmourFittingRecipe::organItem).filter(organ -> !organ.isEmpty())
                    .ifPresent(this::spawnAtLocation);
        }
    }

    // ---- hands on

    /**
     * A bucket of blood feeds flesh; a Soul Canister charges brass and a brass sheet mends it, from a hand or a Deployer
     * (a machine does nothing else to it). Its maker, crouching with an empty hand: opens its task screen while it is up
     * (docs/NEXT.md 1.3), and held for three seconds on it powered down, folds it into a Dormant Minion to carry; crouching
     * with an item, sets a brass minion's filter. Anyone's plain click: its status line.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        // what its traits make of an item in hand first (mending with iron, refuelling, milking)
        InteractionResult traits = com.avicagan.bloodandbones.parts.effect.UpkeepEffects.interact(this, player, hand);
        if (traits != null) {
            return traits;
        }
        ItemStack held = player.getItemInHand(hand);
        if (cybernetic() && held.is(BBItems.SOUL_CANISTER.get())) {
            // a canister by hand: in goes the soul blood, back comes the empty
            if (!level().isClientSide && charge() && !player.hasInfiniteMaterials()) {
                held.shrink(1);
                player.getInventory().placeItemBackInInventory(new ItemStack(BBItems.EMPTY_SOUL_CANISTER.get()));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (cybernetic() && held.is(com.simibubi.create.AllItems.BRASS_SHEET.get()) && getHealth() < getMaxHealth()) {
            // a brass sheet mends brass, from a hand or a Deployer's
            if (!level().isClientSide) {
                heal(SHEET_REPAIR);
                held.consume(1, player);
                level().playSound(null, blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.5F, 1.6F);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!cybernetic() && held.is(BBFluids.BLOOD.getBucket().get())) {
            // not poured away for a sip: only when half of it fits
            if (!level().isClientSide && stats().reservoir() - power() >= Math.min(HALF_BUCKET, stats().reservoir() * 0.5F)) {
                feed(1000.0F);
                if (!player.hasInfiniteMaterials()) {
                    held.shrink(1);
                    player.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET));
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            // a machine's stand-in (a Deployer's) only charges, feeds and mends it: whoever placed the machine, it never
            // takes it apart, folds it, rides it, or changes its module, filter, task or what it holds
            return InteractionResult.PASS;
        }
        if (cybernetic() && isMaker(player) && held.getItem() instanceof com.avicagan.bloodandbones.cyber.ModuleItem item && MinionModules.FITS.contains(item.module())) {
            // its maker fits a module into its socket; one already there comes back
            if (!level().isClientSide) {
                com.avicagan.bloodandbones.cyber.Module old = module();
                setModule(item.module());
                held.consume(1, player);
                if (old != null) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(BBItems.module(old)));
                }
                level().playSound(null, blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1.0F, 1.3F);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (cybernetic() && isMaker(player) && held.is(com.simibubi.create.AllItems.WRENCH.get()) && module() != null && !player.isSecondaryUseActive()) {
            if (!level().isClientSide) {
                player.getInventory().placeItemBackInInventory(new ItemStack(BBItems.module(module())));
                setModule(null);
                com.avicagan.bloodandbones.cyber.Coupler.release(this);
                level().playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.0F, 1.3F);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (isMaker(player) && poweredDown() && com.avicagan.bloodandbones.body.Surgery.isBlade(held)) {
            // its maker takes it apart, lying on an Assembly Frame table: back to a frame there
            if (!level().isClientSide) {
                MinionAssembly.takeApart((ServerLevel) level(), this, player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        // its maker, crouching, sets its filter (brass only) or takes it out with a Wrench
        InteractionResult filtered = MinionFilter.interact(this, player, hand);
        if (filtered != null) {
            return filtered;
        }
        // its maker hands it something to hold (a bow, a rod, a Cleaver, a courier's sample), or takes it back
        InteractionResult hands = MinionTasks.handInteract(this, player, hand);
        if (hands != null) {
            return hands;
        }
        if (!held.isEmpty()) {
            return super.mobInteract(player, hand);
        }
        if (isSaddled() && !poweredDown() && !player.isSecondaryUseActive()
                && (isMaker(player) ? !isVehicle() : isVehicle() && getPassengers().size() < stats().mount().seats())) {
            // saddled: its maker climbs on, and, once they are up front, anyone into a seat behind (a camel's)
            if (!level().isClientSide) {
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (isMaker(player) && player.isShiftKeyDown()) {
            if (poweredDown()) {
                long now = level().getGameTime();
                foldClicks = now - lastFoldClick <= 10 ? foldClicks + 1 : 1;
                lastFoldClick = now;
                if (foldClicks >= 12) {
                    DormantMinionItem.fold(this, player);
                }
                return InteractionResult.CONSUME;
            }
            // its task screen: every task, how well it does each and why, where it works and how far (docs/NEXT.md 1.3)
            MinionTasks.showScreen(this, player);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(MinionTasks.status(this), true);
        return InteractionResult.CONSUME;
    }

    // ---- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (maker != null) {
            tag.putUUID("Maker", maker);
        }
        tag.put("Home", NbtUtils.writeBlockPos(home));
        build().ifPresent(b -> MinionBuild.CODEC.encodeStart(NbtOps.INSTANCE, b).result().ifPresent(t -> tag.put("Build", t)));
        tag.putString("Task", task().id.toString());
        tag.putString("Anchor", anchor().key());
        tag.putInt("Reach", reachSet());
        tag.putFloat("Power", power());
        tag.putBoolean("Down", poweredDown());
        tag.putBoolean("Saddled", isSaddled());
        tag.putString("Module", entityData.get(MODULE));
        tag.put("Inventory", inventory.createTag(registryAccess()));
        filter.save(tag, registryAccess());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        maker = tag.hasUUID("Maker") ? tag.getUUID("Maker") : null;
        home = NbtUtils.readBlockPos(tag, "Home").orElse(blockPosition());
        inventory.fromTag(tag.getList("Inventory", 10), registryAccess());
        if (!tag.contains("Build")) {
            legacy = true;
            return;
        }
        MinionBuild.CODEC.parse(NbtOps.INSTANCE, tag.get("Build")).result().ifPresent(b -> entityData.set(BUILD, Optional.of(b)));
        entityData.set(POWER, tag.getFloat("Power"));
        entityData.set(DOWN, tag.getBoolean("Down"));
        entityData.set(SADDLED, tag.getBoolean("Saddled"));
        entityData.set(MODULE, tag.getString("Module"));
        filter.load(tag, registryAccess());
        stats = null;
        if (tag.contains("Task")) {
            MinionTask task = MinionTask.byId(ResourceLocation.tryParse(tag.getString("Task")));
            MinionTask.Anchor anchor = MinionTask.Anchor.byKey(tag.getString("Anchor"));
            entityData.set(TASK, (task == null ? MinionTask.IDLE : task).id.toString());
            entityData.set(ANCHOR, (byte) (anchor == null ? 0 : anchor.ordinal()));
            entityData.set(REACH, Math.max(0, tag.getInt("Reach")));
        } else {
            // saved before tasks: its old job becomes a task (docs/NEXT.md 1.8), its home kept
            MinionTasks.fromJob(this, tag.getString("Job"));
        }
        if (!level().isClientSide && tag.contains("Health", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
            // its traits' health (a Golem Core's hardy) is not saved with it: back on first, so the health it was saved
            // with is not cut down to its torso's
            com.avicagan.bloodandbones.parts.ActiveTraits.rebuild(this);
            setHealth(tag.getFloat("Health"));
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
