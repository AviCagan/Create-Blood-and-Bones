package com.avicagan.bloodandbones.bleeding;

import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Asks Create's encased fans whether their air current passes through a block. A fan's current is the box
 * {@link AirCurrent#bounds}: it starts at the block in front of the fan and runs {@link AirCurrent#maxDistance}
 * blocks along {@link AirCurrent#direction}, already cut short by solid blocks. The fan rebuilds it every
 * {@code fanBlockCheckRate} ticks (30 by default) and whenever its speed changes.
 */
public final class FanAirflow {
    /** Used when Create's server config is not loaded yet (e.g. a client before login). Create's default is 20. */
    private static final int FALLBACK_RANGE = 20;

    private FanAirflow() {
    }

    /** One fan whose current covers the block. */
    public record Hit(IAirCurrentSource fan, BlockPos fanPos, int distance, float speed, boolean pushing,
                      @Nullable FanProcessingType processing) {
    }

    /**
     * The speed in RPM (always 0 or more) of the fastest fan whose air current passes through {@code pos}, or 0
     * when no fan blows or pulls through it.
     */
    public static float fanSpeedAt(Level level, BlockPos pos) {
        Hit hit = strongestFanAt(level, pos);
        return hit == null ? 0 : hit.speed();
    }

    /** The fastest fan whose current covers {@code pos}, or null. */
    @Nullable
    public static Hit strongestFanAt(Level level, BlockPos pos) {
        int range = maxRange();
        AABB target = new AABB(pos);
        Hit best = null;
        for (Direction side : Direction.values()) {
            // walk away from pos; a fan found on this side must face back toward pos
            for (int i = 1; i <= range; i++) {
                BlockPos fanPos = pos.relative(side, i);
                if (!level.isLoaded(fanPos)) {
                    break; // never load chunks from here
                }
                BlockEntity be = level.getBlockEntity(fanPos);
                if (!(be instanceof IAirCurrentSource source) || source.isSourceRemoved()) {
                    continue;
                }
                AirCurrent current = source.getAirCurrent();
                float speed = Math.abs(source.getSpeed());
                if (current == null || speed == 0 || current.direction != side.getOpposite() || current.maxDistance <= 0) {
                    continue;
                }
                // bounds spans offsets [1, maxDistance + 1) in front of the fan; a partial block that stops the
                // air is inside it, a full block that stops it is not
                if (!current.bounds.intersects(target)) {
                    continue;
                }
                if (best == null || speed > best.speed()) {
                    best = new Hit(source, fanPos, i, speed, current.pushing, current.getTypeAt(i - 1));
                }
            }
        }
        return best;
    }

    private static int maxRange() {
        try {
            CKinetics kinetics = AllConfigs.server().kinetics;
            return Math.max(kinetics.fanPushDistance.get(), kinetics.fanPullDistance.get());
        } catch (IllegalStateException | NullPointerException notLoaded) {
            return FALLBACK_RANGE;
        }
    }
}
