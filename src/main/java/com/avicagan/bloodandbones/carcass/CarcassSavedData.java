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

    public static final class Carcass {
        public final UUID id;
        public final ResourceLocation entity;
        public final String rootBone;
        /** bone name -> sub-level id, in rig order */
        public final Map<String, UUID> bones = new LinkedHashMap<>();
        public final List<CarcassJoints.Spec> joints = new ArrayList<>();
        /** live joint handles, not saved */
        public final List<PhysicsConstraintHandle> liveJoints = new ArrayList<>();

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

        CompoundTag save() {
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
            return tag;
        }

        static Carcass load(CompoundTag tag) {
            Carcass carcass = new Carcass(tag.getUUID("Id"), ResourceLocation.parse(tag.getString("Entity")), tag.getString("Root"));
            for (Tag t : tag.getList("Bones", Tag.TAG_COMPOUND)) {
                CompoundTag b = (CompoundTag) t;
                carcass.bones.put(b.getString("Name"), b.getUUID("SubLevel"));
            }
            for (Tag t : tag.getList("Joints", Tag.TAG_COMPOUND)) {
                carcass.joints.add(CarcassJoints.Spec.load((CompoundTag) t));
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

    /**
     * Called every tick by the root limb while it is loaded. Rebuilds joints after a reload or unload.
     */
    public void tickRoot(UUID id, ServerSubLevel rootSubLevel) {
        Carcass carcass = carcasses.get(id);
        if (carcass == null || carcass.jointsValid()) {
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

    /**
     * Sable removed a body for good (not merely unloaded it): forget that limb and any joints that used it,
     * and forget the whole carcass once nothing is left of it.
     */
    public void onSubLevelRemoved(UUID subLevelId) {
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
            carcass.liveJoints.removeIf(handle -> !handle.isValid());
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
        if (carcass == null) {
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
