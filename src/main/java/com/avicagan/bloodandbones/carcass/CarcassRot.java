package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.registry.BBTags;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3dc;
import plus.dragons.createdragonsplus.common.processing.freeze.BlockFreezer;
import plus.dragons.createdragonsplus.common.processing.freeze.FreezeCondition;

/**
 * Rot. Every carcass has a freshness from 1 (just killed) to 0 (rotten) that falls over the rig's
 * {@code rot_time}. Cold slows it, deep cold stops it: the biome's temperature, any block in the
 * {@code bloodandbones:chills} tag (ice, snow, and what Create: Dragons Plus calls a passive freezer) and any
 * block in {@code bloodandbones:preserves} (packed and blue ice) or a Dragons Plus freezer strong enough to
 * freeze things outright. Heat does the opposite. The torso's root cell is told the freshness now and then so
 * clients can tint the meat.
 */
public final class CarcassRot {
    /** Ticks between looks at the surroundings. */
    public static final int SAMPLE_INTERVAL = 20;
    /** Ticks between freshness updates sent to clients. */
    public static final int SYNC_INTERVAL = 100;
    /** Blocks around the torso that count as "near". */
    public static final int RADIUS = 2;
    /** Most game time an unloaded carcass catches up on at once: a day. */
    public static final long MAX_CATCH_UP = 24000L;

    public static final float CHILLED_RATE = 0.25F;
    public static final float COLD_BIOME_RATE = 0.4F;
    public static final float COOL_BIOME_RATE = 0.7F;
    public static final float HOT_BIOME_RATE = 1.5F;

    private CarcassRot() {
    }

    /** Called every tick from the torso's root cell, resting or not. */
    public static void tick(ServerLevel level, CarcassSavedData.Carcass carcass, ServerSubLevel torso) {
        if (carcass.isRotten()) {
            return;
        }
        Vector3dc position = torso.logicalPose().position();
        BlockPos center = BlockPos.containing(position.x(), position.y(), position.z());
        if (++carcass.rotSampleTicks >= SAMPLE_INTERVAL || carcass.rotSampleTicks < 0) {
            carcass.rotSampleTicks = 0;
            carcass.rotRate = rateAround(level, center);
        }
        long now = level.getGameTime();
        if (carcass.rotRate <= 0.0F) {
            // preserved: time passes without counting, and none of it is owed later
            carcass.rotClock = now;
            return;
        }
        int rotTime = RigManager.forEntity(carcass.entity).map(Rig::rotTime).orElse(Rig.DEFAULT_ROT_TIME);
        // count game time rather than ticks seen, so time spent in an unloaded chunk still rots the meat;
        // a long absence is paid off a day per tick rather than all at once
        long elapsed = carcass.rotClock < 0 ? 1L : Math.max(1L, Math.min(now - carcass.rotClock, MAX_CATCH_UP));
        carcass.rotClock = carcass.rotClock < 0 ? now : Math.min(now, carcass.rotClock + elapsed);
        float before = carcass.freshness;
        carcass.freshness = Math.max(0.0F, before - carcass.rotRate * elapsed / rotTime);
        boolean turnedRotten = carcass.freshness <= 0.0F;
        if (++carcass.rotSyncTicks >= SYNC_INTERVAL || turnedRotten) {
            carcass.rotSyncTicks = 0;
            sync(level, carcass, torso);
            CarcassSavedData.get(level).setDirty();
        }
        if (turnedRotten) {
            BloodAndBones.LOGGER.debug("Carcass {} has rotted", carcass.id);
        }
    }

    /** Tell clients the current freshness through every loaded limb's root cell. */
    public static void sync(ServerLevel level, CarcassSavedData.Carcass carcass, ServerSubLevel torso) {
        dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        for (java.util.UUID id : carcass.bones.values()) {
            dev.ryanhcode.sable.sublevel.SubLevel subLevel = container == null ? null : container.getSubLevel(id);
            if (!(subLevel instanceof ServerSubLevel limb) || limb.isRemoved()) {
                continue;
            }
            BlockPos root = limb.getPlot().getCenterBlock();
            if (level.getBlockEntity(root) instanceof CarcassPartBlockEntity be && Math.abs(be.freshness() - carcass.freshness) > 1.0E-4F) {
                be.setFreshness(carcass.freshness);
                be.setChanged();
                level.sendBlockUpdated(root, level.getBlockState(root), level.getBlockState(root), Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * Rot speed multiplier for a carcass whose torso is at {@code center}: 0 when preserved, otherwise
     * the biome's contribution, quartered when something chilling is within reach.
     */
    public static float rateAround(ServerLevel level, BlockPos center) {
        boolean chilled = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.is(BBTags.PRESERVES)) {
                        return 0.0F;
                    }
                    float freeze = BlockFreezer.findFreeze(level, cursor, state);
                    if (freeze != BlockFreezer.NO_FREEZE && FreezeCondition.FROZEN.testFreezer(freeze)) {
                        return 0.0F;
                    }
                    if (state.is(BBTags.CHILLS) || (freeze != BlockFreezer.NO_FREEZE && FreezeCondition.PASSIVE.testFreezer(freeze))) {
                        chilled = true;
                    }
                }
            }
        }
        float rate = biomeRate(level.getBiome(center).value(), center);
        return chilled ? rate * CHILLED_RATE : rate;
    }

    /**
     * Snowy places rot slowly (the biome's own "cold enough to snow here" rule, which knows about altitude
     * and frozen-ocean patches), cool ones a little slower than temperate, deserts and the Nether fast.
     */
    public static float biomeRate(Biome biome, BlockPos pos) {
        float temperature = biome.getBaseTemperature();
        if (biome.coldEnoughToSnow(pos)) {
            return COLD_BIOME_RATE;
        }
        if (temperature < 0.5F) {
            return COOL_BIOME_RATE;
        }
        if (temperature >= 1.5F) {
            return HOT_BIOME_RATE;
        }
        return 1.0F;
    }
}
