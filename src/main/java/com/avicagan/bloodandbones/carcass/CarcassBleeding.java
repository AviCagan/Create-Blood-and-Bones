package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity;
import com.avicagan.bloodandbones.bleeding.FanAirflow;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBTags;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Draining a body. A fresh carcass holds blood in proportion to its weight. Hung on a Shackle Hook it
 * bleeds from its lowest point; lying still on (or just above) a Bleeding Rack it bleeds into the tray.
 * What falls on a rack is kept, what falls anywhere else is lost. Air from an encased fan blowing across
 * the body speeds it up. A bled carcass keeps longer.
 */
public final class CarcassBleeding {
    /** Blood per unit of rig weight, mB: a cow holds about a bucket. */
    public static final float BLOOD_PER_WEIGHT = 1000.0F;
    /** Seconds a hanging body takes to bleed out with no fan. */
    public static final int BLEED_SECONDS = 40;
    /** Lying on a rack is slower than hanging over one. */
    public static final float LYING_RATE = 0.5F;
    /** Ticks between bleed steps. */
    public static final int INTERVAL = 10;
    /** How far below a hanging body a rack still catches the blood. */
    public static final int HANGING_REACH = 8;
    /** How far below a lying body a rack must be. */
    public static final int LYING_REACH = 1;
    /** Fan speed that doubles the rate; the boost is capped at four times. */
    public static final float FAN_RPM_PER_DOUBLING = 64.0F;
    public static final float MAX_FAN_BOOST = 4.0F;
    /** Rot speed of a bled carcass, as a share of an unbled one's. */
    public static final float BLED_ROT = 0.7F;
    /** Below this freshness the blood has clotted and nothing drains. */
    public static final float CLOTTED = 0.3F;

    private CarcassBleeding() {
    }

