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
 * moves, how hard it bites, what jobs its head offers. It runs on the blood in it, a little all the time and more
 * when it moves, works or fights; low, it walks to a Blood Trough to drink; empty, it powers down where it is and
 * lies on its side, alive, until it gets blood again. Neglect never destroys it.
 */
public class MinionEntity extends PathfinderMob implements net.minecraft.world.entity.Saddleable, net.minecraft.world.entity.monster.RangedAttackMob {
    private static final EntityDataAccessor<Optional<MinionBuild>> BUILD = SynchedEntityData.defineId(MinionEntity.class, MinionSerializers.BUILD.get());
    private static final EntityDataAccessor<String> JOB = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DOWN = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.BOOLEAN);
    /** A brass minion's one module, by name ("" for none). */
    private static final EntityDataAccessor<String> MODULE = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.STRING);
    /** Up against a wall on climbing legs: synced, as the spider's is, so clients predict the climb. */
    private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.BOOLEAN);
    /** How far from home it works. */
    public static final double RANGE = 10.0;
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
    private BlockPos home = BlockPos.ZERO;
    /** Room for the most a minion can carry (spec 6.4: up to 54 with storage traits); {@link #slots} says how much it may use. */
    public final SimpleContainer inventory = new SimpleContainer(54);
    @Nullable
    private MinionStats stats;
    private int statsGeneration = -1;
    /** Where a rider sits, on its back where the saddle is drawn, before its turn: worked out with its stats. */
    private net.minecraft.world.phys.Vec3 seat = net.minecraft.world.phys.Vec3.ZERO;
    /** Blood not yet taken off a whole mB. */
    private float owed;
    /** A minion saved before minions were built of carcass parts: it falls apart on its first tick. */
    private boolean legacy;
    /** Set by job goals while they are doing something, for the drain. */
    boolean working;
    /** Which way of getting about its navigation is set up for: ground, climb, swim or fly. */
    private String movedBy = "";
    /** Which arm strikes next: they take turns. */
    private int nextStrike;
    /** Crouch-held clicks by the maker on it powered down, toward folding it up. */
    private int foldClicks;
    private long lastFoldClick;

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
        builder.define(JOB, MinionStats.COMPANION.toString());
        builder.define(DOWN, false);
        builder.define(POWER, 0.0F);
        builder.define(SADDLED, false);
        builder.define(MODULE, "");
        builder.define(CLIMBING, false);
    }

    /** Just woken: its maker, where it was made, what it is built of, and the blood it was woken with. */
    public void setup(@Nullable Player maker, BlockPos home, MinionBuild build, float power) {
        this.maker = maker == null ? null : maker.getUUID();
        this.home = home.immutable();
        setBuild(build);
        setHealth(getMaxHealth());
        entityData.set(POWER, Math.min(power, stats().reservoir()));
        entityData.set(JOB, stats().jobs().get(0).toString());
    }

    public Optional<MinionBuild> build() {
        return entityData.get(BUILD);
    }

    public void setBuild(MinionBuild build) {
        entityData.set(BUILD, Optional.of(build));
        stats = null;
        applyStats();
        if (!level().isClientSide) {
            // its parts' traits, and their attribute changes, go on with it
            com.avicagan.bloodandbones.parts.ActiveTraits.rebuild(this);
        }
    }

    /** What its build adds up to, worked out again when data changes. */
    public MinionStats stats() {
        PartsData.Store store = PartsData.of(level());
        if (stats == null || statsGeneration != store.generation()) {
            MinionBuild build = build().orElse(null);
            stats = build == null ? new MinionStats(10, 0, 3, 250, "crawl", 0.12F, 1, 0, List.of(), List.of(MinionStats.COMPANION), true, false, false, false, 0.6F, 0.6F, 0.6F, 0.6F)
                    : MinionStats.of(store, build);
            // the saddle's place, turned into the frame a passenger's place is given in (the renderer turns it a half turn more)
            org.joml.Vector3f saddle = build == null ? new org.joml.Vector3f(0.0F, stats.height(), 0.0F) : MinionBody.saddlePoint(MinionBody.layout(store, build));
            seat = new net.minecraft.world.phys.Vec3(-saddle.x, saddle.y, -saddle.z);
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
     * path up walls as a spider does, a flying torso flies, a swimmer takes to water; the rest walk. Changed only
     * when the build does.
     */
    private void moveBy(MinionStats s) {
        String kind = s.flies() ? "fly" : s.climbs() ? "climb" : "swim".equals(s.mode()) || "amphibious".equals(s.mode()) ? "swim" : "ground";
        boolean floats = "fly".equals(kind) && !poweredDown();
        if (isNoGravity() != floats) {
            setNoGravity(floats);
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
                navigation = new net.minecraft.world.entity.ai.navigation.WallClimberNavigation(this, level());
                moveControl = new net.minecraft.world.entity.ai.control.MoveControl(this);
            }
            case "swim" -> {
                navigation = new net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation(this, level());
                moveControl = new net.minecraft.world.entity.ai.control.MoveControl(this);
            }
            default -> {
                navigation = new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(this, level());
                moveControl = new net.minecraft.world.entity.ai.control.MoveControl(this);
            }
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
        return !stats().flies() && super.causeFallDamage(distance, multiplier, source);
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

    /** Saddled, whoever rides it steers it. */
    @Nullable
    @Override
    public net.minecraft.world.entity.LivingEntity getControllingPassenger() {
        return isSaddled() && !poweredDown() && getFirstPassenger() instanceof Player player ? player : super.getControllingPassenger();
    }

    @Override
    protected void tickRidden(Player player, net.minecraft.world.phys.Vec3 travel) {
        super.tickRidden(player, travel);
        setRot(player.getYRot(), player.getXRot() * 0.5F);
        yRotO = yBodyRot = yHeadRot = getYRot();
    }

    /** As a horse: forward and back (back slowly), and half-speed sideways. */
    @Override
    protected net.minecraft.world.phys.Vec3 getRiddenInput(Player player, net.minecraft.world.phys.Vec3 travel) {
        float forward = player.zza <= 0.0F ? player.zza * 0.25F : player.zza;
        return new net.minecraft.world.phys.Vec3(player.xxa * 0.5F, 0.0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    /** Its rider sits on its back where the saddle is, not on top of its whole hitbox (a raised head and all). */
    @Override
    protected net.minecraft.world.phys.Vec3 getPassengerAttachmentPoint(net.minecraft.world.entity.Entity passenger, EntityDimensions dimensions, float scale) {
        stats();
        return seat.yRot(-getYRot() * net.minecraft.util.Mth.DEG_TO_RAD);
    }

    /** A rider is pressing on it: the server holds a ridden mob's own motion at nothing and moves it from the rider's packets. */
    boolean steered() {
        return getControllingPassenger() instanceof Player rider && (rider.zza != 0.0F || rider.xxa != 0.0F);
    }

    // ---- strikes (its arms take turns; with none, it bites)

    /**
     * One blow at its target, in the style of the arm whose turn it is (docs/PARTS-AND-TRAITS.md section 5.6): a
     * punch, a kick that throws them back, a fling that throws them up, a grab that holds them, a sting that
     * poisons, a slam that hits everything round them, a claw or hook that tears. With no arm, the head bites.
     */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        MinionStats s = stats();
        List<MinionStats.Strike> strikes = s.strikes().stream().filter(k -> !"pacifist".equals(k.style())).toList();
        MinionStats.Strike strike = strikes.isEmpty() ? new MinionStats.Strike("bite", s.biteDamage()) : strikes.get(Math.floorMod(nextStrike++, strikes.size()));
        swing(InteractionHand.MAIN_HAND);
        if ("flap".equals(strike.style())) {
            knock(target, 0.8F);
            return true;
        }
        DamageSource source = damageSources().mobAttack(this);
        if (!target.hurt(source, Math.max(0.5F, strike.damage()))) {
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
        if (module() == com.avicagan.bloodandbones.cyber.Module.PISTON_RAM) {
            knock(target, MinionModules.RAM_KNOCKBACK);
        }
        return true;
    }

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

    public ResourceLocation job() {
        ResourceLocation job = ResourceLocation.tryParse(entityData.get(JOB));
        return job == null ? MinionStats.COMPANION : job;
    }

    /** Put it to one of the jobs its head offers; false if it offers no such job. */
    public boolean setJob(ResourceLocation job) {
        if (!stats().jobs().contains(job)) {
            return false;
        }
        entityData.set(JOB, job.toString());
        return true;
    }

    public boolean hasJob(String name) {
        return job().equals(BloodAndBones.asResource(name));
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
        return maker == null ? null : level().getPlayerByUUID(maker);
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
        MinionBuild build = build().orElse(null);
        if (build == null || build.cybernetic()) {
            return List.of();
        }
        List<ResourceLocation> out = new java.util.ArrayList<>();
        List<PieceRef> pieces = new java.util.ArrayList<>();
        pieces.add(build.torso());
        build.parts().forEach(f -> pieces.add(f.piece()));
        for (PieceRef piece : pieces) {
            if (!piece.skinned() && !out.contains(piece.entity()) && out.size() < HIDES) {
                out.add(piece.entity());
            }
        }
        return out;
    }

    public boolean cybernetic() {
        return build().map(MinionBuild::cybernetic).orElse(false);
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
            rate = WORKING;
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
            applyStats();
            // its traits, built again if its build or the data changed
            com.avicagan.bloodandbones.parts.ActiveTraits.of(this);
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
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MinionGoals.SeekBlood(this));
        goalSelector.addGoal(1, new MinionGoals.SeekCradle(this));
        goalSelector.addGoal(2, new MinionGoals.UseOrgan(this));
        goalSelector.addGoal(2, new MinionGoals.Bite(this));
        goalSelector.addGoal(3, new MinionGoals.Farm(this));
        goalSelector.addGoal(3, new MinionGoals.AttendTable(this));
        goalSelector.addGoal(4, new MinionGoals.Deposit(this));
        goalSelector.addGoal(5, new MinionGoals.Collect(this));
        goalSelector.addGoal(6, new MinionGoals.FollowMaker(this));
        goalSelector.addGoal(7, new MinionGoals.StayNearHome(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        MinionGoals.targets(this, targetSelector);
        // what each group of trait effects adds (a ranged minion's keep-away, say)
        com.avicagan.bloodandbones.parts.effect.MotionEffects.minionGoals(this, goalSelector, targetSelector);
        com.avicagan.bloodandbones.parts.effect.RangedEffects.minionGoals(this, goalSelector, targetSelector);
        com.avicagan.bloodandbones.parts.effect.SocialEffects.minionGoals(this, goalSelector, targetSelector);
        com.avicagan.bloodandbones.parts.effect.UpkeepEffects.minionGoals(this, goalSelector, targetSelector);
    }

    // ---- never destroyed by neglect

    /** Powered down, only a player (or the void, /kill) can hurt it. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (poweredDown() && !(source.getEntity() instanceof Player) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
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
        if (isSaddled()) {
            spawnAtLocation(Items.SADDLE);
        }
        if (module() != null) {
            spawnAtLocation(BBItems.module(module()));
        }
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
     * A bucket of blood feeds it. Its maker, crouching with an empty hand: changes its job while it is up, and
     * held for three seconds on it powered down, folds it into a Dormant Minion to carry. Anyone else: what it is.
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
        if (cybernetic() && isMaker(player) && held.is(com.simibubi.create.AllItems.WRENCH.get()) && module() != null) {
            if (!level().isClientSide) {
                player.getInventory().placeItemBackInInventory(new ItemStack(BBItems.module(module())));
                setModule(null);
                com.avicagan.bloodandbones.cyber.Coupler.release(this);
                level().playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.0F, 1.3F);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (cybernetic() && held.is(com.simibubi.create.AllItems.BRASS_SHEET.get()) && getHealth() < getMaxHealth()) {
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
        if (isMaker(player) && poweredDown() && com.avicagan.bloodandbones.body.Surgery.isBlade(held)) {
            // its maker takes it apart, lying on an Assembly Frame table: back to a frame there
            if (!level().isClientSide) {
                MinionAssembly.takeApart((ServerLevel) level(), this, player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!held.isEmpty()) {
            return super.mobInteract(player, hand);
        }
        if (isSaddled() && !isVehicle() && !poweredDown() && isMaker(player) && !player.isSecondaryUseActive()) {
            // saddled: its maker climbs on
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
            List<ResourceLocation> jobs = stats().jobs();
            ResourceLocation next = jobs.get((jobs.indexOf(job()) + 1) % jobs.size());
            entityData.set(JOB, next.toString());
            setTarget(null);
            player.displayClientMessage(Component.translatable("bloodandbones.minion.job_now", Component.translatable(jobKey(next))), true);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable(poweredDown() ? "bloodandbones.minion.status_down" : "bloodandbones.minion.status",
                Component.translatable(jobKey(job())), Math.round(power()), stats().reservoir()), true);
        return InteractionResult.CONSUME;
    }

    public static String jobKey(ResourceLocation job) {
        return "bloodandbones.minion.job." + job.getPath();
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
        tag.putString("Job", entityData.get(JOB));
        tag.putFloat("Power", power());
        tag.putBoolean("Down", poweredDown());
        tag.putBoolean("Saddled", isSaddled());
        tag.putString("Module", entityData.get(MODULE));
        tag.put("Inventory", inventory.createTag(registryAccess()));
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
        entityData.set(JOB, tag.getString("Job"));
        entityData.set(POWER, tag.getFloat("Power"));
        entityData.set(DOWN, tag.getBoolean("Down"));
        entityData.set(SADDLED, tag.getBoolean("Saddled"));
        entityData.set(MODULE, tag.getString("Module"));
        stats = null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
