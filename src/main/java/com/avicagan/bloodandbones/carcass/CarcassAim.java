package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where a line (a blow, a punch, a hook thrown from someone's eye) meets a carcass: which part it lands on, and the
 * point on that part's drawn box. The box is the one each body's cells fill, so a hit is judged on what the player sees.
 * A carcass lying still has one body, its torso; its other parts are drawn from their remembered poses
 * ({@link CarcassPartBlockEntity#merged()}), and a line is judged against those too, so a lying carcass can be hooked or
 * struck by any part of it. Works on either side: the server judges blows and hooks, the client punches.
 */
public final class CarcassAim {
    private CarcassAim() {
    }

    /**
     * @param bone     the part the line landed on
     * @param point    where, in the world
     * @param distance how far along the line from its start
     */
    public record Hit(String bone, Vector3d point, double distance) {
    }

    /**
     * How far along a line (from {@code origin}, {@code dir} of length 1) it enters the box {@code [lo, hi]}; 0 if it
     * starts inside, negative if it misses.
     */
    static double enter(Vector3d origin, Vector3d dir, Vector3d lo, Vector3d hi) {
        double near = Double.NEGATIVE_INFINITY;
        double far = Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            double o = origin.get(axis);
            double d = dir.get(axis);
            double min = lo.get(axis);
            double max = hi.get(axis);
            if (Math.abs(d) < 1.0e-12) {
                if (o < min || o > max) {
                    return -1.0;
                }
                continue;
            }
            double t1 = (min - o) / d;
            double t2 = (max - o) / d;
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
        }
        if (near > far || far < 0.0) {
            return -1.0;
        }
        return Math.max(0.0, near);
    }

    /** A bone's drawn box in its body's own plot: from the bone's origin there, its box in blocks. */
    public static Vector3d[] plotBox(SubLevel body, Bone bone) {
        Vector3d origin = boneOriginInPlot(body, bone);
        return new Vector3d[]{new Vector3d(bone.boxMin()).div(16.0).add(origin), new Vector3d(bone.boxMax()).div(16.0).add(origin)};
    }

    private static Vector3d boneOriginInPlot(SubLevel body, Bone bone) {
        BlockPos anchor = body.getPlot().getCenterBlock();
        return new Vector3d(anchor.getX() - bone.boxMin().x / 16.0, anchor.getY() - bone.boxMin().y / 16.0, anchor.getZ() - bone.boxMin().z / 16.0);
    }

    /** The line in a body's plot: its start and its way, in {@code out[0]} and {@code out[1]}. */
    private static Vector3d[] toPlot(Pose3dc pose, Vec3 eye, Vec3 look) {
        Vector3d origin = pose.transformPositionInverse(new Vector3d(eye.x, eye.y, eye.z), new Vector3d());
        Vector3d dir = pose.orientation().transformInverse(new Vector3d(look.x, look.y, look.z), new Vector3d()).normalize();
        return new Vector3d[]{origin, dir};
    }

    /** Where the line first meets one body's box, within reach, or null. */
    @Nullable
    public static Hit body(SubLevel body, Pose3dc pose, Bone bone, Vec3 eye, Vec3 look, double reach) {
        Vector3d[] box = plotBox(body, bone);
        Vector3d[] line = toPlot(pose, eye, look);
        double t = enter(line[0], line[1], box[0], box[1]);
        if (t < 0.0 || t > reach) {
            return null;
        }
        Vector3d plot = new Vector3d(line[1]).mul(t).add(line[0]);
        return new Hit(bone.name(), pose.transformPosition(plot, new Vector3d()), t);
    }

    /** Where the line first meets any live body of a carcass that is not lying still (server), within reach; or null. */
    @Nullable
    public static Hit first(ServerLevel level, CarcassSavedData.Carcass carcass, Rig rig, Vec3 eye, Vec3 look, double reach) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        Hit best = null;
        for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
            Bone bone = rig.bone(entry.getKey()).orElse(null);
            if (bone == null || !(container.getSubLevel(entry.getValue()) instanceof ServerSubLevel body) || body.isRemoved()) {
                continue;
            }
            Hit hit = body(body, body.logicalPose(), bone, eye, look, reach);
            if (hit != null && (best == null || hit.distance() < best.distance())) {
                best = hit;
            }
        }
        return best;
    }

    /**
     * The point of a carcass nearest a line that meets none of it: on the body whose box middle passes closest, the
     * point of the line nearest that middle, brought inside the box. A blow that glances past (a killer looking a little
     * off) still lands on the side it came from.
     */
    @Nullable
    public static Hit nearest(ServerLevel level, CarcassSavedData.Carcass carcass, Rig rig, Vec3 eye, Vec3 look) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        Hit best = null;
        double bestMiss = Double.MAX_VALUE;
        for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
            Bone bone = rig.bone(entry.getKey()).orElse(null);
            if (bone == null || !(container.getSubLevel(entry.getValue()) instanceof ServerSubLevel body) || body.isRemoved()) {
                continue;
            }
            Vector3d[] box = plotBox(body, bone);
            Vector3d[] line = toPlot(body.logicalPose(), eye, look);
            Vector3d middle = new Vector3d(box[0]).add(box[1]).mul(0.5);
            double along = Math.max(0.0, new Vector3d(middle).sub(line[0]).dot(line[1]));
            Vector3d closest = new Vector3d(line[1]).mul(along).add(line[0]);
            double miss = closest.distance(middle);
            if (miss < bestMiss) {
                bestMiss = miss;
                Vector3d inside = new Vector3d(Math.max(box[0].x, Math.min(box[1].x, closest.x)), Math.max(box[0].y, Math.min(box[1].y, closest.y)),
                        Math.max(box[0].z, Math.min(box[1].z, closest.z)));
                best = new Hit(bone.name(), body.logicalPose().transformPosition(inside, new Vector3d()), along);
            }
        }
        return best;
    }

    /**
     * Where the line first meets a carcass lying still, within reach: its torso's box, or the box of a part folded into
     * it where that part is drawn. Judged from the torso's root cell, so either side can ask.
     *
     * @param torso     the torso's body
     * @param pose      its pose (the logical pose on the server, the drawn pose on a client)
     * @param torsoBone the torso's bone
     * @param merged    the parts folded into it and where they lie ({@link CarcassPartBlockEntity#merged()})
     */
    @Nullable
    public static Hit resting(SubLevel torso, Rig rig, Bone torsoBone, List<CarcassPartBlockEntity.MergedPart> merged,
                              Vec3 eye, Vec3 look, double reach) {
        return resting(torso, torso.logicalPose(), rig, torsoBone, merged, eye, look, reach);
    }

    /**
     * @param torso the torso of a carcass lying still
     * @param root  its root cell, which draws its folded parts
     */
    public record RestingHit(SubLevel torso, CarcassPartBlockEntity root, Hit hit) {
    }

    /**
     * The carcass lying still whose drawn parts or torso a line meets first, within reach, on either side: judged from
     * each lying carcass's root cell and the rig its side knows.
     */
    @Nullable
    public static RestingHit nearestResting(net.minecraft.world.level.Level level, Vec3 eye, Vec3 look, double reach) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        RestingHit best = null;
        for (SubLevel torso : container.getAllSubLevels()) {
            if (torso.isRemoved() || torso.logicalPose().position().distance(eye.x, eye.y, eye.z) > reach + 4.0
                    || !(level.getBlockEntity(torso.getPlot().getCenterBlock()) instanceof CarcassPartBlockEntity root)
                    || !root.isRoot() || root.merged().isEmpty() || root.carcassId() == null) {
                continue;
            }
            Rig rig = (level.isClientSide ? com.avicagan.bloodandbones.carcass.rig.RigManager.clientRig(root.entity(), root.baby())
                    : com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(root.entity(), root.baby())).orElse(null);
            Bone torsoBone = rig == null ? null : rig.bone(root.bone()).orElse(null);
            if (torsoBone == null) {
                continue;
            }
            Hit hit = resting(torso, rig, torsoBone, root.merged(), eye, look, reach);
            if (hit != null && (best == null || hit.distance() < best.hit().distance())) {
                best = new RestingHit(torso, root, hit);
            }
        }
        return best;
    }

    @Nullable
    public static Hit resting(SubLevel torso, Pose3dc pose, Rig rig, Bone torsoBone, List<CarcassPartBlockEntity.MergedPart> merged,
                              Vec3 eye, Vec3 look, double reach) {
        Hit best = body(torso, pose, torsoBone, eye, look, reach);
        Vector3d[] line = toPlot(pose, eye, look);
        Vector3d torsoOrigin = boneOriginInPlot(torso, torsoBone);
        for (CarcassPartBlockEntity.MergedPart part : merged) {
            Bone bone = rig.bone(part.bone()).orElse(null);
            if (bone == null) {
                continue;
            }
            // into the part's own frame: its origin sits at the torso's origin plus its remembered offset, turned as it lay
            Quaterniond turn = new Quaterniond(part.orientation().x, part.orientation().y, part.orientation().z, part.orientation().w);
            Vector3d offset = new Vector3d(part.position().x, part.position().y, part.position().z).add(torsoOrigin);
            Vector3d origin = turn.transformInverse(new Vector3d(line[0]).sub(offset), new Vector3d());
            Vector3d dir = turn.transformInverse(new Vector3d(line[1]), new Vector3d());
            Vector3d lo = new Vector3d(bone.boxMin()).div(16.0);
            Vector3d hi = new Vector3d(bone.boxMax()).div(16.0);
            double t = enter(origin, dir, lo, hi);
            if (t < 0.0 || t > reach || (best != null && t >= best.distance())) {
                continue;
            }
            Vector3d plot = new Vector3d(line[1]).mul(t).add(line[0]);
            best = new Hit(bone.name(), pose.transformPosition(plot, new Vector3d()), t);
        }
        return best;
    }
}