    /** Blood the whole animal held at death; skeletons and severed pieces have none. */
    public static float capacity(CarcassSavedData.Carcass carcass) {
        Rig rig = RigManager.forCarcass(carcass).orElse(null);
        if (rig == null || !carcass.rootBone.equals(rig.root().name())) {
            return 0.0F;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(carcass.entity);
        if (type.is(BBTags.BLOODLESS)) {
            return 0.0F;
        }
        return Math.round(rig.weight() * BLOOD_PER_WEIGHT);
    }

    /** Fill in blood for a record that has never had it worked out. */
    public static void ensureBlood(CarcassSavedData.Carcass carcass) {
        if (carcass.bloodMax < 0.0F) {
            carcass.bloodMax = capacity(carcass);
            carcass.blood = carcass.bloodMax;
        }
        if (carcass.blood < 0.0F) {
            carcass.blood = carcass.bloodMax;
        }
    }

    /** How long a fresh cut pours, in ticks: wherever the body lies, rack or no rack. */
    public static final int GUSH_TICKS = 300;
    /** Share of the body's blood a stump loses each bleed step while it pours. */
    public static final float GUSH_SHARE = 0.01F;

    /** A joint was just cut through: the end of it this record holds pours for a while. */
    public static void freshCut(CarcassSavedData.Carcass carcass, String parent, String child) {
        carcass.gushing.put(parent + ">" + child, GUSH_TICKS);
    }

    /** What drains out of this carcass: Soul Blood from a nether mob, blood from any other. */
    public static net.minecraft.world.level.material.Fluid fluidOf(CarcassSavedData.Carcass carcass) {
        boolean soul = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(carcass.entity)
                .map(type -> type.is(com.avicagan.bloodandbones.registry.BBTags.SOUL_BLEEDERS)).orElse(false);
        return soul ? BBFluids.soulBlood() : BBFluids.blood();
    }

    /** Called every tick from the torso's root cell. */
    public static void tick(ServerLevel level, CarcassSavedData.Carcass carcass, ServerSubLevel torso) {
        if (++carcass.bleedTicks < INTERVAL) {
            return;
        }
        carcass.bleedTicks = 0;
        ensureBlood(carcass);
        gush(level, carcass);
        if (carcass.blood <= 0.0F || carcass.freshness < CLOTTED) {
            return;
        }
        boolean hanging = ShackleHookBlockEntity.isHanging(level, carcass.id)
                || com.avicagan.bloodandbones.carcass.trolley.ShackleTrolleyEntity.isHanging(level, carcass.id);
        if (!hanging && !carcass.resting) {
            // a body still moving about has not settled onto anything
            return;
        }
        Vector3d drip = lowestPoint(carcass, torso);
        BleedingRackBlockEntity rack = rackBelow(level, drip, hanging ? HANGING_REACH : LYING_REACH, fluidOf(carcass));
        if (rack == null && !hanging) {
            return;
        }
        float perTick = carcass.bloodMax / (BLEED_SECONDS * 20.0F) * (hanging ? 1.0F : LYING_RATE);
        BlockPos body = BlockPos.containing(torso.logicalPose().position().x(), torso.logicalPose().position().y(), torso.logicalPose().position().z());
        float fan = level.isLoaded(body) ? FanAirflow.fanSpeedAt(level, body) : 0.0F;
        float boost = Math.min(MAX_FAN_BOOST, 1.0F + fan / FAN_RPM_PER_DOUBLING);
        int amount = (int) Math.ceil(Math.min(carcass.blood, perTick * INTERVAL * boost));
        if (amount <= 0) {
            return;
        }
        if (rack != null) {
            int taken = rack.collect(new FluidStack(fluidOf(carcass), amount), IFluidHandler.FluidAction.EXECUTE);
            if (taken <= 0) {
                // the tray is full: the blood waits in the body until the rack is emptied
                return;
            }
            amount = taken;
        }
        carcass.blood = Math.max(0.0F, carcass.blood - amount);
        Blood.drip(level, drip, Blood.soul(carcass));
        if (hanging) {
            Blood.drip(level, drip, Blood.soul(carcass));
        }
        if (rack == null) {
            // nothing catches it: it pools on the floor below
            Blood.stain(level, drip, 1, Blood.soul(carcass));
        }
        CarcassSavedData.get(level).setDirty();
    }

    /** Fresh cuts pour onto whatever is below them, the stump draining the body as it goes. */
    private static void gush(ServerLevel level, CarcassSavedData.Carcass carcass) {
        if (carcass.gushing.isEmpty()) {
            return;
        }
        Rig rig = RigManager.forCarcass(carcass).orElse(null);
        if (rig == null || !Blood.bloody(carcass) || carcass.freshness < CLOTTED) {
            carcass.gushing.clear();
            return;
        }
        var iterator = carcass.gushing.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            int left = entry.getValue() - INTERVAL;
            if (left <= 0) {
                iterator.remove();
                continue;
            }
            entry.setValue(left);
            int split = entry.getKey().indexOf('>');
            String parent = entry.getKey().substring(0, split);
            String child = entry.getKey().substring(split + 1);
            Vector3d at = woundPoint(level, carcass, rig, parent, child);
            if (at == null) {
                continue;
            }
            Blood.drip(level, at, Blood.soul(carcass));
            if (left > GUSH_TICKS / 2) {
                Blood.drip(level, at, Blood.soul(carcass));
            }
            // a stump drains the body; what pours onto a rack is kept, the rest stains the floor
            BleedingRackBlockEntity rack = rackBelow(level, at, HANGING_REACH, fluidOf(carcass));
            if (carcass.bones.containsKey(parent) && carcass.blood > 0.0F) {
                int amount = (int) Math.ceil(Math.min(carcass.blood, carcass.bloodMax * GUSH_SHARE));
                if (rack != null) {
                    amount = rack.collect(new FluidStack(fluidOf(carcass), amount), IFluidHandler.FluidAction.EXECUTE);
                }
                carcass.blood = Math.max(0.0F, carcass.blood - amount);
                CarcassSavedData.get(level).setDirty();
            }
            if (rack == null && (left / INTERVAL) % 4 == 0) {
                Blood.stain(level, at, 1, Blood.soul(carcass));
            }
        }
    }

