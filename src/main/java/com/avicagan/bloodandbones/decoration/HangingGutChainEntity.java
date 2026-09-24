package com.avicagan.bloodandbones.decoration;

import com.avicagan.bloodandbones.carcass.trolley.ChainCursor;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A length of Gut Chain hung from a Create chain conveyor, riding it as packages do. It follows the chain
 * with the same {@link ChainCursor} the Shackle Trolley uses; the server moves it and every client swings
 * its own copy of the hanging string (see {@link #swing}), so it sways as it goes round wheels and stops.
 * Hit it to take it down: its links drop.
 */
public class HangingGutChainEntity extends Entity {
    /** Longest it can be: each Gut Chain used adds one link, a block long like a placed one. */
    public static final int MAX_LINKS = 8;
    private static final EntityDataAccessor<Integer> DATA_LINKS = SynchedEntityData.defineId(HangingGutChainEntity.class, EntityDataSerializers.INT);
    /** The string is drawn as two joints a link, so it bends along its length. */
    public static final int JOINTS_PER_LINK = 2;
    /** Pull down each tick (blocks per tick per tick, as the game's gravity) and air drag. */
    private static final double GRAVITY = 0.06;
    private static final double DRAG = 0.94;

    @Nullable
    private ChainCursor cursor;

    // client only: the hanging string, this tick and last, from the top (on the chain) down
    @Nullable
    private Vec3[] joints;
    @Nullable
    private Vec3[] lastJoints;

    public HangingGutChainEntity(EntityType<? extends HangingGutChainEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Server: a string of one link hung at the cursor's place on the chain. */
    public static HangingGutChainEntity create(ServerLevel level, ChainCursor cursor, int links) {
        HangingGutChainEntity chain = new HangingGutChainEntity(BBEntities.HANGING_GUT_CHAIN.get(), level);
        chain.cursor = cursor;
        chain.setLinks(links);
        ChainConveyorBlockEntity be = ChainCursor.conveyorAt(level, cursor.conveyor);
        Vec3 start = be == null ? null : cursor.chainPoint(be);
        if (start != null) {
            chain.setPos(start);
        }
        return chain;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LINKS, 1);
    }

    public int links() {
        return entityData.get(DATA_LINKS);
    }

    public void setLinks(int links) {
        entityData.set(DATA_LINKS, Mth.clamp(links, 1, MAX_LINKS));
        setBoundingBox(makeBoundingBox());
    }

    @Nullable
    public ChainCursor cursor() {
        return cursor;
    }

    /** The box hangs down from the chain the string's length, so the string can be hit anywhere along it. */
    @Override
    protected AABB makeBoundingBox() {
        double length = links();
        return new AABB(getX() - 0.2, getY() - length - 0.05, getZ() - 0.2, getX() + 0.2, getY() + 0.1, getZ() + 0.2);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_LINKS.equals(key)) {
            setBoundingBox(makeBoundingBox());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            advance(level);
        } else {
            swing();
        }
    }

    /** One tick along the chain; a broken chain drops the string. */
    private void advance(ServerLevel level) {
        if (cursor == null) {
            takeDown(null);
            return;
        }
        ChainCursor.Step step = cursor.advance(level, false);
        if (step == ChainCursor.Step.DERAILED) {
            takeDown(null);
            return;
        }
        ChainConveyorBlockEntity be = ChainCursor.conveyorAt(level, cursor.conveyor);
        Vec3 point = be == null ? null : cursor.chainPoint(be);
        if (point != null) {
            setPos(point);
            Vec3 heading = cursor.heading(be);
            setYRot((float) Math.toDegrees(Math.atan2(-heading.x, heading.z)));
        }
    }

    /**
     * Client: the string as a chain of joints a set length apart, the top one held on the chain, the rest
     * falling and dragged by the air, then pulled back to length (Verlet). Moving the top swings the rest.
     */
    private void swing() {
        int count = links() * JOINTS_PER_LINK + 1;
        double gap = 1.0 / JOINTS_PER_LINK;
        Vec3 top = position();
        if (joints == null || lastJoints == null || joints.length != count) {
            // first seen: hanging straight down; lengthened or shortened: the joints kept, new ones below the last
            Vec3[] kept = joints;
            joints = new Vec3[count];
            for (int i = 0; i < count; i++) {
                joints[i] = kept != null && i < kept.length ? kept[i]
                        : (i == 0 ? top : joints[i - 1].subtract(0.0, gap, 0.0));
            }
            lastJoints = joints.clone();
            return;
        }
        Vec3[] before = joints.clone();
        joints[0] = top;
        for (int i = 1; i < count; i++) {
            Vec3 velocity = joints[i].subtract(lastJoints[i]).scale(DRAG);
            joints[i] = joints[i].add(velocity).subtract(0.0, GRAVITY, 0.0);
        }
        for (int pass = 0; pass < 12; pass++) {
            joints[0] = top;
            for (int i = 1; i < count; i++) {
                Vec3 link = joints[i].subtract(joints[i - 1]);
                double length = link.length();
                if (length < 1.0e-6) {
                    continue;
                }
                Vec3 fix = link.scale((length - gap) / length);
                if (i == 1) {
                    joints[i] = joints[i].subtract(fix);
                } else {
                    joints[i - 1] = joints[i - 1].add(fix.scale(0.5));
                    joints[i] = joints[i].subtract(fix.scale(0.5));
                }
            }
        }
        joints[0] = top;
        lastJoints = before;
    }

    /** Client: where the string's joints are drawn this frame, between last tick and this one; null before the first tick. */
    @Nullable
    public Vec3[] drawnJoints(float partialTick) {
        if (joints == null || lastJoints == null) {
            return null;
        }
        Vec3[] out = new Vec3[joints.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = lastJoints[i].lerp(joints[i], partialTick);
        }
        out[0] = getPosition(partialTick);
        return out;
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    /** Another Gut Chain used on the string makes it a link longer. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(BBBlocks.GUT_CHAIN.asItem())) {
            return InteractionResult.PASS;
        }
        if (links() >= MAX_LINKS) {
            // as long as it goes; and not a second string hung on the chain behind it either
            return InteractionResult.CONSUME;
        }
        if (!level().isClientSide) {
            setLinks(links() + 1);
            stack.consume(1, player);
            level().playSound(null, getX(), getY() - links(), getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 0.9F);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || isRemoved()) {
            return false;
        }
        if (!level().isClientSide) {
            takeDown(source.getEntity());
        }
        return true;
    }

    /** Down it comes, its links dropped where it hung (none for a creative player, as with a painting). */
    public void takeDown(@Nullable Entity by) {
        if (isRemoved()) {
            return;
        }
        if (!(by instanceof Player player && player.hasInfiniteMaterials())) {
            spawnAtLocation(new ItemStack(BBBlocks.GUT_CHAIN.asItem(), links()), -links() / 2.0F);
        }
        playSound(SoundEvents.SLIME_BLOCK_BREAK, 1.0F, 0.8F);
        discard();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(BBBlocks.GUT_CHAIN.asItem());
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        cursor = tag.contains("Cursor") ? ChainCursor.load(tag.getCompound("Cursor")) : null;
        setLinks(tag.getInt("Links"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (cursor != null) {
            tag.put("Cursor", cursor.save());
        }
        tag.putInt("Links", links());
    }
}
