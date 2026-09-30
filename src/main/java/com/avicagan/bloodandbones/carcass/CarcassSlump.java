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
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import org.jetbrains.annotations.Nullable;
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
     * How the legs giving way turn it over its feet: a little more than it takes to carry its middle over them, at least
     * {@link #SPIN_LEAST} radians a second, and each time it is still standing a little more.
     */
    static final double TIP = 1.3;
    static final double SPIN_LEAST = 0.8;
    static final double SPIN_MORE = 0.5;
    /** Its torso slower than this, blocks a second, is nearly still. */
    private static final double NEARLY_STILL = 0.5;
    /** Times the legs give way before a carcass that still stands (wedged upright somewhere) is let rest as it is. */
    static final int MOST = 6;

    private CarcassSlump() {
    }

    /**
     * Called each tick for a carcass that is loose, or dragged ({@code dragged}: then it gives way however it moves, as
     * it is pulled along on its feet). Returns true while it stands on its legs, so it is not let rest yet.
     */
    static boolean tick(ServerLevel level, ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, Rig rig, boolean dragged) {
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
        debug(level, container, carcass, rig, torso, torsoBone, physics);
        // nearly still where it stands: on a ship under way, on the deck
        Vector3d speed = physics.getPhysicsHandle(torso).getLinearVelocity(new Vector3d());
        ServerSubLevel deck = deckUnderFeet(level, container, carcass, rig);
        if (deck != null) {
            Vector3d deckLinear = new Vector3d();
            Vector3d deckAngular = new Vector3d();
            CarcassFloat.velocity(level, physics, deck, deckLinear, deckAngular);
            speed.sub(new Vector3d(deckAngular).cross(new Vector3d(torso.logicalPose().position()).sub(deck.logicalPose().position())).add(deckLinear));
        }
        if (!dragged && speed.length() > NEARLY_STILL) {
            return true;
        }
        if (++carcass.standingTicks < STANDING_TICKS) {
            return true;
        }
        carcass.standingTicks = 0;
        carcass.slumps++;
        com.avicagan.bloodandbones.BloodAndBones.LOGGER.info("[slump] {} {} gives way, time {}", carcass.entity, carcass.slumps, level.getGameTime());
        giveWay(level, container, carcass, rig, torso, torsoBone);
        return true;
    }

    /**
     * Its legs give way: the whole carcass is set turning over the feet on one side (the side it leans to, or, standing
     * straight, one side or the other), all its parts together, so no joint fights it, just fast enough to carry its
     * middle over those feet, and it falls onto that side as a body pushed past its balance does. Turned about its own
     * middle instead, the torso pressed its legs into the ground on that side, which pushed it back: a polar bear so
     * rocked came back upright every time.
     */
    static void giveWay(ServerLevel level, ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, Rig rig, ServerSubLevel torso, Bone torsoBone) {
        SubLevelPhysicsSystem physics = container.physicsSystem();
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
        // the level way across it, and which way along that it goes down
        Vector3d across = new Vector3d(0, 1, 0).cross(along).normalize();
        double lean = across.dot(up.x, 0.0, up.z);
        double side = Math.abs(lean) > 0.02 ? Math.signum(lean) : (carcass.id.getLeastSignificantBits() & 1L) == 0L ? 1.0 : -1.0;
        across.mul(side);
        // its middle, by weight, and the feet it goes over: the part corners on the ground furthest that way
        List<ServerSubLevel> bodies = new ArrayList<>();
        Vector3d middle = new Vector3d();
        double mass = 0.0;
        double ground = Double.MAX_VALUE;
        List<Vector3d> low = new ArrayList<>();
        for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
            Bone bone = rig.bone(entry.getKey()).orElse(null);
            if (bone == null || !(container.getSubLevel(entry.getValue()) instanceof ServerSubLevel body) || body.isRemoved()) {
                continue;
            }
            var tracker = body.getMassTracker();
            if (!tracker.isInvalid()) {
                double m = tracker.getMass();
                middle.add(body.logicalPose().transformPosition(new Vector3d(tracker.getCenterOfMass())).mul(m));
                mass += m;
            }
            bodies.add(body);
            for (Vector3d corner : corners(body, bone)) {
                ground = Math.min(ground, corner.y);
                low.add(corner);
            }
        }
        if (mass <= 0.0 || bodies.isEmpty()) {
            return;
        }
        middle.div(mass);
        double outer = -Double.MAX_VALUE;
        for (Vector3d corner : low) {
            if (corner.y < ground + FOOT) {
                outer = Math.max(outer, across.dot(corner));
            }
        }
        // the line it turns about: level, along it, at the ground, through the outermost foot that way
        Vector3d pivot = new Vector3d(middle).add(new Vector3d(across).mul(outer - across.dot(middle)));
        pivot.y = ground;
        double height = Math.max(0.05, middle.y - ground);
        double inside = Math.max(0.0, outer - across.dot(middle));
        double reach = Math.sqrt(height * height + inside * inside);
        // what raising its middle over the feet takes: a body falling from there comes down at the speed it would anyway
        double gravity = DimensionPhysicsData.getGravity(level).length();
        double spin = TIP * Math.sqrt(2.0 * gravity * (reach - height)) / reach + SPIN_MORE * Math.max(0, carcass.slumps - 1);
        spin = Math.max(SPIN_LEAST, spin);
        Vector3d turn = new Vector3d(0, 1, 0).cross(across).mul(spin);
        for (ServerSubLevel body : bodies) {
            var tracker = body.getMassTracker();
            Vector3d at = tracker.isInvalid() ? new Vector3d(body.logicalPose().position())
                    : body.logicalPose().transformPosition(new Vector3d(tracker.getCenterOfMass()));
            physics.getPipeline().wakeUp(body);
            physics.getPipeline().addLinearAndAngularVelocity(body, new Vector3d(turn).cross(at.sub(pivot)), turn);
        }
    }

    /** How close to the lowest corner of it a corner must be to count as a foot on the ground, blocks. */
    private static final double FOOT = 0.1;

    /** The sub-level (a ship's deck) its lowest part stands on, or null for the world. */
    @Nullable
    static ServerSubLevel deckUnderFeet(ServerLevel level, ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, Rig rig) {
        Vector3d lowest = null;
        for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
            Bone bone = rig.bone(entry.getKey()).orElse(null);
            if (bone != null && container.getSubLevel(entry.getValue()) instanceof ServerSubLevel body && !body.isRemoved()) {
                for (Vector3d corner : corners(body, bone)) {
                    if (lowest == null || corner.y < lowest.y) {
                        lowest = corner;
                    }
                }
            }
        }
        if (lowest == null) {
            return null;
        }
        Vector3d point = new Vector3d(lowest.x, lowest.y - 0.1, lowest.z);
        BoundingBox3d reach = new BoundingBox3d(point.x - 0.05, point.y - 0.05, point.z - 0.05, point.x + 0.05, point.y + 0.05, point.z + 0.05);
        for (SubLevel other : container.queryIntersecting(reach)) {
            if (other instanceof ServerSubLevel deck && !other.isRemoved() && !carcass.bones.containsValue(other.getUniqueId())) {
                return deck;
            }
        }
        return null;
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

    static void debug(ServerLevel level, ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, Rig rig, ServerSubLevel torso, Bone torsoBone, SubLevelPhysicsSystem physics) {
        StringBuilder b = new StringBuilder();
        Quaterniond model = modelToWorld(torso, torsoBone);
        Vector3d up = model.transform(new Vector3d(0, -1, 0));
        double low = Double.MAX_VALUE;
        for (Vector3d c : corners(torso, torsoBone)) low = Math.min(low, c.y);
        b.append(String.format("[slumpdbg] %s t=%d st=%d upy=%.3f low=%.3f v=%s w=%s", carcass.entity, level.getGameTime(), carcass.standingTicks, up.y, low,
                physics.getPhysicsHandle(torso).getLinearVelocity(new Vector3d()).toString(new java.text.DecimalFormat("0.000")),
                physics.getPhysicsHandle(torso).getAngularVelocity(new Vector3d()).toString(new java.text.DecimalFormat("0.000"))));
        for (CarcassJoints.Spec spec : carcass.joints) {
            if (!spec.parent().equals(carcass.rootBone)) continue;
            if (!(container.getSubLevel(carcass.bones.get(spec.child())) instanceof ServerSubLevel leg)) continue;
            Quaterniond rel = new Quaterniond(torso.logicalPose().orientation()).mul(new Quaterniond(spec.frame1())).invert().mul(leg.logicalPose().orientation());
            Vector3d e = rel.getEulerAnglesXYZ(new Vector3d()).mul(180 / Math.PI);
            double footY = Double.MAX_VALUE;
            Bone lb = rig.bone(spec.child()).orElse(null);
            if (lb != null) for (Vector3d c : corners(leg, lb)) footY = Math.min(footY, c.y);
            b.append(String.format(" | %s e=(%.0f,%.0f,%.0f) foot=%.3f", spec.child(), e.x, e.y, e.z, footY));
        }
        com.avicagan.bloodandbones.BloodAndBones.LOGGER.info(b.toString());
    }
}
