package com.avicagan.bloodandbones.carcass.trolley;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectedPort;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectionStats;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A position on a Create chain conveyor network, advanced with exactly the rules Create uses for packages
 * (ChainConveyorBlockEntity#tick) and for riding players (ChainConveyorRidingHandler#updateTargetPosition),
 * but owned by us instead of by the conveyor block entity.
 */
public final class ChainCursor {
    /** Radius of the strand end points around a wheel (ChainConveyorBlockEntity#calculateConnectionStats). */
    public static final float LOOP_RADIUS = 1.25f;
    /** Height of the chain line above the conveyor block's bottom face. */
    public static final float CHAIN_Y = 6 / 16f;
    /** Create's off-branch angle between a connection's direction and where its strand leaves the wheel. */
    private static final float OFF_BRANCH = 35f;

    /** Absolute position of the conveyor that owns the current strand, or whose wheel we are looping. */
    public BlockPos conveyor;
    /** Relative offset (a key of {@code be.connections}); null while looping around {@link #conveyor}. */
    @Nullable
    public BlockPos connection;
    /** Blocks from the strand start, or degrees around the wheel while looping. */
    public float position;
    /** Conveyor speed was negative when we got on this strand: Create's strands swap sides when it flips. */
    public boolean flipped;
    /** While looping: the connection leading back where we came from. */
    @Nullable
    public BlockPos cameFrom;
    /** Optional package-style address; routed with the conveyor's own routing table. */
    public String address = "";

    public enum Step { MOVED, IDLE, WAITING, ARRIVED, DERAILED }

    public ChainCursor(BlockPos conveyor, @Nullable BlockPos connection, float position, boolean flipped) {
        this.conveyor = conveyor;
        this.connection = connection;
        this.position = position;
        this.flipped = flipped;
    }

    // ---- speed (Create has no public helper: formula copied from ChainConveyorBlockEntity#tick) ----

    public static float blocksPerTick(ChainConveyorBlockEntity be) {
        return Math.abs(be.getSpeed()) / 360f;
    }

    public static float degreesPerTick(ChainConveyorBlockEntity be) {
        return be.getSpeed() / 360f / (Mth.PI * 1.5f) * 360f;
    }

    /** Never force-loads a chunk: ServerLevel#getBlockEntity on an unloaded position would. */
    @Nullable
    public static ChainConveyorBlockEntity conveyorAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof ChainConveyorBlockEntity be ? be : null;
    }

    // ---- geometry ----

    /** World point on the chain line for this cursor, or null when the strand no longer exists. */
    @Nullable
    public Vec3 chainPoint(ChainConveyorBlockEntity be) {
        if (connection == null) {
            // Same radius as the strand ends, so strand -> wheel -> strand is continuous.
            // (be.getPackagePosition(angle, null) uses 0.875, which jumps 0.375 blocks at every junction.)
            return Vec3.atBottomCenterOf(conveyor)
                    .add(VecHelper.rotate(new Vec3(0, CHAIN_Y, LOOP_RADIUS), position, Direction.Axis.Y));
        }
        be.prepareStats();
        ConnectionStats stats = be.connectionStats.get(connection);
        if (stats == null) {
            return null;
        }
        Vec3 dir = stats.end().subtract(stats.start()).normalize();
        return stats.start().add(dir.scale(Mth.clamp(position, 0f, stats.chainLength())));
    }

    /** Horizontal unit vector of travel at the cursor (strand direction, or wheel tangent). */
    public Vec3 heading(ChainConveyorBlockEntity be) {
        Vec3 dir;
        if (connection == null) {
            Vec3 radial = VecHelper.rotate(new Vec3(0, 0, 1), position, Direction.Axis.Y);
            dir = new Vec3(radial.z, 0, -radial.x);
            if (be.getSpeed() < 0) {
                dir = dir.scale(-1);
            }
        } else {
            be.prepareStats();
            ConnectionStats stats = be.connectionStats.get(connection);
            dir = stats == null ? new Vec3(0, 0, 1) : stats.end().subtract(stats.start());
        }
        dir = new Vec3(dir.x, 0, dir.z);
        return dir.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : dir.normalize();
    }

    // ---- motion ----

    /**
     * One server tick of travel. Mirrors ChainConveyorBlockEntity#tick: straight strands always run from
     * stats.start to stats.end; the wheel turns with the sign of the speed; hand-over at a strand end enters
     * the next wheel at the strand's end angle; the wheel is left when its tangent angle is crossed.
     */
    public Step advance(Level level, boolean hold) {
        ChainConveyorBlockEntity be = conveyorAt(level, conveyor);
        if (be == null) {
            return level.isLoaded(conveyor) ? Step.DERAILED : Step.WAITING;
        }
        be.prepareStats(); // recomputes connectionStats (and be.reversed) when the speed sign changed
        if (connection != null) {
            if (!be.connections.contains(connection)) {
                return Step.DERAILED;
            }
            // Speed flipped: this conveyor's stats now describe the OTHER side of the loop, and our strand
            // belongs to the conveyor at the far end, run backwards (ChainConveyorBlockEntity#tick lines 173-190,
            // ChainConveyorRidingHandler lines 128-135). Checked even when stopped or parked, or we would jump sides.
            if (flipped != be.reversed) {
                BlockPos otherPos = conveyor.offset(connection);
                if (conveyorAt(level, otherPos) == null) {
                    return Step.WAITING;
                }
                ConnectionStats stats = be.connectionStats.get(connection);
                float length = stats == null ? position : stats.chainLength();
                position = Math.max(0, length - position);
                conveyor = otherPos;
                connection = connection.multiply(-1);
                flipped = !flipped;
                return Step.MOVED;
            }
        }
        float rpm = be.getSpeed(); // 0 when overstressed or the tick rate is frozen (KineticBlockEntity#getSpeed)
        if (hold || rpm == 0) {
            return Step.IDLE;
        }
        return connection == null ? advanceLoop(be) : advanceStrand(level, be);
    }

    private Step advanceStrand(Level level, ChainConveyorBlockEntity be) {
        ConnectionStats stats = be.connectionStats.get(connection);
        if (stats == null) {
            return Step.DERAILED;
        }
        float prev = position;
        position = Math.min(stats.chainLength(), position + blocksPerTick(be));
        if (stopAtStrandPort(be, prev)) {
            return Step.ARRIVED;
        }
        if (position < stats.chainLength()) {
            return Step.MOVED;
        }
        // Hand over to the next wheel (ChainConveyorBlockEntity#tick lines 247-253).
        BlockPos otherPos = conveyor.offset(connection);
        if (conveyorAt(level, otherPos) == null) {
            return Step.WAITING; // sit at the end of the strand until the next conveyor loads
        }
        position = be.wrapAngle(stats.tangentAngle() + 180 + 2 * OFF_BRANCH * (be.reversed ? -1 : 1));
        cameFrom = connection.multiply(-1);
        conveyor = otherPos;
        connection = null;
        return Step.MOVED;
    }

    private Step advanceLoop(ChainConveyorBlockEntity be) {
        float prev = position;
        position = be.wrapAngle(position + degreesPerTick(be));
        if (stopAtLoopPort(be, prev)) {
            return Step.ARRIVED;
        }
        BlockPos exit = pickExit(be, prev);
        if (exit == null) {
            return Step.MOVED;
        }
        // Leave the wheel (ChainConveyorBlockEntity#tick lines 295-311).
        connection = exit;
        position = 0;
        flipped = be.reversed;
        cameFrom = null;
        return Step.MOVED;
    }

    /**
     * Which branch to take when its tangent angle is crossed. With an address, Create's routing table decides
     * (it only knows frogport filters); otherwise take the first branch that does not lead straight back,
     * unless the wheel is a dead end.
     */
    @Nullable
    private BlockPos pickExit(ChainConveyorBlockEntity be, float prev) {
        BlockPos routed = BlockPos.ZERO;
        if (!address.isBlank()) {
            routed = be.routingTable.getExitFor(addressStack());
        }
        for (BlockPos candidate : be.connections) {
            ConnectionStats stats = be.connectionStats.get(candidate);
            if (stats == null || !be.loopThresholdCrossed(position, prev, stats.tangentAngle())) {
                continue;
            }
            if (!routed.equals(BlockPos.ZERO)) {
                if (routed.equals(candidate)) {
                    return candidate;
                }
                continue;
            }
            if (candidate.equals(cameFrom) && be.connections.size() > 1) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    /** A dummy stack carrying our address, so PackageItem.matchAddress / routing tables can read it. */
    private ItemStack addressStack() {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(AllDataComponents.PACKAGE_ADDRESS, address);
        return stack;
    }

    /**
     * Stop at a frogport whose filter matches our address, the way packages are caught
     * (ChainConveyorBlockEntity#tick lines 217-242 for strands, 273-293 for the wheel). Frogports register
     * themselves into be.travelPorts / be.loopPorts every lazyTick (PackagePortTarget.ChainConveyorFrogportTarget#register).
     */
    private boolean stopAtStrandPort(ChainConveyorBlockEntity be, float prev) {
        if (address.isBlank()) {
            return false;
        }
        for (ConnectedPort port : be.travelPorts.values()) {
            if (connection.equals(port.connection()) && prev < port.chainPosition() && position >= port.chainPosition()
                    && PackageItem.matchAddress(address, port.filter())) {
                position = port.chainPosition();
                return true;
            }
        }
        return false;
    }

    private boolean stopAtLoopPort(ChainConveyorBlockEntity be, float prev) {
        if (address.isBlank()) {
            return false;
        }
        for (ConnectedPort port : be.loopPorts.values()) {
            if (be.loopThresholdCrossed(position, prev, port.chainPosition())
                    && PackageItem.matchAddress(address, port.filter())) {
                position = port.chainPosition();
                return true;
            }
        }
        return false;
    }

    // ---- persistence ----

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Conveyor", NbtUtils.writeBlockPos(conveyor));
        if (connection != null) {
            tag.put("Connection", NbtUtils.writeBlockPos(connection));
        }
        if (cameFrom != null) {
            tag.put("CameFrom", NbtUtils.writeBlockPos(cameFrom));
        }
        tag.putFloat("Position", position);
        tag.putBoolean("Flipped", flipped);
        tag.putString("Address", address);
        return tag;
    }

    public static ChainCursor load(CompoundTag tag) {
        BlockPos conveyor = NbtUtils.readBlockPos(tag, "Conveyor").orElse(BlockPos.ZERO);
        BlockPos connection = NbtUtils.readBlockPos(tag, "Connection").orElse(null);
        ChainCursor cursor = new ChainCursor(conveyor, connection, tag.getFloat("Position"), tag.getBoolean("Flipped"));
        cursor.cameFrom = NbtUtils.readBlockPos(tag, "CameFrom").orElse(null);
        cursor.address = tag.getString("Address");
        return cursor;
    }
}
