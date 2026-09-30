package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.registry.BBTags;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A dead animal never stays on its feet. Its legs are rigid bodies on loose joints, and a body on four of them can stand:
 * struck square in the face or not struck at all, nothing tips it; with its legs splayed a little they prop it like a
 * trestle, the friction under its feet holding them; and a leg swept back to the end of its joint props it as a strut
 * does (the showcase's cow struck in the face, a cow set down on a ship's deck, a pig or a polar bear struck in the face).
 * Once it keeps still the resting form pins it as it stands, for good. Real legs fold at the knee; these cannot. So a
 * carcass that stands on its legs, nearly still (on a deck under way, still on the deck), for {@link #STANDING_TICKS}
 * has its legs give way ({@link #giveWay}): it goes over onto a side, the way it already leans, or, standing straight,
 * one side or the other. Until it is down it is not let rest. A carcass dragged along on its feet gives way however it
 * moves (a sheep dragged by a hind leg slid along standing, facing the wrong way), going down away from the leg it is
 * hooked by; one that stands upright (a zombie) is pulled off its feet by that leg and is left to it.
 * <p>
 * It stands when its torso is within {@link #UPRIGHT} degrees of upright, some other part of it (a leg) reaches below the
 * torso by more than a quarter as far as its legs reach when it stands ({@link #LIFTED}), and nothing (the ground, a
 * rack, a deck, another carcass) is that close under the torso; or, on four legs, when it sits up on its front ones, the
 * front of its belly held up that high, its rear lower. A carcass lying on its belly, or across a Bleeding Rack with its
 * legs hanging, is down already. Hung on a hook it is lifted off its legs and never gives way; standing on a block that
 * holds a carcass to be worked (a table, a machine: {@link BBTags#HOLDS_CARCASSES}) it is held there.
 */
public final class CarcassSlump {
    /** Ticks a carcass may stand on its legs, nearly still, before they give way. */
    public static final int STANDING_TICKS = 10;
    /**
     * How high its legs may hold it, as a share of how high they held it standing, and still count as down: at half, a
     * horse held up on splayed legs at half its height stood there for good.
     */
    static final double LIFTED = 0.25;
    /** How far from upright its torso may lean and still stand on its legs, degrees. */
    public static final double UPRIGHT = 45.0;
    /**
     * How the legs giving way turn it over its feet: a little more than it takes to carry its middle over them, at least
     * {@link #SPIN_LEAST} radians a second, and each time it is still standing a little more.
     */
    static final double TIP = 1.1;
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
        // one that stands upright (a zombie) is pulled off its feet by the leg it is dragged by, and falls feet first
        if (torso == null || torsoBone == null || dragged && upright(torsoBone) || !standing(level, container, carcass, rig, torso, torsoBone)
                || standsOnAHolder(level, container, carcass, rig)) {
            carcass.standingTicks = 0;
            return false;
        }
        SubLevelPhysicsSystem physics = container.physicsSystem();
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
        com.avicagan.bloodandbones.BloodAndBones.LOGGER.debug("Carcass {} ({}) left standing: its legs give way ({} of at most {})", carcass.id, carcass.entity, carcass.slumps, MOST);
        giveWay(level, container, carcass, rig, torso, torsoBone, dragged ? CarcassDrag.hookedBone(carcass.id) : null);
        return true;
    }

    /**
     * Its legs give way: the whole carcass is set turning over the feet on one side (the side it leans to, or, standing
     * straight, one side or the other), all its parts together, so no joint fights it, just fast enough to carry its
     * middle over those feet, and it falls onto that side as a body pushed past its balance does, its legs sliding out
     * from under it the other way ({@link #LEGS_OUT}). Turned about its own middle instead, the torso pressed its legs
     * into the ground on that side, which pushed it back: a polar bear so rocked came back upright every time.
     */
    static void giveWay(ServerLevel level, ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, Rig rig, ServerSubLevel torso, Bone torsoBone,
                        @Nullable String hooked) {
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
        // dragged by a leg, it goes down away from that leg, which the pull draws out from under it: the leg ends on top,
        // free to lead, not pinned under the body
        ServerSubLevel hookedBody = hooked == null || hooked.equals(carcass.rootBone) ? null
                : container.getSubLevel(carcass.bones.get(hooked)) instanceof ServerSubLevel body && !body.isRemoved() ? body : null;
        if (hookedBody != null) {
            double off = across.dot(new Vector3d(hookedBody.logicalPose().position()).sub(torso.logicalPose().position()));
            if (Math.abs(off) > 0.02) {
                side = -Math.signum(off);
            }
        }
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
            Vector3d kick = new Vector3d(turn).cross(new Vector3d(at).sub(pivot));
            // and the legs under it slide out the other way, as legs giving way do: it goes down more where it stood
            // (turned over its feet alone, a cow set down standing lay 1.8 blocks from where it stood, 1.7 with its legs
            // sliding out at 3 blocks a second; at 5 a sheep went on over onto its back). Not while it is dragged: its
            // legs kicked out under a cow pulled by a hind leg, and it came round rear first a little less often (60 and
            // more degrees off 2 runs in 30, where it was none in 90 without)
            if (hooked == null && body != torso && at.y < middle.y) {
                kick.add(new Vector3d(across).mul(-LEGS_OUT));
            }
            physics.getPipeline().addLinearAndAngularVelocity(body, kick, turn);
        }
    }

    /** How fast the parts under its middle (its legs) slide out from under it as it goes over, blocks a second. */
    static final double LEGS_OUT = 3.0;

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
        double clear = Math.max(0.125, LIFTED * reach);
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
        // its legs reach well below it, and nothing is close under its lowest side: it is held up by its legs, not lying
        // on something
        List<Vector3d> lowest = new ArrayList<>(corners);
        lowest.sort((a, b) -> Double.compare(a.y, b.y));
        if (partsLow <= torsoLow - clear && unsupported(level, container, carcass, lowest.subList(0, 4), clear)) {
            return true;
        }
        // or it sits up like a dog, its hind end down and its front held up on straight front legs (the showcase's cow
        // struck in the face, tipped 15 degrees, its chest a third of a block up): only a body on four legs, and only its
        // front, since one pitched onto its nose with its rear up is how a blow from behind leaves it
        if (upright(torsoBone)) {
            return false;
        }
        Vector3d forward = model.transform(new Vector3d(0, 0, -1));
        List<Vector3d> belly = new ArrayList<>(corners);
        belly.sort((a, b) -> Double.compare(a.dot(up), b.dot(up)));
        belly = new ArrayList<>(belly.subList(0, 4));
        belly.sort((a, b) -> Double.compare(b.dot(forward), a.dot(forward)));
        List<Vector3d> front = belly.subList(0, 2);
        double frontLow = Math.min(front.get(0).y, front.get(1).y);
        double rearLow = Math.min(belly.get(2).y, belly.get(3).y);
        return frontLow > rearLow && frontLow - partsLow > clear && unsupported(level, container, carcass, front, clear);
    }


    /** Whether nothing solid is under any of these corners, as far down as {@code clear}. */
    private static boolean unsupported(ServerLevel level, ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, List<Vector3d> corners,
                                       double clear) {
        for (Vector3d corner : corners) {
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

    /**
     * Whether any part of it stands on a block that holds a carcass to be worked ({@link BBTags#HOLDS_CARCASSES}: a table,
     * a machine), in the world or on a ship: set down standing on a Surgery Table or a Mangler, a block wide, it would go
     * over the side and off it.
     */
    static boolean standsOnAHolder(ServerLevel level, ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, Rig rig) {
        Vector3d local = new Vector3d();
        for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
            Bone bone = rig.bone(entry.getKey()).orElse(null);
            if (bone == null || !(container.getSubLevel(entry.getValue()) instanceof ServerSubLevel body) || body.isRemoved()) {
                continue;
            }
            Vector3d low = null;
            for (Vector3d corner : corners(body, bone)) {
                if (low == null || corner.y < low.y) {
                    low = corner;
                }
            }
            Vector3d point = new Vector3d(low.x, low.y - 0.05, low.z);
            if (level.getBlockState(BlockPos.containing(point.x, point.y, point.z)).is(BBTags.HOLDS_CARCASSES)) {
                return true;
            }
            BoundingBox3d reach = new BoundingBox3d(point.x - 0.05, point.y - 0.05, point.z - 0.05, point.x + 0.05, point.y + 0.05, point.z + 0.05);
            for (SubLevel other : container.queryIntersecting(reach)) {
                if (!other.isRemoved() && !carcass.bones.containsValue(other.getUniqueId())) {
                    other.logicalPose().transformPositionInverse(point, local);
                    if (level.getBlockState(BlockPos.containing(local.x, local.y, local.z)).is(BBTags.HOLDS_CARCASSES)) {
                        return true;
                    }
                }
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

    /** Whether a torso stands upright, as a biped's does: taller, as the model stands, than it is long. */
    public static boolean upright(Bone torsoBone) {
        Quaterniond turn = new Quaterniond(torsoBone.rotation());
        double lowY = Double.MAX_VALUE, highY = -Double.MAX_VALUE, lowZ = Double.MAX_VALUE, highZ = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vector3d corner = turn.transform(new Vector3d((i & 1) == 0 ? torsoBone.boxMin().x : torsoBone.boxMax().x,
                    (i & 2) == 0 ? torsoBone.boxMin().y : torsoBone.boxMax().y, (i & 4) == 0 ? torsoBone.boxMin().z : torsoBone.boxMax().z));
            lowY = Math.min(lowY, corner.y);
            highY = Math.max(highY, corner.y);
            lowZ = Math.min(lowZ, corner.z);
            highZ = Math.max(highZ, corner.z);
        }
        return highY - lowY > highZ - lowZ;
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
