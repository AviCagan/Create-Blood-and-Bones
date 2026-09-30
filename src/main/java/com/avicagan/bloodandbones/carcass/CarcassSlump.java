package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A dead animal never stays on its feet. Its legs are rigid bodies on loose joints, and a body on four of them can stand:
 * struck square in the face or not struck at all, nothing tips it, and with its legs splayed a little they prop it like a
 * trestle, the friction under its feet holding them (the showcase's cow struck in the face, a cow set down on a ship's
 * deck, a zombie struck from the side). Real legs fold at the knee; these cannot. So a carcass that stands on its legs,
 * nearly still, for {@link #STANDING_TICKS} has its legs give way: its torso is rolled over onto a side, the way it
 * already leans (or, standing straight, one side or the other), and it goes down with its legs limp under it. Until it
 * is down it is not let rest either, or the resting form would pin it standing for good.
 * <p>
 * It stands when its torso is within {@link #UPRIGHT} degrees of upright, some other part of it (a leg) reaches below the
 * torso by more than half as far as its legs reach when it stands, and nothing (the ground, a rack, a deck, another
 * carcass) is that close under the torso: a carcass lying on its belly, or across a Bleeding Rack with its legs hanging,
 * is down already.
 */
public final class CarcassSlump {
    /** Ticks a carcass may stand on its legs, nearly still, before they give way. */
    public static final int STANDING_TICKS = 10;
    /** How far from upright its torso may lean and still stand on its legs, degrees. */
    public static final double UPRIGHT = 45.0;
    /**
     * How fast the legs giving way roll it over, radians a second, the first time and each time more: the same for any
     * weight, as a fall is. Legs splayed to the ends of their joints prop a body like a trestle's, and the first roll may
     * only rock it; each time it is still standing they give way harder.
     */
    static final double SPIN = 2.5;
    static final double SPIN_MORE = 1.5;
    /** Its torso slower than this, blocks a second, is nearly still. */
    private static final double NEARLY_STILL = 0.5;
    /** Times the legs give way before a carcass that still stands (wedged upright somewhere) is let rest as it is. */
    static final int MOST = 6;

    private CarcassSlump() {
    }

    /**
     * Called each tick for a carcass that is loose (not held). Returns true while it stands on its legs, so it is not let
     * rest yet.
     */
    static boolean tick(ServerLevel level, ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, Rig rig) {
        if (carcass.slumps >= MOST) {
            return false;
        }
        ServerSubLevel torso = container.getSubLevel(carcass.bones.get(carcass.rootBone)) instanceof ServerSubLevel body && !body.isRemoved() ? body : null;
        Bone torsoBone = rig.bone(carcass.rootBone).orElse(null);
        if (torso == null || torsoBone == null || !standing(level, container, carcass, rig, torso, torsoBone)) {
            carcass.standingTicks = 0;
            return false;
        }
        SubLevelPhysicsSystem physics = container.physicsSystem();
        if (physics.getPhysicsHandle(torso).getLinearVelocity(new Vector3d()).length() > NEARLY_STILL) {
            return true;
        }
        if (++carcass.standingTicks < STANDING_TICKS) {
            return true;
        }
        carcass.standingTicks = 0;
        carcass.slumps++;
        com.avicagan.bloodandbones.BloodAndBones.LOGGER.info("[slump] {} {} gives way, time {}", carcass.entity, carcass.slumps, level.getGameTime());
        giveWay(physics, carcass, torso, torsoBone);
        return true;
    }

    /** Roll the torso over onto a side, about its own length, the way it leans (or by the carcass, standing straight). */
    static void giveWay(SubLevelPhysicsSystem physics, CarcassSavedData.Carcass carcass, ServerSubLevel torso, Bone torsoBone) {
        Quaterniond model = modelToWorld(torso, torsoBone);
        Vector3d up = model.transform(new Vector3d(0, -1, 0));
        Vector3d along = model.transform(new Vector3d(0, 0, -1));
        // about its length, level: for a body on four legs its spine, for one that stands upright (a zombie) its forward
        along.y = 0.0;
        if (along.lengthSquared() < 1.0e-6) {
            along.set(model.transform(new Vector3d(-1, 0, 0)));
            along.y = 0.0;
        }
        if (along.lengthSquared() < 1.0e-6) {
            return;
        }
        along.normalize();
        Vector3d lean = new Vector3d(up.x, 0.0, up.z);
        // which way the top goes rolling about +along: along x up
        Vector3d topGoes = new Vector3d(along).cross(up);
        double side;
        if (lean.lengthSquared() > 1.0e-4 && Math.abs(topGoes.dot(lean)) > 1.0e-4) {
            side = Math.signum(topGoes.dot(lean));
        } else {
            side = (carcass.id.getLeastSignificantBits() & 1L) == 0L ? 1.0 : -1.0;
        }
        physics.getPipeline().wakeUp(torso);
        physics.getPipeline().addLinearAndAngularVelocity(torso, new Vector3d(), new Vector3d(along).mul(side * (SPIN + SPIN_MORE * Math.max(0, carcass.slumps - 1))));
    }

    /** Whether the carcass stands on its legs (see the class). */
    static boolean standing(ServerLevel level, ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, Rig rig, ServerSubLevel torso,
                            Bone torsoBone) {
        double reach = legReach(rig, torsoBone);
        if (reach < 0.2) {
            return false;
        }
        Quaterniond model = modelToWorld(torso, torsoBone);
        Vector3d up = model.transform(new Vector3d(0, -1, 0));
        if (up.y < Math.cos(Math.toRadians(UPRIGHT))) {
            return false;
        }
        double clear = Math.max(0.125, 0.5 * reach);
        List<Vector3d> corners = corners(torso, torsoBone);
        double torsoLow = Double.MAX_VALUE;
        for (Vector3d corner : corners) {
            torsoLow = Math.min(torsoLow, corner.y);
        }
        double partsLow = Double.MAX_VALUE;
        for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
            if (entry.getKey().equals(carcass.rootBone)) {
                continue;
            }
            Bone bone = rig.bone(entry.getKey()).orElse(null);
            if (bone != null && container.getSubLevel(entry.getValue()) instanceof ServerSubLevel body && !body.isRemoved()) {
                for (Vector3d corner : corners(body, bone)) {
                    partsLow = Math.min(partsLow, corner.y);
                }
            }
        }
        if (partsLow > torsoLow - clear) {
            return false;
        }
        // nothing close under its lowest side: it is held up by its legs, not lying on something
        corners.sort((a, b) -> Double.compare(a.y, b.y));
        for (int i = 0; i < 4; i++) {
            Vector3d corner = corners.get(i);
            for (double depth : new double[]{0.05, clear * 0.5, clear}) {
                if (solidAt(level, container, carcass, new Vector3d(corner.x, corner.y - depth, corner.z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether something solid is at a point: a block of the world, or of a sub-level that is not this carcass. */
    private static boolean solidAt(ServerLevel level, ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, Vector3d point) {
        if (solidIn(level, BlockPos.containing(point.x, point.y, point.z), point)) {
            return true;
        }
        BoundingBox3d reach = new BoundingBox3d(point.x - 0.05, point.y - 0.05, point.z - 0.05, point.x + 0.05, point.y + 0.05, point.z + 0.05);
        Vector3d local = new Vector3d();
        for (SubLevel other : container.queryIntersecting(reach)) {
            if (other.isRemoved() || carcass.bones.containsValue(other.getUniqueId())) {
                continue;
            }
            other.logicalPose().transformPositionInverse(point, local);
            if (solidIn(level, BlockPos.containing(local.x, local.y, local.z), local)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a point lies in the collision shape of the block at {@code pos}. */
    private static boolean solidIn(ServerLevel level, BlockPos pos, Vector3d point) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        double y = point.y - pos.getY();
        return y >= shape.min(Direction.Axis.Y) - 1.0e-3 && y <= shape.max(Direction.Axis.Y) + 1.0e-3;
    }

    /** A body's model frame in the world: its orientation with its bone's own rest turn taken out. */
    static Quaterniond modelToWorld(ServerSubLevel body, Bone bone) {
        return new Quaterniond(body.logicalPose().orientation()).mul(new Quaterniond(bone.rotation()).invert());
    }

    /** The eight corners of a body's drawn box, in the world. */
    static List<Vector3d> corners(ServerSubLevel body, Bone bone) {
        Vector3d[] box = CarcassAim.plotBox(body, bone);
        List<Vector3d> out = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            out.add(body.logicalPose().transformPosition(new Vector3d((i & 1) == 0 ? box[0].x : box[1].x, (i & 2) == 0 ? box[0].y : box[1].y,
                    (i & 4) == 0 ? box[0].z : box[1].z)));
        }
        return out;
    }

    /**
     * How far below its torso the lowest of its other parts reaches as the living mob stands, in blocks: how high its legs
     * hold it. Read off the rig's standing pose (the model's y points down).
     */
    static double legReach(Rig rig, Bone torsoBone) {
        return REACH.computeIfAbsent(rig, r -> new java.util.concurrent.ConcurrentHashMap<>())
                .computeIfAbsent(torsoBone.name(), name -> lowest(torsoBone, true, rig) - lowest(torsoBone, false, rig));
    }

    /** By rig, then by the bone that is the torso (a severed piece is a carcass of its own, with its own torso). */
    private static final Map<Rig, Map<String, Double>> REACH = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** The lowest point, in blocks down the model, of the torso or of every other part. */
    private static double lowest(Bone torsoBone, boolean others, Rig rig) {
        double lowest = -Double.MAX_VALUE;
        for (Bone bone : rig.bones()) {
            if (bone.name().equals(torsoBone.name()) == others) {
                continue;
            }
            Quaterniond turn = new Quaterniond(bone.rotation());
            for (int i = 0; i < 8; i++) {
                Vector3d corner = new Vector3d((i & 1) == 0 ? bone.boxMin().x : bone.boxMax().x, (i & 2) == 0 ? bone.boxMin().y : bone.boxMax().y,
                        (i & 4) == 0 ? bone.boxMin().z : bone.boxMax().z);
                lowest = Math.max(lowest, turn.transform(corner).add(bone.offset().x, bone.offset().y, bone.offset().z).y / 16.0);
            }
        }
        return lowest;
    }
}
