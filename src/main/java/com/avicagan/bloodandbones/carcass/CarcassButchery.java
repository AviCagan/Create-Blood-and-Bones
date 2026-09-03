package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.BloodAndBones;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;

/**
 * Taking a carcass apart. A Cleaver cut on a limb wounds it; enough cuts sever the joint to the body and
 * the limb becomes a body of its own, still part of the carcass record so it can be hooked and dragged.
 */
public final class CarcassButchery {
    /** Cleaver cuts it takes to get through a joint. */
    public static final int CUTS_TO_SEVER = 3;

    private CarcassButchery() {
    }

    /**
     * One cut on {@code bone} at {@code hitWorld}.
     *
     * @return true if the cut did anything
     */
    public static boolean cut(ServerLevel level, @Nullable Player player, CarcassSavedData.Carcass carcass, String bone, @Nullable Vector3d hitWorld) {
        if (carcass.resting) {
            if (CarcassRest.split(level, carcass) == null) {
                return false;
            }
        }
        if (bone.equals(carcass.rootBone) || carcass.severed.contains(bone) || !carcass.bones.containsKey(bone)) {
            return false;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        UUID id = carcass.bones.get(bone);
        Vector3d at = hitWorld;
        if (at == null && container != null && container.getSubLevel(id) instanceof ServerSubLevel limb && !limb.isRemoved()) {
            at = new Vector3d(limb.logicalPose().position());
        }
        int cuts = carcass.cuts.merge(bone, 1, Integer::sum);
        if (at != null) {
            Blood.burst(level, at, 8);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 0.7F);
        }
        if (cuts >= CUTS_TO_SEVER) {
            sever(level, carcass, bone, at);
        }
        CarcassSavedData.get(level).setDirty();
        return true;
    }

    /** Cut the joint between a limb and its parent for good. */
    public static void sever(ServerLevel level, CarcassSavedData.Carcass carcass, String bone, @Nullable Vector3d at) {
        carcass.joints.removeIf(joint -> joint.child().equals(bone));
        carcass.severed.add(bone);
        carcass.cuts.remove(bone);
        // live joints are not tied to their specs; drop them all and let the root tick rebuild the survivors
        for (PhysicsConstraintHandle handle : carcass.liveJoints) {
            if (handle.isValid()) {
                handle.remove();
            }
        }
        carcass.liveJoints.clear();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            for (UUID id : carcass.bones.values()) {
                if (container.getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved()) {
                    container.physicsSystem().getPipeline().wakeUp(body);
                }
            }
        }
        if (at != null) {
            Blood.burst(level, at, 30);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
        CarcassSavedData.get(level).setDirty();
        BloodAndBones.LOGGER.debug("Severed {} from carcass {}", bone, carcass.id);
    }

    /** How many joints a carcass still has to hold it together. */
    public static int intactJoints(CarcassSavedData.Carcass carcass) {
        return carcass.joints.size();
    }

    static int cutsOn(CarcassSavedData.Carcass carcass, String bone) {
        return carcass.cuts.getOrDefault(bone, 0);
    }

    static Map<String, Integer> cuts(CarcassSavedData.Carcass carcass) {
        return carcass.cuts;
    }
}
