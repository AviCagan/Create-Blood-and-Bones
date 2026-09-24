package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.body.BBAttachments;
import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * A minion: a body stitched together on the Surgery Table from carcass parts, limbs, organs and implants,
 * woken with soul blood. Its parts decide its job ({@link MinionJob}), its legs how it walks and its arms how
 * it hits, as for anyone. It keeps near where it was made (its home), follows its maker when it fights or
 * keeps company, and wears a backtank if given one (right-click it with one) to run powered implants.
 */
public class MinionEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> JOB = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> HEAD = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.STRING);
    /** How far from home it works. */
    public static final double RANGE = 10.0;

    @Nullable
    private UUID maker;
    private BlockPos home = BlockPos.ZERO;
    public final SimpleContainer inventory = new SimpleContainer(9);

    public MinionEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setDropChance(EquipmentSlot.CHEST, 2.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 24.0).add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 3.0).add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(JOB, MinionJob.COMPANION.ordinal());
        builder.define(HEAD, "");
    }

    /** Just woken: its maker, where it was made, and what it was built as. */
    public void setup(Player maker, BlockPos home, MinionFrame.Frame frame) {
        this.maker = maker.getUUID();
        this.home = home.immutable();
        setData(BBAttachments.BODY, frame.body().copy());
        entityData.set(HEAD, frame.head().map(ResourceLocation::toString).orElse(""));
        updateJob();
    }

    public MinionJob job() {
        return MinionJob.values()[Math.floorMod(entityData.get(JOB), MinionJob.values().length)];
    }

    public Optional<ResourceLocation> head() {
        String head = entityData.get(HEAD);
        return head.isEmpty() ? Optional.empty() : Optional.ofNullable(ResourceLocation.tryParse(head));
    }

    public BlockPos home() {
        return home;
    }

    @Nullable
    public Player maker() {
        return maker == null ? null : level().getPlayerByUUID(maker);
    }

    public boolean isMaker(Player player) {
        return player.getUUID().equals(maker);
    }

    private void updateJob() {
        entityData.set(JOB, MinionJob.of(BodyEffects.body(this), this).ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MinionGoals.VentAttack(this));
        goalSelector.addGoal(2, new MinionGoals.Melee(this));
        goalSelector.addGoal(3, new MinionGoals.Farm(this));
        goalSelector.addGoal(4, new MinionGoals.Deposit(this));
        goalSelector.addGoal(5, new MinionGoals.Collect(this));
        goalSelector.addGoal(6, new MinionGoals.FollowMaker(this));
        goalSelector.addGoal(7, new MinionGoals.StayNearHome(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return job() == MinionJob.FIGHTER && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.Mob.class, 10, true, false,
                target -> target instanceof Enemy && !(target instanceof MinionEntity)) {
            @Override
            public boolean canUse() {
                return job() == MinionJob.FIGHTER && super.canUse();
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 20 == 0) {
            updateJob();
            if (job() != MinionJob.FIGHTER && getTarget() != null) {
                setTarget(null);
            }
        }
    }

    /** Its maker: with a backtank, puts it on; sneaking with an empty hand, takes it back. Anyone: what it is. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (isMaker(player) && held.getItem() instanceof FluidBacktankItem) {
            if (!level().isClientSide) {
                ItemStack old = getItemBySlot(EquipmentSlot.CHEST);
                setItemSlot(EquipmentSlot.CHEST, held.copyWithCount(1));
                held.consume(1, player);
                if (!old.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(old);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (held.isEmpty()) {
            if (!level().isClientSide) {
                if (isMaker(player) && player.isShiftKeyDown() && !getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
                    player.getInventory().placeItemBackInInventory(getItemBySlot(EquipmentSlot.CHEST));
                    setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                } else {
                    player.displayClientMessage(Component.translatable("bloodandbones.minion.status", Component.translatable(job().translationKey())), true);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    /** Killed, it drops what it carried and the implants that were in it. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            spawnAtLocation(inventory.removeItemNoUpdate(i));
        }
        Body body = BodyEffects.body(this);
        for (BodyPart part : BodyPart.values()) {
            if (body.state(part) == Body.State.IMPLANT) {
                spawnAtLocation(body.unclip(part));
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (maker != null) {
            tag.putUUID("Maker", maker);
        }
        tag.put("Home", NbtUtils.writeBlockPos(home));
        tag.putString("Head", entityData.get(HEAD));
        tag.put("Inventory", inventory.createTag(registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        maker = tag.hasUUID("Maker") ? tag.getUUID("Maker") : null;
        home = NbtUtils.readBlockPos(tag, "Home").orElse(blockPosition());
        entityData.set(HEAD, tag.getString("Head"));
        inventory.fromTag(tag.getList("Inventory", 10), registryAccess());
        updateJob();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
