package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.butchery.ButcheryManager;
import com.avicagan.bloodandbones.config.BBServerConfig;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.registry.BBTags;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3d;
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
 * clients can tint the meat. Once rotten it keeps going off, and after a while (a day by default, see
 * {@link BBServerConfig}) it falls apart into bones and a little rotten flesh.
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
        Vector3dc position = torso.logicalPose().position();
        BlockPos center = BlockPos.containing(position.x(), position.y(), position.z());
        if (++carcass.rotSampleTicks >= SAMPLE_INTERVAL || carcass.rotSampleTicks < 0) {
            carcass.rotSampleTicks = 0;
            carcass.rotRate = rateAround(level, center) * (carcass.isBled() ? CarcassBleeding.BLED_ROT : 1.0F);
        }
        long now = level.getGameTime();
        float rate = carcass.rotRate * BBServerConfig.rotSpeed();
        if (rate <= 0.0F) {
            // preserved: time passes without counting, and none of it is owed later
            carcass.rotClock = now;
            if (carcass.isRotten() && ++carcass.rotSyncTicks >= SYNC_INTERVAL) {
                carcass.rotSyncTicks = 0;
                sync(level, carcass, torso);
            }
            return;
        }
        // count game time rather than ticks seen, so time spent in an unloaded chunk still rots the meat;
        // a long absence is paid off a day per tick rather than all at once
        long elapsed = carcass.rotClock < 0 ? 1L : Math.max(1L, Math.min(now - carcass.rotClock, MAX_CATCH_UP));
        carcass.rotClock = carcass.rotClock < 0 ? now : Math.min(now, carcass.rotClock + elapsed);
        if (carcass.isRotten()) {
            // nothing left to rot, but it keeps going off until it falls apart; limbs that load later still
            // need telling what they look like
            carcass.decay += rate * elapsed;
            if (++carcass.rotSyncTicks >= SYNC_INTERVAL) {
                carcass.rotSyncTicks = 0;
                sync(level, carcass, torso);
                CarcassSavedData.get(level).setDirty();
            }
            if (BBServerConfig.crumble() && carcass.decay >= BBServerConfig.crumbleTicks()) {
                CarcassSavedData.get(level).crumbling.add(carcass.id);
            }
            return;
        }
        int rotTime = RigManager.forEntity(carcass.entity).map(Rig::rotTime).orElse(Rig.DEFAULT_ROT_TIME);
        float before = carcass.freshness;
        carcass.freshness = Math.max(0.0F, before - rate * elapsed / rotTime);
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

    /** Share of what a fresh butchering would give that a carcass leaves when it falls apart (before rot). */
    public static final float CRUMBLE_SHARE = 0.5F;

    /**
     * The rig's joints that touch this record but no longer hold, as "parent>child": a limb cut off (its
     * stump on the parent), a piece cut from its parent (its own cut end), or one butchered away.
     */
    public static java.util.List<String> cuts(CarcassSavedData.Carcass carcass) {
        Rig rig = RigManager.forEntity(carcass.entity).orElse(null);
        if (rig == null) {
            return java.util.List.of();
        }
        java.util.Set<String> here = pieces(carcass);
        java.util.List<String> cuts = new java.util.ArrayList<>();
        for (com.avicagan.bloodandbones.carcass.rig.Bone bone : rig.bones()) {
            String parent = bone.parent().orElse(null);
            if (parent == null || (!here.contains(parent) && !here.contains(bone.name()))) {
                continue;
            }
            boolean held = here.contains(parent) && here.contains(bone.name())
                    && carcass.joints.stream().anyMatch(joint -> joint.parent().equals(parent) && joint.child().equals(bone.name()));
            if (!held) {
                cuts.add(parent + ">" + bone.name());
            }
        }
        return cuts;
    }

    /** Every piece still part of the carcass: its own bodies and, while resting, the limbs folded into the torso. */
    public static java.util.Set<String> pieces(CarcassSavedData.Carcass carcass) {
        java.util.Set<String> pieces = new java.util.LinkedHashSet<>(carcass.bones.keySet());
        if (carcass.resting) {
            pieces.addAll(carcass.restPoses.keySet());
        }
        return pieces;
    }

    /** End of the level tick: carcasses whose rot is done fall apart. */
    public static void levelTick(ServerLevel level) {
        CarcassSavedData data = CarcassSavedData.get(level);
        if (data.crumbling.isEmpty()) {
            return;
        }
        for (java.util.UUID id : java.util.List.copyOf(data.crumbling)) {
            data.crumbling.remove(id);
            CarcassSavedData.Carcass carcass = data.carcass(id);
            if (carcass != null) {
                crumble(level, carcass);
            }
        }
    }

    /**
     * The carcass falls apart where it lies: every piece leaves half its bones and a little rotten flesh,
     * and the bodies go. Waits (returns false) while any piece is in an unloaded chunk.
     */
    public static boolean crumble(ServerLevel level, CarcassSavedData.Carcass carcass) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return false;
        }
        java.util.Map<String, ServerSubLevel> bodies = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, java.util.UUID> bone : carcass.bones.entrySet()) {
            if (container.getSubLevel(bone.getValue()) instanceof ServerSubLevel body && !body.isRemoved()) {
                bodies.put(bone.getKey(), body);
            }
        }
        ServerSubLevel torso = bodies.get(carcass.rootBone);
        // resting, the limbs are folded into the torso's body; otherwise each must be here to be cleared away
        if (torso == null || (!carcass.resting && bodies.size() < carcass.bones.size())) {
            return false;
        }
        var table = ButcheryManager.forEntity(carcass.entity);
        for (String bone : pieces(carcass)) {
            Vector3d at = new Vector3d(bodies.getOrDefault(bone, torso).logicalPose().position());
            table.ifPresent(t -> CarcassButchery.dropYields(level, carcass, t.part(bone), CRUMBLE_SHARE, at));
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.ROTTEN_FLESH)),
                    at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.08);
            level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 3, 0.15, 0.1, 0.15, 0.02);
        }
        Vector3dc middle = torso.logicalPose().position();
        level.playSound(null, middle.x(), middle.y(), middle.z(), SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.playSound(null, middle.x(), middle.y(), middle.z(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 0.8F);
        // forget the record first, so taking the bodies away does not split what is left into new ones
        CarcassSavedData.get(level).forget(carcass);
        CarcassRest.unlock(carcass);
        for (dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle handle : carcass.liveJoints) {
            if (handle.isValid()) {
                handle.remove();
            }
        }
        carcass.liveJoints.clear();
        for (ServerSubLevel body : bodies.values()) {
            container.removeSubLevel(body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        }
        BloodAndBones.LOGGER.debug("Carcass {} of {} fell apart", carcass.id, carcass.entity);
        return true;
    }

    /** Tell clients the current freshness, look and cut joints through every loaded limb's root cell. */
    public static void sync(ServerLevel level, CarcassSavedData.Carcass carcass, ServerSubLevel torso) {
        java.util.List<String> cuts = cuts(carcass);
        dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        for (java.util.UUID id : carcass.bones.values()) {
            dev.ryanhcode.sable.sublevel.SubLevel subLevel = container == null ? null : container.getSubLevel(id);
            if (!(subLevel instanceof ServerSubLevel limb) || limb.isRemoved()) {
                continue;
            }
            BlockPos root = limb.getPlot().getCenterBlock();
            if (!(level.getBlockEntity(root) instanceof CarcassPartBlockEntity be) || !be.isRoot()) {
                continue;
            }
            boolean changed = false;
            if (Math.abs(be.freshness() - carcass.freshness) > 1.0E-4F) {
                be.setFreshness(carcass.freshness);
                changed = true;
            }
            // the look too: a skinned carcass whose limb was unloaded while it was skinned
            if (!be.look().equals(carcass.look)) {
                be.setLook(carcass.look);
                changed = true;
            }
            if (be.setCuts(cuts)) {
                changed = true;
            }
            if (changed) {
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
