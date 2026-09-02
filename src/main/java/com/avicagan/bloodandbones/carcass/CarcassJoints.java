package com.avicagan.bloodandbones.carcass;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.EnumSet;

/**
 * Creates the ball joints between limbs. All positions are in plot-space block coordinates, the way Sable
 * expects joint anchors; the frame orientations are relative to each body's own orientation.
 */
public final class CarcassJoints {
    /**
     * @param parent       bone name on the parent side
     * @param child        bone name on the child side
     * @param anchorParent joint point in the parent's plot space
     * @param anchorChild  joint point in the child's plot space
     * @param frame1       joint frame relative to the parent body
     * @param frame2       joint frame relative to the child body
     * @param limitMin     minimum swing per axis, radians
     * @param limitMax     maximum swing per axis, radians
     * @param damping      motor damping (resists motion)
     * @param stiffness    motor stiffness (pulls back to rest)
     */
    public record Spec(String parent, String child, Vector3d anchorParent, Vector3d anchorChild,
                       Quaterniond frame1, Quaterniond frame2, Vector3f limitMin, Vector3f limitMax,
                       float damping, float stiffness) {

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Parent", parent);
            tag.putString("Child", child);
            tag.put("AnchorParent", vec(anchorParent));
            tag.put("AnchorChild", vec(anchorChild));
            tag.put("Frame1", quat(frame1));
            tag.put("Frame2", quat(frame2));
            tag.put("LimitMin", vec(limitMin));
            tag.put("LimitMax", vec(limitMax));
            tag.putFloat("Damping", damping);
            tag.putFloat("Stiffness", stiffness);
            return tag;
        }

        public static Spec load(CompoundTag tag) {
            return new Spec(
                    tag.getString("Parent"),
                    tag.getString("Child"),
                    vec3d(tag.getList("AnchorParent", Tag.TAG_DOUBLE)),
                    vec3d(tag.getList("AnchorChild", Tag.TAG_DOUBLE)),
                    quat(tag.getList("Frame1", Tag.TAG_DOUBLE)),
                    quat(tag.getList("Frame2", Tag.TAG_DOUBLE)),
                    vec3f(tag.getList("LimitMin", Tag.TAG_DOUBLE)),
                    vec3f(tag.getList("LimitMax", Tag.TAG_DOUBLE)),
                    tag.getFloat("Damping"),
                    tag.getFloat("Stiffness"));
        }

        private static ListTag vec(Vector3d v) {
            ListTag list = new ListTag();
            list.add(net.minecraft.nbt.DoubleTag.valueOf(v.x));
            list.add(net.minecraft.nbt.DoubleTag.valueOf(v.y));
            list.add(net.minecraft.nbt.DoubleTag.valueOf(v.z));
            return list;
        }

        private static ListTag vec(Vector3f v) {
            return vec(new Vector3d(v));
        }

        private static ListTag quat(Quaterniond q) {
            ListTag list = new ListTag();
            list.add(net.minecraft.nbt.DoubleTag.valueOf(q.x));
            list.add(net.minecraft.nbt.DoubleTag.valueOf(q.y));
            list.add(net.minecraft.nbt.DoubleTag.valueOf(q.z));
            list.add(net.minecraft.nbt.DoubleTag.valueOf(q.w));
            return list;
        }

        private static Vector3d vec3d(ListTag list) {
            return new Vector3d(list.getDouble(0), list.getDouble(1), list.getDouble(2));
        }

        private static Vector3f vec3f(ListTag list) {
            return new Vector3f((float) list.getDouble(0), (float) list.getDouble(1), (float) list.getDouble(2));
        }

        private static Quaterniond quat(ListTag list) {
            return new Quaterniond(list.getDouble(0), list.getDouble(1), list.getDouble(2), list.getDouble(3));
        }
    }

    private CarcassJoints() {
    }

    /**
     * Adds the joint to the physics scene. Returns null if Sable refused it.
     */
    @Nullable
    public static GenericConstraintHandle attach(PhysicsPipeline pipeline, ServerSubLevel parent, ServerSubLevel child, Spec spec) {
        GenericConstraintConfiguration config = new GenericConstraintConfiguration(
                spec.anchorParent(), spec.anchorChild(), spec.frame1(), spec.frame2(),
                EnumSet.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z));
        GenericConstraintHandle handle = pipeline.addConstraint(parent, child, config);
        if (handle == null) {
            return null;
        }
        handle.setContactsEnabled(false);
        handle.setLimit(ConstraintJointAxis.ANGULAR_X, spec.limitMin().x, spec.limitMax().x);
        handle.setLimit(ConstraintJointAxis.ANGULAR_Y, spec.limitMin().y, spec.limitMax().y);
        handle.setLimit(ConstraintJointAxis.ANGULAR_Z, spec.limitMin().z, spec.limitMax().z);
        if (spec.damping() > 0 || spec.stiffness() > 0) {
            // Motor gains are per unit of limb mass, so a light leg and a heavy body feel the same.
            double mass = Math.max(0.01, child.getMassTracker().getMass());
            for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
                handle.setMotor(axis, 0.0, spec.stiffness() * mass, spec.damping() * mass, false, 0.0);
            }
        }
        return handle;
    }
}
