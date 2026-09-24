package com.avicagan.bloodandbones.carcass.trolley;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectionStats;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Pick of the chain strand a player is looking at, on either side (the client asks too, so it does not
 * place a held block behind the chain). Create's own picker (ChainConveyorInteractionHandler) is client-only
 * and only active for chain-rideable items, frogports and packages, and
 * ChainConveyorShape.ChainConveyorOBB#connection is package-private, so we redo the math.
 */
public final class ChainPicker {
    /** Chain strands are thin; Create's OBB uses radius 0.175 (ChainConveyorShape.ChainConveyorOBB#radius). */
    private static final double PICK_RADIUS = 0.3;

    public record Hit(BlockPos conveyor, BlockPos connection, float position, double distance) {
    }

    private ChainPicker() {
    }

    @Nullable
    public static Hit pick(Level level, Player player, double reach) {
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        int maxLength = AllConfigs.server().kinetics.maxChainConveyorLength.get();
        int radius = Mth.ceil((maxLength + reach) / 16.0);
        ChunkPos center = player.chunkPosition();
        Hit best = null;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof ChainConveyorBlockEntity be)) {
                        continue;
                    }
                    be.prepareStats();
                    for (BlockPos connection : be.connections) {
                        ConnectionStats stats = be.connectionStats.get(connection);
                        if (stats == null) {
                            continue;
                        }
                        Hit hit = closest(be.getBlockPos(), connection, stats, from, look, reach);
                        if (hit != null && (best == null || hit.distance() < best.distance())) {
                            best = hit;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Closest approach between the look ray and one strand segment. */
    @Nullable
    private static Hit closest(BlockPos conveyor, BlockPos connection, ConnectionStats stats, Vec3 from, Vec3 look, double reach) {
        Vec3 a = stats.start();
        Vec3 segment = stats.end().subtract(a);
        double length = segment.length();
        if (length < 1.0e-4) {
            return null;
        }
        Vec3 u = segment.scale(1 / length);
        Vec3 w = from.subtract(a);
        double b = u.dot(look);
        double d = u.dot(w);
        double e = look.dot(w);
        double denom = 1 - b * b;
        if (denom < 1.0e-6) {
            return null; // looking along the chain
        }
        double s = Mth.clamp((d - b * e) / denom, 0, length); // along the chain
        double t = look.dot(a.add(u.scale(s)).subtract(from)); // along the ray
        if (t < 0 || t > reach) {
            return null;
        }
        Vec3 onChain = a.add(u.scale(s));
        Vec3 onRay = from.add(look.scale(t));
        if (onChain.distanceToSqr(onRay) > PICK_RADIUS * PICK_RADIUS) {
            return null;
        }
        return new Hit(conveyor, connection, (float) s, t);
    }
}
