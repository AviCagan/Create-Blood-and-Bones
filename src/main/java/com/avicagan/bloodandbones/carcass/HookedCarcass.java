package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;

/**
 * A carcass hung on a Shackle Hook that a Create contraption picks up (ARCHITECTURE 3.5). Create can carry nothing on a
 * contraption block but the block's data, so while the hook moves the carcass is kept in that data and its bodies leave
 * the world: every piece's pose relative to the torso (as the resting form keeps them), the torso's own turn, and where
 * on it the hook holds it. The hook's actor draws it hanging there as it goes (ShackleHookMovement), and when the
 * contraption is set down the hook hangs it again, every piece where it was and joined as it was, the same record.
 */
public final class HookedCarcass {
    private HookedCarcass() {
    }

    /**
     * Take a hung carcass off the world into a tag: the record as it is saved, with its bodies' ids dropped and every
     * piece but the torso as a rest pose, and the torso's turn in the world. What is drawn while it moves goes in "Draw".
     *
     * @param anchorPlot where the hook holds the torso, in its plot
     * @return the tag, or null if the carcass is not whole in the world (then it is left alone)
     */
    @Nullable
    public static CompoundTag pack(ServerLevel level, CarcassSavedData.Carcass carcass, Vector3d anchorPlot) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        Rig rig = RigManager.forCarcass(carcass).orElse(null);
        if (container == null || rig == null) {
            return null;
        }
        Bone torsoBone = rig.bone(carcass.rootBone).orElse(rig.root());
        if (!(container.getSubLevel(carcass.bones.get(torsoBone.name())) instanceof ServerSubLevel torso) || torso.isRemoved()) {
            return null;
        }
        Pose3d torsoPose = torso.logicalPose();
        Vector3d torsoOriginPlot = CarcassAssembler.boneOriginInPlot(torso, torsoBone);
        Vector3d torsoOriginWorld = torsoPose.transformPosition(torsoOriginPlot, new Vector3d());
        Quaterniond torsoInverse = new Quaterniond(torsoPose.orientation()).invert();
        ListTag poses = new ListTag();
        if (carcass.resting) {
            carcass.restPoses.forEach((name, pose) -> poses.add(named(pose, name)));
        }
        for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
            Bone bone = rig.bone(entry.getKey()).orElse(null);
            if (bone == null || bone == torsoBone || !(container.getSubLevel(entry.getValue()) instanceof ServerSubLevel limb) || limb.isRemoved()) {
                continue;
            }
            // the piece's bone frame relative to the torso's, as CarcassRest#rest remembers a folded limb
            Pose3d limbPose = limb.logicalPose();
            Vector3d originWorld = limbPose.transformPosition(CarcassAssembler.boneOriginInPlot(limb, bone), new Vector3d());
            poses.add(named(new CarcassSavedData.RestPose(torsoInverse.transform(originWorld.sub(torsoOriginWorld)),
                    new Quaterniond(torsoInverse).mul(limbPose.orientation())), bone.name()));
        }
        CompoundTag record = carcass.save();
        record.put("Bones", new ListTag());
        record.put("RestPoses", poses);
        record.putBoolean("Resting", true);
        record.put("RestCells", new ListTag());
        CompoundTag tag = new CompoundTag();
        tag.put("Record", record);
        putQuaternion(tag, "Turn", torsoPose.orientation());
        // where the hook holds it, in the torso bone's own frame (the plot's axes are the bone's)
        putVector(tag, "Held", new Vector3d(anchorPlot).sub(torsoOriginPlot));
        CompoundTag draw = drawn(carcass, poses);
        draw.put("Turn", tag.get("Turn").copy());
        draw.put("Held", tag.get("Held").copy());
        tag.put("Draw", draw);
        return tag;
    }

    /** What the actor needs to draw it: the mob, its look and rot, its cut ends and every piece's pose. */
    private static CompoundTag drawn(CarcassSavedData.Carcass carcass, ListTag poses) {
        CompoundTag draw = new CompoundTag();
        draw.putString("Entity", carcass.entity.toString());
        draw.putString("Root", carcass.rootBone);
        draw.putBoolean("Baby", carcass.baby);
        draw.putFloat("Freshness", carcass.freshness);
        draw.putString("Texture", carcass.look.texture().toString());
        ListTag coats = new ListTag();
        for (CarcassLook.Coat coat : carcass.look.passes()) {
            CompoundTag c = new CompoundTag();
            c.putString("Layer", coat.layer());
            c.putString("Texture", coat.texture().toString());
            c.putInt("Tint", coat.tint());
            coats.add(c);
        }
        draw.put("Coats", coats);
        ListTag cuts = new ListTag();
        for (String cut : CarcassRot.cuts(carcass)) {
            cuts.add(StringTag.valueOf(cut));
        }
        draw.put("Cuts", cuts);
        draw.put("Pieces", poses.copy());
        return draw;
    }

    /**
     * Hang a packed carcass on a hook again: its torso made anew with its turn, held at {@code tip}, and every other
     * piece made at its pose and joined to it (CarcassRest#split, as a resting carcass unfolds).
     *
     * @param tip where the hook holds it now, in the world
     * @return the carcass hanging again, or null if it could not be made (no room; it is lost, and said so in the log)
     */
    @Nullable
    public static Hung unpack(ServerLevel level, CompoundTag tag, Vector3d tip) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        CarcassSavedData.Carcass carcass = CarcassSavedData.Carcass.load(tag.getCompound("Record"));
        Rig rig = RigManager.forCarcass(carcass).orElse(null);
        if (container == null || rig == null) {
            BloodAndBones.LOGGER.warn("Could not hang packed carcass {} of {} again: no rig", carcass.id, carcass.entity);
            return null;
        }
        Bone torsoBone = rig.bone(carcass.rootBone).orElse(rig.root());
        BlockPos staging = CarcassAssembler.findStaging(level, BlockPos.containing(tip.x, tip.y, tip.z), rig);
        ServerSubLevel torso = staging == null ? null : CarcassAssembler.assembleBone(level, staging, carcass.id, rig, torsoBone, carcass.look);
        if (torso == null) {
            BloodAndBones.LOGGER.warn("Could not hang packed carcass {} of {} again: no room", carcass.id, carcass.entity);
            return null;
        }
        carcass.bones.clear();
        carcass.bones.put(torsoBone.name(), torso.getUniqueId());
        CarcassSavedData.get(level).add(carcass);
        Quaterniond orientation = quaternion(tag, "Turn");
        Vector3d held = vector(tag, "Held");
        // the torso's bone origin, set so the point the hook holds lands on the tip
        Vector3d origin = new Vector3d(tip).sub(orientation.transform(new Vector3d(held)));
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        CarcassAssembler.pose(pipeline, torso, torsoBone, origin, orientation);
        if (level.getBlockEntity(torso.getPlot().getCenterBlock()) instanceof CarcassPartBlockEntity root) {
            root.setFreshness(carcass.freshness);
        }
        if (CarcassRest.split(level, carcass) == null) {
            // no room for the rest of it: the torso alone hangs, and its pieces are gone
            BloodAndBones.LOGGER.warn("Could not unfold packed carcass {}; it hangs as its torso alone", carcass.id);
            carcass.restPoses.clear();
            carcass.joints.clear();
            carcass.resting = false;
            CarcassRot.sync(level, carcass, null);
        }
        Vector3d anchor = new Vector3d(held).add(CarcassAssembler.boneOriginInPlot(torso, torsoBone));
        return new Hung(carcass, torso, anchor);
    }

    /** A carcass hung again: its record, its torso, and where on the torso's plot the hook holds it. */
    public record Hung(CarcassSavedData.Carcass carcass, ServerSubLevel torso, Vector3d anchorPlot) {
    }

    /** Turn a packed carcass's pose by {@code turn} (a contraption set down turned), as its hook turns. */
    public static void turned(CompoundTag tag, Quaterniond turn) {
        putQuaternion(tag, "Turn", new Quaterniond(turn).mul(quaternion(tag, "Turn")));
        if (tag.contains("Draw")) {
            tag.getCompound("Draw").put("Turn", tag.get("Turn").copy());
        }
    }

    /** The carcass's id in a packed tag, or null. */
    @Nullable
    public static UUID id(CompoundTag tag) {
        CompoundTag record = tag.getCompound("Record");
        return record.hasUUID("Id") ? record.getUUID("Id") : null;
    }

    private static CompoundTag named(CarcassSavedData.RestPose pose, String name) {
        CompoundTag r = pose.save();
        r.putString("Name", name);
        return r;
    }

    static void putQuaternion(CompoundTag tag, String key, org.joml.Quaterniondc q) {
        ListTag list = new ListTag();
        list.add(net.minecraft.nbt.DoubleTag.valueOf(q.x()));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(q.y()));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(q.z()));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(q.w()));
        tag.put(key, list);
    }

    public static Quaterniond quaternion(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_DOUBLE);
        return list.size() == 4 ? new Quaterniond(list.getDouble(0), list.getDouble(1), list.getDouble(2), list.getDouble(3)).normalize() : new Quaterniond();
    }

    static void putVector(CompoundTag tag, String key, Vector3d v) {
        ListTag list = new ListTag();
        list.add(net.minecraft.nbt.DoubleTag.valueOf(v.x));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(v.y));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(v.z));
        tag.put(key, list);
    }

    public static Vector3d vector(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_DOUBLE);
        return list.size() == 3 ? new Vector3d(list.getDouble(0), list.getDouble(1), list.getDouble(2)) : new Vector3d();
    }
}
