package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.BloodAndBones;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers which sub-levels make up each carcass and how they are jointed, because Sable persists
 * sub-levels but not joints. Joints are re-created whenever the root limb ticks and finds them missing.
 */
public class CarcassSavedData extends SavedData {
    private static final String NAME = BloodAndBones.MOD_ID + "_carcasses";

    /** A merged limb's bone origin and orientation relative to the torso bone's frame, in blocks. */
    public record RestPose(org.joml.Vector3d position, org.joml.Quaterniond orientation) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("X", position.x);
            tag.putDouble("Y", position.y);
            tag.putDouble("Z", position.z);
            tag.putDouble("QX", orientation.x);
            tag.putDouble("QY", orientation.y);
            tag.putDouble("QZ", orientation.z);
            tag.putDouble("QW", orientation.w);
            return tag;
        }

        static RestPose load(CompoundTag tag) {
            return new RestPose(new org.joml.Vector3d(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                    new org.joml.Quaterniond(tag.getDouble("QX"), tag.getDouble("QY"), tag.getDouble("QZ"), tag.getDouble("QW")));
        }
    }

    public static final class Carcass {
        public final UUID id;
        public final ResourceLocation entity;
        public final String rootBone;
        /** bone name -> sub-level id, in rig order */
        public final Map<String, UUID> bones = new LinkedHashMap<>();
        public final List<CarcassJoints.Spec> joints = new ArrayList<>();
        /** live joint handles, not saved */
        public final List<PhysicsConstraintHandle> liveJoints = new ArrayList<>();
        /** Resting form: limbs merged into the torso's sub-level; poses relative to the torso bone frame. */
        public boolean resting;
        public final Map<String, RestPose> restPoses = new LinkedHashMap<>();
        /** plot positions of the cells added to the torso plot for the merged limbs */
        public final List<net.minecraft.core.BlockPos> restCells = new ArrayList<>();
        /** consecutive ticks the whole carcass has been still, not saved */
        public int stillTicks;
        /** cleaver cuts per limb, and limbs whose joint to the body has been cut through */
        public final Map<String, Integer> cuts = new LinkedHashMap<>();
        public final java.util.Set<String> severed = new java.util.LinkedHashSet<>();
        /** the limb the killing blow landed on, for the shove; not saved */
        @Nullable
        public String hitBone;
        /** resting form: the world joint that pins the merged body in place, not saved */
        @Nullable
        public PhysicsConstraintHandle restLock;
        /** what the mob wore, so re-assembled limbs draw the same */
        public CarcassLook look = new CarcassLook(ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"), List.of());
        /** rot: 1.0 fresh, 0.0 rotten */
        public float freshness = 1.0F;
        /** game time the rot was last applied, so time spent unloaded still counts; -1 until first tick */
        public long rotClock = -1L;
        /** rot speed multiplier from the surroundings, re-sampled now and then; not saved */
        public float rotRate = 1.0F;
        /** ticks since the surroundings were sampled / the clients were told the freshness; not saved */
        public int rotSampleTicks = Integer.MAX_VALUE;
        public int rotSyncTicks;

        public boolean isRotten() {
            return freshness <= 0.0F;
        }

        public Carcass(UUID id, ResourceLocation entity, String rootBone) {
            this.id = id;
            this.entity = entity;
            this.rootBone = rootBone;
        }

        public boolean jointsValid() {
            if (liveJoints.size() != joints.size()) {
                return false;
            }
            for (PhysicsConstraintHandle handle : liveJoints) {
                if (!handle.isValid()) {
                    return false;
                }
            }
            return true;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("Entity", entity.toString());
            tag.putString("Root", rootBone);
            ListTag boneList = new ListTag();
            bones.forEach((name, uuid) -> {
                CompoundTag b = new CompoundTag();
                b.putString("Name", name);
                b.putUUID("SubLevel", uuid);
                boneList.add(b);
            });
            tag.put("Bones", boneList);
            ListTag jointList = new ListTag();
            for (CarcassJoints.Spec joint : joints) {
                jointList.add(joint.save());
            }
            tag.put("Joints", jointList);
            tag.putBoolean("Resting", resting);
            CompoundTag cutTag = new CompoundTag();
            cuts.forEach(cutTag::putInt);
            tag.put("Cuts", cutTag);
            ListTag severedList = new ListTag();
            for (String bone : severed) {
                severedList.add(net.minecraft.nbt.StringTag.valueOf(bone));
            }
            tag.put("Severed", severedList);
            tag.putFloat("Freshness", freshness);
            tag.putLong("RotClock", rotClock);
            tag.putString("Texture", look.texture().toString());
            ListTag passList = new ListTag();
            for (CarcassLook.Coat pass : look.passes()) {
                CompoundTag p = new CompoundTag();
                p.putString("Layer", pass.layer());
                p.putString("Texture", pass.texture().toString());
                p.putInt("Tint", pass.tint());
                passList.add(p);
            }
            tag.put("Passes", passList);
            ListTag restList = new ListTag();
            restPoses.forEach((name, pose) -> {
                CompoundTag r = pose.save();
                r.putString("Name", name);
                restList.add(r);
            });
            tag.put("RestPoses", restList);
            ListTag cellList = new ListTag();
            for (net.minecraft.core.BlockPos cell : restCells) {
                cellList.add(net.minecraft.nbt.NbtUtils.writeBlockPos(cell));
            }
            tag.put("RestCells", cellList);
            return tag;
        }

        public static Carcass load(CompoundTag tag) {
            Carcass carcass = new Carcass(tag.getUUID("Id"), ResourceLocation.parse(tag.getString("Entity")), tag.getString("Root"));
            for (Tag t : tag.getList("Bones", Tag.TAG_COMPOUND)) {
                CompoundTag b = (CompoundTag) t;
                carcass.bones.put(b.getString("Name"), b.getUUID("SubLevel"));
            }
            for (Tag t : tag.getList("Joints", Tag.TAG_COMPOUND)) {
                CarcassJoints.Spec joint = CarcassJoints.Spec.load((CompoundTag) t);
                if (joint != null) {
                    carcass.joints.add(joint);
                }
            }
            carcass.resting = tag.getBoolean("Resting");
            CompoundTag cutTag = tag.getCompound("Cuts");
            for (String key : cutTag.getAllKeys()) {
                carcass.cuts.put(key, cutTag.getInt(key));
            }
            for (Tag t : tag.getList("Severed", Tag.TAG_STRING)) {
                carcass.severed.add(t.getAsString());
            }
            carcass.freshness = tag.contains("Freshness") ? tag.getFloat("Freshness") : 1.0F;
            carcass.rotClock = tag.contains("RotClock") ? tag.getLong("RotClock") : -1L;
            if (tag.contains("Texture")) {
                List<CarcassLook.Coat> passes = new ArrayList<>();
                for (Tag t : tag.getList("Passes", Tag.TAG_COMPOUND)) {
                    CompoundTag p = (CompoundTag) t;
                    passes.add(new CarcassLook.Coat(p.getString("Layer"), ResourceLocation.parse(p.getString("Texture")), p.getInt("Tint")));
                }
                carcass.look = new CarcassLook(ResourceLocation.parse(tag.getString("Texture")), passes);
            }
            for (Tag t : tag.getList("RestPoses", Tag.TAG_COMPOUND)) {
                CompoundTag r = (CompoundTag) t;
                carcass.restPoses.put(r.getString("Name"), RestPose.load(r));
            }
            // NbtUtils.writeBlockPos makes int arrays, so the list must be read as int arrays
            for (Tag t : tag.getList("RestCells", Tag.TAG_INT_ARRAY)) {
                int[] xyz = ((net.minecraft.nbt.IntArrayTag) t).getAsIntArray();
                if (xyz.length == 3) {
                    carcass.restCells.add(new net.minecraft.core.BlockPos(xyz[0], xyz[1], xyz[2]));
                }
            }
            return carcass;
        }
    }

    private final Map<UUID, Carcass> carcasses = new LinkedHashMap<>();

    public static CarcassSavedData get(ServerLevel level) {
        CarcassSavedData data = level.getDataStorage().computeIfAbsent(new Factory<>(CarcassSavedData::new, CarcassSavedData::load, null), NAME);
        data.level = level;
        return data;
    }

    private static CarcassSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CarcassSavedData data = new CarcassSavedData();
        for (Tag t : tag.getList("Carcasses", Tag.TAG_COMPOUND)) {
            Carcass carcass = Carcass.load((CompoundTag) t);
            data.carcasses.put(carcass.id, carcass);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Carcass carcass : carcasses.values()) {
            list.add(carcass.save());
        }
        tag.put("Carcasses", list);
        return tag;
    }

    public void add(Carcass carcass) {
        carcasses.put(carcass.id, carcass);
        setDirty();
    }

    @Nullable
    public Carcass carcass(UUID id) {
        return carcasses.get(id);
    }

    public Collection<Carcass> all() {
        return carcasses.values();
    }

    /** The carcass one of whose limbs is this sub-level. */
    @Nullable
    public Carcass carcassOfSubLevel(UUID subLevelId) {
        for (Carcass carcass : carcasses.values()) {
            if (carcass.bones.containsValue(subLevelId)) {
                return carcass;
            }
        }
        return null;
    }

    /**
     * Called every tick by the root limb while it is loaded. Rebuilds joints after a reload or unload.
     */
    public void tickRoot(UUID id, ServerSubLevel rootSubLevel) {
        Carcass carcass = carcasses.get(id);
        if (carcass == null) {
            return;
        }
        // every limb's first cell ticks; only the torso's counts stillness, or the carcass folds N times too fast
        UUID torsoId = carcass.bones.get(carcass.rootBone);
        boolean torso = torsoId != null && torsoId.equals(rootSubLevel.getUniqueId());
        if (torso) {
            CarcassRot.tick(rootSubLevel.getLevel(), carcass, rootSubLevel);
        }
        if (carcass.resting) {
            if (torso) {
                if (carcass.restLock == null || !carcass.restLock.isValid()) {
                    CarcassRest.lock(rootSubLevel.getLevel(), carcass, rootSubLevel);
                }
                CarcassRest.tickResting(rootSubLevel.getLevel(), carcass, rootSubLevel);
            }
            return;
        }
        if (torso) {
            CarcassRest.tick(rootSubLevel.getLevel(), carcass);
        }
        if (carcass.resting) {
            return;
        }
        // joints saved by an older build could not be read; with none at all jointsValid() would say fine
        if (torso && carcass.joints.size() < carcass.bones.size() - 1 - carcass.severed.size()) {
            rebuildJointSpecs(carcass);
        }
        if (carcass.jointsValid()) {
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(rootSubLevel.getLevel());
        if (container == null) {
            return;
        }
        Map<String, ServerSubLevel> loaded = new LinkedHashMap<>();
        for (Map.Entry<String, UUID> bone : carcass.bones.entrySet()) {
            SubLevel subLevel = container.getSubLevel(bone.getValue());
            if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
                return;
            }
            loaded.put(bone.getKey(), serverSubLevel);
        }
        for (PhysicsConstraintHandle handle : carcass.liveJoints) {
            if (handle.isValid()) {
                handle.remove();
            }
        }
        carcass.liveJoints.clear();
        for (CarcassJoints.Spec joint : carcass.joints) {
            ServerSubLevel parent = loaded.get(joint.parent());
            ServerSubLevel child = loaded.get(joint.child());
            if (parent == null || child == null) {
                continue;
            }
            PhysicsConstraintHandle handle = CarcassJoints.attach(container.physicsSystem().getPipeline(), parent, child, joint);
            if (handle != null) {
                carcass.liveJoints.add(handle);
            }
        }
        BloodAndBones.LOGGER.debug("Re-attached {} joints for carcass {}", carcass.liveJoints.size(), id);
    }

    /** Joints saved by an older build cannot be read; make them again from the rig for the bones still here. */
    private void rebuildJointSpecs(Carcass carcass) {
        com.avicagan.bloodandbones.carcass.rig.Rig rig = com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(carcass.entity).orElse(null);
        if (rig == null) {
            return;
        }
        int before = carcass.joints.size();
        java.util.List<String> had = new ArrayList<>(carcass.bones.keySet());
        carcass.joints.clear();
        for (com.avicagan.bloodandbones.carcass.rig.Bone bone : rig.bones()) {
            if (bone.parent().isEmpty() || !carcass.bones.containsKey(bone.name()) || !carcass.bones.containsKey(bone.parent().get())
                    || carcass.severed.contains(bone.name())) {
                continue;
            }
            rig.bone(bone.parent().get()).ifPresent(parent -> carcass.joints.add(CarcassAssembler.jointSpec(parent, bone)));
        }
        setDirty();
        BloodAndBones.LOGGER.debug("Rebuilt {} joints (had {}) of {} carcass {} with bones {}", carcass.joints.size(), before, carcass.entity, carcass.id, had);
    }

    /**
     * Sable removed a body for good (not merely unloaded it): forget that limb and any joints that used it,
     * and forget the whole carcass once nothing is left of it.
     */
    /** Set while the resting form deliberately removes limb sub-levels, so they are not forgotten. */
    public boolean mergingLimbs;

    public void onSubLevelRemoved(UUID subLevelId) {
        if (mergingLimbs) {
            return;
        }
        boolean changed = false;
        var iterator = carcasses.values().iterator();
        while (iterator.hasNext()) {
            Carcass carcass = iterator.next();
            String bone = null;
            for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
                if (entry.getValue().equals(subLevelId)) {
                    bone = entry.getKey();
                    break;
                }
            }
            if (bone == null) {
                continue;
            }
            String removedBone = bone;
            carcass.bones.remove(removedBone);
            carcass.joints.removeIf(joint -> joint.parent().equals(removedBone) || joint.child().equals(removedBone));
            if (removedBone.equals(carcass.rootBone) && carcass.resting) {
                // the merged body itself is gone: the folded limbs went with it
                CarcassRest.unlock(carcass);
                carcass.resting = false;
                carcass.restPoses.clear();
                carcass.restCells.clear();
            }
            // Sable only notices a joint's body is gone on its next physics tick, so isValid() still says yes
            // here; drop every live joint and let the root tick rebuild the survivors
            for (PhysicsConstraintHandle handle : carcass.liveJoints) {
                if (handle.isValid()) {
                    handle.remove();
                }
            }
            carcass.liveJoints.clear();
            if (carcass.bones.isEmpty()) {
                iterator.remove();
            }
            changed = true;
        }
        if (changed) {
            setDirty();
        }
    }

    /**
     * The other limbs of a carcass, for Sable's load-together dependencies.
     */
    @Nullable
    public Iterable<SubLevel> siblings(UUID id, String selfBone) {
        Carcass carcass = carcasses.get(id);
        if (carcass == null || carcass.resting) {
            return null;
        }
        ServerSubLevelContainer container = null;
        List<SubLevel> result = new ArrayList<>();
        for (Map.Entry<String, UUID> bone : carcass.bones.entrySet()) {
            if (bone.getKey().equals(selfBone)) {
                continue;
            }
            if (container == null) {
                container = findContainer();
                if (container == null) {
                    return null;
                }
            }
            SubLevel subLevel = container.getSubLevel(bone.getValue());
            if (subLevel != null && !subLevel.isRemoved()) {
                result.add(subLevel);
            }
        }
        return result;
    }

    private ServerLevel level;

    private CarcassSavedData() {
    }

    @Nullable
    private ServerSubLevelContainer findContainer() {
        return level == null ? null : SubLevelContainer.getContainer(level);
    }
}
