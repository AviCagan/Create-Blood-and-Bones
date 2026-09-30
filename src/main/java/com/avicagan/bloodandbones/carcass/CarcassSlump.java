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
 * has its legs give way ({@link #giveWay}): it is tipped over the way it already leans, however little, its legs sliding
 * out the other way, and comes down onto that side. Nothing picks the side: a carcass struck on one side leans away
 * from the blow, one dragged leans the way the pull takes it, and one standing so squarely that nothing says which way
 * has its legs slide out from under it, which breaks that balance. That it is tipped at all, and not left to fold at
 * knees it does not have, is made up. Until it is down it is not let rest. A carcass dragged along on its feet gives way
 * however it moves (a sheep dragged by a hind leg slid along standing, facing the wrong way); one that stands upright
 * (a zombie) is pulled off its feet by the leg it is dragged by and is left to it. One balanced on end, on its rump or
 * its snout, goes over too ({@link #onEnd}).
 * <p>
 * It stands when its torso is within {@link #UPRIGHT} degrees of upright, some other part of it (a leg) reaches below the
 * torso by more than a quarter as far as its legs reach when it stands ({@link #LIFTED}), that part stands on something
 * (a carcass held up in the air, or floating, its legs hanging, is not standing), and nothing (the ground, a rack, a
 * deck, another carcass) is that close under the torso; or, on four legs, when it sits up on its front ones, the front of
 * its belly held up that high, its rear lower. A carcass lying on its belly, or across a Bleeding Rack with its legs
 * hanging, is down already; one in water floats or sinks and is never standing. Hung on a hook it is lifted off its legs
 * and never gives way; standing with its feet on a block that holds a carcass to be worked (a table, a machine:
 * {@link BBTags#HOLDS_CARCASSES}) it is held there.
 * <p>
 * Its legs give way at most {@link #MOST} times while it stands, and then a carcass wedged upright somewhere is let rest
 * as it is. The count starts again once it has been down a while ({@link #DOWN_TICKS}), rests or is woken from resting,
 * is hung, or is taken up or let go by a drag.
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
    /** How far from straight up or down the spine of a body on four legs may point and still be balanced on end, degrees. */
    static final double ON_END = 30.0;
    /**
     * How fast the legs of one on four legs slide out from under it as it goes over, blocks a second, the other way. Its
     * middle then stays nearer where it stood: set down standing, a cow lay 2.1 blocks from there with them kept under
     * it, 1.7 at 3 blocks a second, 0.6 to 1.2 at 7; at 9 a sheep went on sliding, 1.2.
     */
    static final double LEGS_OUT = 7.0;
    /**
     * Standing too squarely to go either way, its legs slide out from under it this fast, blocks a second, and each time
     * it still stands {@link #SPLAY_MORE} faster: only to break the balance, so that the next time it leans some way (at
     * 7 a cow so set off went on over onto its back; at 3 a sheep splayed its legs, and then went over them and slid two
     * blocks).
     */
    static final double SPLAY = 1.0;
    static final double SPLAY_MORE = 1.0;
    /**
     * How it is turned over its edge: a little more than it takes to carry its middle over it, at least
     * {@link #SPIN_LEAST} radians a second, and each time it is still up a little more.
     */
    static final double TIP = 1.1;
    static final double SPIN_LEAST = 0.8;
    static final double SPIN_MORE = 0.5;
    /** Its torso slower than this, blocks a second, is nearly still. */
    private static final double NEARLY_STILL = 0.5;
    /** Times the legs give way while it stands before a carcass that still stands (wedged upright somewhere) is let rest as it is. */
    static final int MOST = 6;
    /** Ticks a carcass must be down before the count of times its legs gave way starts again. */
    static final int DOWN_TICKS = 40;

    private CarcassSlump() {
    }

    /** Starts the count afresh: it has come to rest or been woken, been hung, or been taken up or let go by a drag. */
    static void reset(CarcassSavedData.Carcass carcass) {
        carcass.standingTicks = 0;
        carcass.slumps = 0;
        carcass.tips = 0;
        carcass.downTicks = 0;
    }

    /**
     * Called each tick for a carcass that is loose, or dragged ({@code dragged}: then it gives way however it moves, as
     * it is pulled along on its feet). Returns true while it stands on its legs, so it is not let rest yet.
     */
    static boolean tick(ServerLevel level, ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, Rig rig, boolean dragged) {
        if (dragged != carcass.slumpDragged) {
            // taken up or let go: dragged or loose, it starts its count again
            reset(carcass);
            carcass.slumpDragged = dragged;
        }
        ServerSubLevel torso = container.getSubLevel(carcass.bones.get(carcass.rootBone)) instanceof ServerSubLevel body && !body.isRemoved() ? body : null;
        Bone torsoBone = rig.bone(carcass.rootBone).orElse(null);
        // one that stands upright (a zombie) is pulled off its feet by the leg it is dragged by, and falls feet first
        boolean onEnd = torso != null && torsoBone != null && !dragged && !inLiquid(container, carcass) && onEnd(level, container, carcass, torso, torsoBone);
        if (torso == null || torsoBone == null || dragged && upright(torsoBone) || inLiquid(container, carcass)
                || !onEnd && !standing(level, container, carcass, rig, torso, torsoBone) || standsOnAHolder(level, container, carcass, rig)) {
            carcass.standingTicks = 0;
            if (++carcass.downTicks >= DOWN_TICKS) {
                carcass.slumps = 0;
                carcass.tips = 0;
            }
            return false;
        }
        carcass.downTicks = 0;
        if (carcass.slumps >= MOST) {
            return false;
        }
        SubLevelPhysicsSystem physics = container.physicsSystem();
        // nearly still where it stands: on a ship under way, on the deck (as it really moves: CarcassFloat)
        Vector3d speed = new Vector3d();
        CarcassFloat.velocity(level, physics, torso, speed, new Vector3d());
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
        com.avicagan.bloodandbones.BloodAndBones.LOGGER.debug("Carcass {} ({}) left {}: its legs give way ({} of at most {}){}", carcass.id, carcass.entity,
                onEnd ? "balanced on end" : "standing", carcass.slumps, MOST, dragged ? ", dragged" : "");
        giveWay(level, container, carcass, rig, torso, torsoBone, onEnd, dragged);
        return true;
    }

    /** Whether any part of the carcass is in water or another liquid (CarcassFloat): it floats or sinks, and does not stand. */
    private static boolean inLiquid(ServerSubLevelContainer container, CarcassSavedData.Carcass carcass) {
        for (UUID id : carcass.bones.values()) {
            if (CarcassFloat.inLiquid(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Its legs give way, and it goes over the way it already leans, however little: nothing picks a side for it. On four
     * legs, over onto the side its torso leans to, or failing that the side its weight lies over its feet; standing
     * upright (a zombie), the way its weight lies over its feet, or failing that the way its torso leans; balanced on end,
     * the way its weight lies over the end it rests on, or failing that the way its top end leans out. Standing so
     * squarely that nothing says which way (a carcass built standing, not struck), its legs slide out from under it, each
     * the way it already points out from under its middle, which breaks the balance for the next time.
     */
    static void giveWay(ServerLevel level, ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, Rig rig, ServerSubLevel torso, Bone torsoBone,
                        boolean onEnd, boolean dragged) {
        SubLevelPhysicsSystem physics = container.physicsSystem();
        // its middle, by weight, and its parts with the corners they reach lowest
        List<ServerSubLevel> bodies = new ArrayList<>();
        List<List<Vector3d>> bodyCorners = new ArrayList<>();
        Vector3d middle = new Vector3d();
        double mass = 0.0;
        double ground = Double.MAX_VALUE;
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
            List<Vector3d> corners = corners(body, bone);
            for (Vector3d corner : corners) {
                ground = Math.min(ground, corner.y);
            }
            bodies.add(body);
            bodyCorners.add(corners);
        }
        if (mass <= 0.0 || bodies.isEmpty()) {
            return;
        }
        middle.div(mass);
        // where it stands: the middle of the corners on the ground
        Vector3d feet = new Vector3d();
        int count = 0;
        for (List<Vector3d> corners : bodyCorners) {
            for (Vector3d corner : corners) {
                if (corner.y < ground + FOOT) {
                    feet.add(corner);
                    count++;
                }
            }
        }
        feet.div(Math.max(1, count));
        Vector3d weight = new Vector3d(middle.x - feet.x, 0.0, middle.z - feet.z);
        Quaterniond model = modelToWorld(torso, torsoBone);
        Vector3d up = model.transform(new Vector3d(0, -1, 0));
        Vector3d forward = model.transform(new Vector3d(0, 0, -1));
        Vector3d way = new Vector3d();
        double slide = 0.0;
        if (onEnd) {
            // on end its spine is near straight up, so how it leans is which way its top end points out
            way.set(weight.lengthSquared() >= LEAST * LEAST ? weight : new Vector3d(forward.x, 0.0, forward.z).mul(Math.signum(forward.y)));
        } else if (upright(torsoBone)) {
            way.set(weight.lengthSquared() >= LEAST * LEAST ? weight : new Vector3d(up.x, 0.0, up.z));
        } else {
            // onto a side: about its length, which way across it it leans
            Vector3d along = new Vector3d(forward.x, 0.0, forward.z);
            if (along.lengthSquared() > 1.0e-6) {
                Vector3d across = new Vector3d(0, 1, 0).cross(along.normalize()).normalize();
                double lean = across.dot(up.x, 0.0, up.z);
                if (Math.abs(lean) < LEAST) {
                    lean = across.dot(weight);
                }
                if (Math.abs(lean) >= LEAST) {
                    way.set(across).mul(Math.signum(lean));
                }
            }
            // not while it is dragged: its legs kicked out under a sheep or a cow pulled by a hind leg, and it came round
            // rear first less often (the sheep 61 degrees off where it was 9 without, over four runs)
            slide = dragged ? 0.0 : LEGS_OUT;
        }
        if (way.lengthSquared() >= LEAST * LEAST) {
            // a little harder each time it has been tipped and still stands
            goOver(level, physics, torso, bodies, bodyCorners, middle, ground, way.normalize(), carcass.tips++, slide);
            return;
        }
        // so square that nothing says which way: its legs slide out from under it
        double out = SPLAY + SPLAY_MORE * Math.max(0, carcass.slumps - carcass.tips - 1);
        for (int i = 0; i < bodies.size(); i++) {
            ServerSubLevel body = bodies.get(i);
            if (body == torso) {
                continue;
            }
            Vector3d low = null;
            for (Vector3d corner : bodyCorners.get(i)) {
                if (low == null || corner.y < low.y) {
                    low = corner;
                }
            }
            var tracker = body.getMassTracker();
            Vector3d at = tracker.isInvalid() ? new Vector3d(body.logicalPose().position())
                    : body.logicalPose().transformPosition(new Vector3d(tracker.getCenterOfMass()));
            // a part above its middle (a head held up) is not a leg under it
            if (low == null || at.y >= middle.y) {
                continue;
            }
            Vector3d away = new Vector3d(low.x - middle.x, 0.0, low.z - middle.z);
            if (away.length() < 0.02) {
                continue;
            }
            physics.getPipeline().wakeUp(body);
            physics.getPipeline().addLinearAndAngularVelocity(body, away.normalize().mul(out), new Vector3d());
        }
        physics.getPipeline().wakeUp(torso);
    }

    /** The least lean, blocks of the torso's up per block, or offset of its weight over its feet, blocks, that says which way it goes. */
    private static final double LEAST = 1.0e-4;

    /**
     * Turns the whole carcass over the edge of what it stands on the way {@code way} (level, of length one), all its parts
     * together, so no joint fights it, just fast enough to carry its middle over that edge; it falls on over as a body
     * pushed past its balance does. Its legs slide out the other way at {@code slide}, blocks a second, so it goes down
     * nearer where it stood. Turned about its own middle instead, the torso pressed its legs into the ground on that side,
     * which pushed it back: a polar bear so rocked came back upright every time.
     */
    private static void goOver(ServerLevel level, SubLevelPhysicsSystem physics, ServerSubLevel torso, List<ServerSubLevel> bodies,
                               List<List<Vector3d>> bodyCorners, Vector3d middle, double ground, Vector3d way, int more, double slide) {
        double outer = -Double.MAX_VALUE;
        for (List<Vector3d> corners : bodyCorners) {
            for (Vector3d corner : corners) {
                if (corner.y < ground + FOOT) {
                    outer = Math.max(outer, way.dot(corner));
                }
            }
        }
        // the line it turns about: level, square to the way it goes, at the ground, through its outermost foot that way
        Vector3d pivot = new Vector3d(middle).add(new Vector3d(way).mul(outer - way.dot(middle)));
        pivot.y = ground;
        double height = Math.max(0.05, middle.y - ground);
        double inside = Math.max(0.0, outer - way.dot(middle));
        double reach = Math.sqrt(height * height + inside * inside);
        // what raising its middle over the edge takes: a body falling from there comes down at the speed it would anyway
        double gravity = DimensionPhysicsData.getGravity(level).length();
        double spin = Math.max(SPIN_LEAST, TIP * Math.sqrt(2.0 * gravity * (reach - height)) / reach) + SPIN_MORE * more;
        Vector3d turn = new Vector3d(0, 1, 0).cross(way).mul(spin);
        for (ServerSubLevel body : bodies) {
            var tracker = body.getMassTracker();
            Vector3d at = tracker.isInvalid() ? new Vector3d(body.logicalPose().position())
                    : body.logicalPose().transformPosition(new Vector3d(tracker.getCenterOfMass()));
            physics.getPipeline().wakeUp(body);
            Vector3d kick = new Vector3d(turn).cross(new Vector3d(at).sub(pivot));
            if (body != torso && at.y < middle.y) {
                kick.add(new Vector3d(way).mul(-slide));
            }
            physics.getPipeline().addLinearAndAngularVelocity(body, kick, turn);
        }
    }

    /**
     * Whether a body on four legs is balanced on end: its spine within {@link #ON_END} degrees of straight up or down, on
     * its rump or its snout, that end resting on something (a pig struck in the face was left so, belly toward its killer).
     */
    static boolean onEnd(ServerLevel level, ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, ServerSubLevel torso, Bone torsoBone) {
        if (upright(torsoBone)) {
            return false;
        }
        Vector3d forward = modelToWorld(torso, torsoBone).transform(new Vector3d(0, 0, -1));
        if (Math.abs(forward.y) < Math.cos(Math.toRadians(ON_END))) {
            return false;
        }
        Vector3d low = null;
        for (Vector3d corner : corners(torso, torsoBone)) {
            if (low == null || corner.y < low.y) {
                low = corner;
            }
        }
        return low != null && solidAt(level, container, carcass, new Vector3d(low.x, low.y - 0.05, low.z));
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
        double clear = Math.max(0.125, LIFTED * reach);
        List<Vector3d> corners = corners(torso, torsoBone);
        double torsoLow = Double.MAX_VALUE;
        for (Vector3d corner : corners) {
            torsoLow = Math.min(torsoLow, corner.y);
        }
        double partsLow = Double.MAX_VALUE;
        List<Vector3d> partCorners = new ArrayList<>();
        for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
            if (entry.getKey().equals(carcass.rootBone)) {
                continue;
            }
            Bone bone = rig.bone(entry.getKey()).orElse(null);
            if (bone != null && container.getSubLevel(entry.getValue()) instanceof ServerSubLevel body && !body.isRemoved()) {
                for (Vector3d corner : corners(body, bone)) {
                    partsLow = Math.min(partsLow, corner.y);
                    partCorners.add(corner);
                }
            }
        }
        // its feet stand on something: held up in the air (on a pin, in a machine's grip) its legs only hang
        boolean onItsFeet = false;
        for (Vector3d corner : partCorners) {
            if (corner.y < partsLow + FOOT && (solidAt(level, container, carcass, new Vector3d(corner.x, corner.y - 0.05, corner.z))
                    || solidAt(level, container, carcass, new Vector3d(corner.x, corner.y - FOOT, corner.z)))) {
                onItsFeet = true;
                break;
            }
        }
        if (!onItsFeet) {
            return false;
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
     * Whether it stands with its feet on a block that holds a carcass to be worked ({@link BBTags#HOLDS_CARCASSES}: a
     * table, a machine), in the world or on a ship: set down standing on a Surgery Table or a Mangler, a block wide, it
     * would go over the side and off it. Its feet are the corners it reaches lowest with, and most of them must be on
     * such a block: a cow standing on the floor beside a table, its head drooped onto the table top or a hoof on the
     * rack's tray, stands on the floor.
     */
    static boolean standsOnAHolder(ServerLevel level, ServerSubLevelContainer container, CarcassSavedData.Carcass carcass, Rig rig) {
        List<Vector3d> corners = new ArrayList<>();
        double ground = Double.MAX_VALUE;
        for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
            Bone bone = rig.bone(entry.getKey()).orElse(null);
            if (bone != null && container.getSubLevel(entry.getValue()) instanceof ServerSubLevel body && !body.isRemoved()) {
                for (Vector3d corner : corners(body, bone)) {
                    ground = Math.min(ground, corner.y);
                    corners.add(corner);
                }
            }
        }
        int feet = 0;
        int held = 0;
        Vector3d local = new Vector3d();
        for (Vector3d corner : corners) {
            if (corner.y >= ground + FOOT) {
                continue;
            }
            feet++;
            Vector3d point = new Vector3d(corner.x, corner.y - 0.05, corner.z);
            if (level.getBlockState(BlockPos.containing(point.x, point.y, point.z)).is(BBTags.HOLDS_CARCASSES)) {
                held++;
                continue;
            }
            BoundingBox3d reach = new BoundingBox3d(point.x - 0.05, point.y - 0.05, point.z - 0.05, point.x + 0.05, point.y + 0.05, point.z + 0.05);
            for (SubLevel other : container.queryIntersecting(reach)) {
                if (!other.isRemoved() && !carcass.bones.containsValue(other.getUniqueId())) {
                    other.logicalPose().transformPositionInverse(point, local);
                    if (level.getBlockState(BlockPos.containing(local.x, local.y, local.z)).is(BBTags.HOLDS_CARCASSES)) {
                        held++;
                        break;
                    }
                }
            }
        }
        return feet > 0 && held * 2 > feet;
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

    /** The eight corners of a body's drawn box, in the world (for tests). */
    public static List<Vector3d> cornersOf(ServerSubLevel body, Bone bone) {
        return corners(body, bone);
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
