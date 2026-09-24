package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.config.BBServerConfig;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.registry.BBFluids;
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
public class MinionEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<MinionBuild>> BUILD = SynchedEntityData.defineId(MinionEntity.class, MinionSerializers.BUILD.get());
    private static final EntityDataAccessor<String> JOB = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DOWN = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.FLOAT);
    /** How far from home it works. */
    public static final double RANGE = 10.0;
    /** mB of blood a minute: idle, moving, working, fighting (docs/PARTS-AND-TRAITS.md section 6.7). */
    public static final float IDLE = 3.0F;
    public static final float MOVING = 15.0F;
    public static final float WORKING = 25.0F;
    public static final float FIGHTING = 40.0F;
    /** Below this share of its blood it goes to drink. */
    public static final float HUNGRY = 0.25F;

    @Nullable
    private UUID maker;
    private BlockPos home = BlockPos.ZERO;
    public final SimpleContainer inventory = new SimpleContainer(27);
    @Nullable
    private MinionStats stats;
    private int statsGeneration = -1;
    /** Blood not yet taken off a whole mB. */
    private float owed;
    /** A minion saved before minions were built of carcass parts: it falls apart on its first tick. */
    private boolean legacy;
    /** Set by job goals while they are doing something, for the drain. */
    boolean working;
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
                .add(Attributes.ATTACK_DAMAGE, 1.0).add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BUILD, Optional.empty());
        builder.define(JOB, MinionStats.COMPANION.toString());
        builder.define(DOWN, false);
        builder.define(POWER, 0.0F);
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
    }

    /** What its build adds up to, worked out again when data changes. */
    public MinionStats stats() {
        PartsData.Store store = PartsData.of(level());
        if (stats == null || statsGeneration != store.generation()) {
            MinionBuild build = build().orElse(null);
            stats = build == null ? new MinionStats(10, 0, 3, 250, "crawl", 0.12F, 1, 0, List.of(), List.of(MinionStats.COMPANION), true, 0.6F, 0.6F, 0.6F, 0.6F)
                    : MinionStats.of(store, build);
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
        float arm = s.arms().stream().max(Float::compare).orElse(0.0F);
        base(Attributes.ATTACK_DAMAGE, Math.max(arm, s.biteDamage()));
        if (getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
        refreshDimensions();
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

    public boolean cybernetic() {
        return build().map(MinionBuild::cybernetic).orElse(false);
    }

    /** Whether it has room for this in the slots its torso gives it. */
    public boolean canCarry(ItemStack stack) {
        int slots = stats().slots();
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
        int slots = stats().slots();
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

    /** Out of blood: it stops where it is and lies down. */
    public void powerDown() {
        entityData.set(POWER, 0.0F);
        if (!poweredDown()) {
            entityData.set(DOWN, true);
            getNavigation().stop();
            setTarget(null);
            refreshDimensions();
            level().playSound(null, blockPosition(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.NEUTRAL, 1.0F, 0.6F);
        }
    }

    /** Blood used this tick: a little just to be awake, more moving, working, fighting. */
    private void drain() {
        float rate = IDLE;
        if (getTarget() != null) {
            rate = FIGHTING;
        } else if (working) {
            rate = WORKING;
        } else if (getDeltaMovement().horizontalDistanceSqr() > 1.0E-4 || !getNavigation().isDone()) {
            rate = MOVING;
        }
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
            // built the old way (a humanoid with implants): it falls apart, dropping what it had
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                spawnAtLocation(inventory.removeItemNoUpdate(i));
            }
            discard();
            return;
        }
        if (tickCount % 20 == 0) {
            applyStats();
        }
        if (poweredDown()) {
            // down: a trough right beside it tops it up (so a guard fallen by its trough gets up again)
            if (tickCount % 20 == 0) {
                MinionGoals.drinkNearby(this);
            }
        } else {
            drain();
        }
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

    /** A lethal blow collapses it (by default): 1 health, no blood, powered down. It is never destroyed that way. */
    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && BBServerConfig.minionDeath() == BBServerConfig.MinionDeath.COLLAPSE) {
            setHealth(1.0F);
            powerDown();
            return;
        }
        super.die(source);
        MinionCensus.forget(this);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            spawnAtLocation(inventory.removeItemNoUpdate(i));
        }
        if (BBServerConfig.minionDeath() == BBServerConfig.MinionDeath.SCATTER && build().isPresent()) {
            // it falls apart into what it was built of, half gone off
            MinionBuild build = build().get();
            spawnAtLocation(MinionAssembly.pieceItem(build.torso(), 0.5F));
            for (MinionBuild.Fitted fitted : build.parts()) {
                spawnAtLocation(MinionAssembly.pieceItem(fitted.piece(), 0.5F));
            }
        }
    }

    // ---- hands on

    /**
     * A bucket of blood feeds it. Its maker, crouching with an empty hand: changes its job while it is up, and
     * held for three seconds on it powered down, folds it into a Dormant Minion to carry. Anyone else: what it is.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(BBFluids.BLOOD.getBucket().get())) {
            if (!level().isClientSide && feed(1000.0F) > 0.0F && !player.hasInfiniteMaterials()) {
                held.shrink(1);
                player.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!held.isEmpty()) {
            return super.mobInteract(player, hand);
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
        stats = null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
