package com.avicagan.bloodandbones.cyber;

import com.avicagan.bloodandbones.registry.BBBlocks;
import com.simibubi.create.content.kinetics.base.IRotate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Rotational Coupler: while the throttle is held and you look at the end of a machine's shaft (any
 * Create block that takes rotation on that face) within reach, a shaft reaches out of your arm into it and
 * turns it. In the world that is a hidden generator block in the space in front of the face, owned by you,
 * gone the moment you let go, look away, run dry or walk off. A tap turns it at 16 RPM with enough to run a
 * drill or a press; full spool, 256 RPM with sixteen times the stress capacity, at full throttle's price.
 */
public final class Coupler {
    /** How far the shaft reaches. */
    public static final double REACH = 3.5;
    /** RPM at the bottom of the throttle and at the top. */
    public static final int BASE_RPM = 16;
    public static final int MAX_RPM = 256;
    /** Stress capacity, per RPM (Create's measure): 16 RPM gives 256 su, 256 RPM gives 4096. */
    public static final float CAPACITY = 16.0F;

    record Link(ResourceKey<Level> level, BlockPos pos) {
    }

    private static final Map<UUID, Link> LINKS = new ConcurrentHashMap<>();

    private Coupler() {
    }

    /** RPM at this spool, in Create's steps of 16. */
    public static int rpm(float level) {
        int rpm = Math.round(BASE_RPM + (MAX_RPM - BASE_RPM) * level * level);
        return Math.max(BASE_RPM, Math.min(MAX_RPM, rpm / 16 * 16));
    }

    /**
     * Where a coupler of this player's would go: walking along the look block by block, the first block that
     * takes rotation on the face the look comes in by, with room in front of that face. Forgiving on purpose:
     * a bare shaft is a quarter of a block across, and the look only has to pass through its block, not touch
     * the rod. Anything else solid in the way stops the search.
     *
     * @return the machine's position and the face, or null
     */
    @Nullable
    static BlockHitResult target(Player player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockPos prev = BlockPos.containing(eye);
        for (double d = 0.05; d <= REACH; d += 0.05) {
            Vec3 at = eye.add(look.scale(d));
            BlockPos pos = BlockPos.containing(at);
            if (pos.equals(prev)) {
                continue;
            }
            Direction face = Direction.getNearest(prev.getX() - pos.getX(), prev.getY() - pos.getY(), prev.getZ() - pos.getZ());
            BlockPos front = pos.relative(face);
            BlockState machine = level.getBlockState(pos);
            if (machine.getBlock() instanceof IRotate rotate && !(machine.getBlock() instanceof CouplerBlock)
                    && rotate.hasShaftTowards(level, pos, machine, face)) {
                BlockState there = level.getBlockState(front);
                // a coupler is replaceable, but someone else's (a minion's, another player's) is not free
                boolean free = there.is(BBBlocks.COUPLER.get()) ? owns(player.getUUID(), level, front) : there.canBeReplaced();
                return free && level.mayInteract(player, front) ? new BlockHitResult(at, face, pos, false) : null;
            }
            if (!machine.canBeReplaced() && !(machine.getBlock() instanceof CouplerBlock)) {
                return null;
            }
            prev = pos;
        }
        return null;
    }

    /** Every tick the throttle is held: couple to what is looked at, at this spool, or uncouple. */
    static void hold(Player player, float level) {
        ServerLevel world = ((ServerLevel) player.level());
        BlockHitResult hit = target(player);
        if (hit == null) {
            stop(player);
            return;
        }
        couple(player, hit.getBlockPos().relative(hit.getDirection()), hit.getDirection().getOpposite(), rpm(level));
    }

    /**
     * Couple this owner (a player's arm, a brass minion) into the machine whose face is in front of {@code at},
     * {@code towardMachine} pointing from {@code at} into it, turning it at this RPM. Moves the link if it was
     * somewhere else.
     */
    public static void couple(net.minecraft.world.entity.Entity owner, BlockPos at, net.minecraft.core.Direction towardMachine, int rpm) {
        ServerLevel world = (ServerLevel) owner.level();
        Link link = LINKS.get(owner.getUUID());
        if (link != null && (!link.pos.equals(at) || link.level != world.dimension())) {
            release(owner);
            link = null;
        }
        if (link == null) {
            LINKS.put(owner.getUUID(), new Link(world.dimension(), at));
            world.setBlock(at, BBBlocks.COUPLER.get().defaultBlockState().setValue(CouplerBlock.FACING, towardMachine), 3);
            world.playSound(null, at, net.minecraft.sounds.SoundEvents.PISTON_EXTEND, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
        }
        if (world.getBlockEntity(at) instanceof CouplerBlockEntity coupler) {
            coupler.drive(owner, rpm);
        }
    }

    /** Whether this owner is coupled into something now. */
    public static boolean coupled(net.minecraft.world.entity.Entity owner) {
        return LINKS.containsKey(owner.getUUID());
    }

    /** Let go: the shaft pulls back out of the machine. */
    static void stop(Player player) {
        release(player);
    }

    /** The owner lets go (or is gone): the shaft pulls back out of the machine. */
    public static void release(net.minecraft.world.entity.Entity owner) {
        Link link = LINKS.remove(owner.getUUID());
        if (link == null || owner.getServer() == null) {
            return;
        }
        ServerLevel level = owner.getServer().getLevel(link.level);
        // only its own: a coupler someone else holds there now stays
        if (level != null && level.isLoaded(link.pos) && level.getBlockState(link.pos).is(BBBlocks.COUPLER.get())
                && level.getBlockEntity(link.pos) instanceof CouplerBlockEntity coupler && owner.getUUID().equals(coupler.owner())) {
            level.removeBlock(link.pos, false);
            level.playSound(null, link.pos, net.minecraft.sounds.SoundEvents.PISTON_CONTRACT, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
        }
    }

    /** Whether this player is holding the coupler at that place now. */
    public static boolean owns(@Nullable UUID player, Level level, BlockPos pos) {
        Link link = player == null ? null : LINKS.get(player);
        return link != null && link.level == level.dimension() && link.pos.equals(pos);
    }

    /** Forget every link (the server stopped). */
    public static void clear() {
        LINKS.clear();
    }
}