    /**
     * Where a cut is, in the world: on the stump (the child's pivot in the parent's frame) if this record
     * holds the parent, else on the piece's own cut end (its pivot). Null while that body is not loaded.
     */
    @Nullable
    private static Vector3d woundPoint(ServerLevel level, CarcassSavedData.Carcass carcass, Rig rig, String parent, String child) {
        Bone parentBone = rig.bone(parent).orElse(null);
        Bone childBone = rig.bone(child).orElse(null);
        boolean stump = carcass.bones.containsKey(parent);
        java.util.UUID id = carcass.bones.get(stump ? parent : child);
        dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (parentBone == null || childBone == null || id == null || container == null
                || !(container.getSubLevel(id) instanceof ServerSubLevel body) || body.isRemoved()) {
            return null;
        }
        BlockPos center = body.getPlot().getCenterBlock();
        Vector3d plot = CarcassAssembler.originOffset(stump ? parentBone : childBone).add(center.getX(), center.getY(), center.getZ());
        if (stump) {
            // the child's pivot in the parent's frame, as the joint had it
            Vector3d relative = new Vector3d(childBone.offset()).sub(new Vector3d(parentBone.offset()));
            new org.joml.Quaterniond(parentBone.rotation()).invert().transform(relative);
            plot.add(relative.div(16.0));
        }
        return body.logicalPose().transformPosition(plot);
    }

    /** The torso box corner lowest in the world, where blood gathers and falls from. */
    public static Vector3d lowestPoint(CarcassSavedData.Carcass carcass, ServerSubLevel torso) {
        Rig rig = RigManager.forCarcass(carcass).orElse(null);
        Pose3d pose = torso.logicalPose();
        if (rig == null) {
            return new Vector3d(pose.position());
        }
        Bone bone = rig.bone(carcass.rootBone).orElse(rig.root());
        BlockPos center = torso.getPlot().getCenterBlock();
        Vector3d min = new Vector3d(bone.boxMin()).div(16.0).add(CarcassAssembler.originOffset(bone)).add(center.getX(), center.getY(), center.getZ());
        Vector3d max = new Vector3d(bone.boxMax()).div(16.0).add(CarcassAssembler.originOffset(bone)).add(center.getX(), center.getY(), center.getZ());
        Vector3d lowest = null;
        for (int i = 0; i < 8; i++) {
            Vector3d c = new Vector3d((i & 1) == 0 ? min.x : max.x, (i & 2) == 0 ? min.y : max.y, (i & 4) == 0 ? min.z : max.z);
            pose.transformPosition(c);
            if (lowest == null || c.y < lowest.y) {
                lowest = c;
            }
        }
        return lowest;
    }

    /**
     * The Bleeding Rack the blood lands in: going down from the drip point, the first rack in that column or
     * the eight around it (a tray catches a little wide), no lower than the first solid thing straight below.
     */
    @Nullable
    public static BleedingRackBlockEntity rackBelow(ServerLevel level, Vector3d from, int reach) {
        return rackBelow(level, from, reach, null);
    }

    /**
     * The nearest rack under a point that can take this fluid: empty, or holding the same (a rack holds
     * one fluid, so a tray of blood is passed over for a hoglin's Soul Blood, and one beside it tried).
     * When the only racks at that level hold the other fluid, the one under it is returned anyway: it
     * takes nothing, like a full tray, so the blood waits in the body instead of draining into nothing.
     */
    public static BleedingRackBlockEntity rackBelow(ServerLevel level, Vector3d from, int reach,
                                                    @org.jetbrains.annotations.Nullable net.minecraft.world.level.material.Fluid fluid) {
        java.util.function.Predicate<BleedingRackBlockEntity> takes = rack -> fluid == null
                || rack.getFluid().isEmpty() || rack.getFluid().getFluid().isSame(fluid);
        BlockPos start = BlockPos.containing(from.x, from.y, from.z);
        for (int i = 0; i <= reach; i++) {
            BlockPos pos = start.below(i);
            if (!level.isLoaded(pos)) {
                return null;
            }
            BleedingRackBlockEntity refused = null;
            if (level.getBlockEntity(pos) instanceof BleedingRackBlockEntity rack) {
                if (takes.test(rack)) {
                    return rack;
                }
                refused = rack;
            }
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                for (int corner = 0; corner < 2; corner++) {
                    BlockPos around = corner == 0 ? pos.relative(side) : pos.relative(side).relative(side.getClockWise());
                    if (level.isLoaded(around) && level.getBlockEntity(around) instanceof BleedingRackBlockEntity rack) {
                        if (takes.test(rack)) {
                            return rack;
                        }
                        refused = refused == null ? rack : refused;
                    }
                }
            }
            if (refused != null) {
                return refused;
            }
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return null;
            }
        }
        return null;
    }
}
