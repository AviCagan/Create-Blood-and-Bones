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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A length of Gut Chain hung from a Create chain conveyor, riding it as packages do. It follows the chain
 * with the same {@link ChainCursor} the Shackle Trolley uses; the server moves it and every client swings
 * its own copy of the hanging string (see {@link #swing}), so it sways as it goes round wheels and stops.
 * Each link is a hit box of its own ({@link Link}), so the string can be hit anywhere along it. Hit it to
 * take it down: its links drop.
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
    /** One hit box a link, all made at the start: the game only asks an entity for its parts as it is added. */
    private final Link[] parts = new Link[MAX_LINKS];

    // client only: the hanging string, this tick and last, from the top (on the chain) down
    @Nullable
    private Vec3[] joints;
    @Nullable
    private Vec3[] lastJoints;
    // client only: where the server last said it is, and the ticks left to glide there (see lerpTo)
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYRot;

    public HangingGutChainEntity(EntityType<? extends HangingGutChainEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = new Link(this, i);
        }
        // the links' ids follow the string's own, so they match on the server and every client (as the Ender Dragon's do)
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
        placeParts();
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
        placeParts();
    }

    @Nullable
    public ChainCursor cursor() {
        return cursor;
    }

    /** The box hangs down from the chain the string's length; hits land on the links' own boxes ({@link Link}). */
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
            placeParts();
        }
    }

    // ---------------------------------------------------------------- the links' hit boxes

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < parts.length; i++) {
            parts[i].setId(id + i + 1);
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        placeParts();
    }

    /** The links' boxes one under another down from the chain; those not hung yet sit on the last one, unpickable. */
    private void placeParts() {
        // the game gives an entity its first position before this class has made its links
        if (parts == null || parts[parts.length - 1] == null) {
            return;
        }
        int links = links();
        for (int i = 0; i < parts.length; i++) {
            parts[i].setPos(getX(), getY() + 0.1 - Math.min(i, links - 1) - Link.HEIGHT, getZ());
        }
    }

    /** Only the links are hit, each where it hangs. */
    @Override
    public boolean isPickable() {
        return false;
    }

    // ---------------------------------------------------------------- moving

    @Override
    public void tick() {
        super.tick();
        for (Link part : parts) {
            part.setOldPosAndRot();
        }
        if (level() instanceof ServerLevel level) {
            advance(level);
        } else {
            clientTick();
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
     * Client: where the server says it is arrives once a tick. Glide there over the next few ticks, as a
     * minecart does, rather than jump there, so it moves smoothly between ticks as Create's packages do.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = steps;
    }

    @Override
    public double lerpTargetX() {
        return lerpSteps > 0 ? lerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return lerpSteps > 0 ? lerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return lerpSteps > 0 ? lerpZ : getZ();
    }

    @Override
    public float lerpTargetYRot() {
        return lerpSteps > 0 ? lerpYRot : getYRot();
    }

    /** Client, each tick: a step of the glide towards where the server put it, then the string swings after its top. */
    public void clientTick() {
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, getXRot());
            lerpSteps--;
        }
        swing();
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

    /**
     * Client: a box round the whole string as it swings, this tick and last, for the renderer to cull by. On
     * a fast chain the string trails well behind its top, out of the straight-down box.
     */
    public AABB cullingBox() {
        if (joints == null || lastJoints == null) {
            return getBoundingBox().inflate(1.0);
        }
        AABB box = getBoundingBox();
        for (Vec3[] set : new Vec3[][]{joints, lastJoints}) {
            for (Vec3 joint : set) {
                box = box.minmax(new AABB(joint, joint));
            }
        }
        return box.inflate(0.5);
    }

    // ---------------------------------------------------------------- interaction

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

    /**
     * One link of the string as a hit box of its own, passing hits and clicks on to the string. The game files
     * an entity under the 16-block section its position is in, and a search for entities in a box looks only a
     * little below that box; so one long box hanging from the chain was missed by a player or an arrow aiming
     * at its lower end once that end hung in the section below. Parts are kept in a list of their own, which
     * every search looks through.
     */
    public static final class Link extends PartEntity<HangingGutChainEntity> {
        /** A block, and a little more to meet the next link and the chain. */
        static final float HEIGHT = 1.15F;
        private static final EntityDimensions SIZE = EntityDimensions.scalable(0.4F, HEIGHT);
        private final int index;

        Link(HangingGutChainEntity string, int index) {
            super(string);
            this.index = index;
            refreshDimensions();
        }

        @Override
        public EntityDimensions getDimensions(Pose pose) {
            return SIZE;
        }

        /** Only the links that are hung. */
        @Override
        public boolean isPickable() {
            return index < getParent().links() && !getParent().isRemoved();
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            return getParent().hurt(source, amount);
        }

        @Override
        public InteractionResult interact(Player player, InteractionHand hand) {
            return getParent().interact(player, hand);
        }

        @Override
        public ItemStack getPickResult() {
            return getParent().getPickResult();
        }

        @Override
        public boolean is(Entity entity) {
            return this == entity || getParent() == entity;
        }

        @Override
        public boolean shouldBeSaved() {
            return false;
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder builder) {
        }

        @Override
        protected void readAdditionalSaveData(CompoundTag tag) {
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag tag) {
        }
    }
}
