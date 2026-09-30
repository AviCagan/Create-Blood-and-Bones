package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.gametest.RigComparison.Numbers;
import com.avicagan.bloodandbones.gametest.RigComparison.Subject;
import com.avicagan.bloodandbones.registry.BBItems;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Map;
import java.util.function.Consumer;

/**
 * The scenarios the brief's physics goals are measured by (RigComparison has the tools). Each plays one carcass (or a
 * pair) out in its own test's arena and hands its numbers on once, when it is done; a scenario that cannot be played
 * out says why in {@link Numbers#broken}. The kills are played on the open ground ({@link RigComparison#OPEN_GROUND}),
 * the rest in the tests' own walled arena.
 */
final class RigScenarios {
    /** Facing south (the game's yaw 0): its right is -X. Facing east: yaw -90. Facing west: yaw 90. */
    static final float SOUTH = 0.0F;
    static final float EAST = -90.0F;
    static final float WEST = 90.0F;
    /** Where on the open ground a mob is killed, and how far off its killer stands. */
    static final Vec3 KILLED_AT = new Vec3(RigComparison.OPEN_MIDDLE, 2, RigComparison.OPEN_MIDDLE);
    static final double KILLER_OFF = 2.0;
    /** The ticks after a blow from behind in which the highest nose-down pitch is looked for. */
    static final int PITCH_WINDOW = 120;

    private RigScenarios() {
    }

    /**
     * Killed by a Meat Hook blow from its right side (the flank), two blocks off, on open ground. Criterion 1 (it
     * ragdolls at once, with proper weight and jointing, and does not sink into the floor) and criterion 4's flank (it
     * goes down sideways, away from the blow). How deep it sinks is taken twice: the deepest at any moment and once it
     * has settled.
     */
    static void killedFromTheFlank(GameTestHelper helper, EntityType<? extends Mob> type, Consumer<Numbers> ragdoll, Consumer<Numbers> flank) {
        RigComparison.killed(helper, type, KILLED_AT, SOUTH, KILLED_AT.add(-KILLER_OFF, 0, 0), s -> {
            Vector3d blow = new Vector3d(1, 0, 0);
            Vector3d start = s.torsoCentre();
            Vector3d upAtStart = s.up();
            double floor = helper.absolutePos(new BlockPos(0, RigComparison.FLOOR, 0)).getY();
            double standing = belly(s, floor);
            int[] t = {0};
            int[] motionStart = {-1};
            int[] stillRun = {0};
            int[] settled = {-1};
            double[] gap = {0.0};
            double[] sink = {0.0};
            double[] peak = {start.y};
            Numbers one = new Numbers();
            Numbers four = new Numbers();
            helper.onEachTick(() -> {
                if (settled[0] >= 0 || s.torso() == null) {
                    return;
                }
                int now = ++t[0];
                if (motionStart[0] < 0 && s.torsoSpeed() > 0.1) {
                    motionStart[0] = now;
                }
                if (now <= 100) {
                    gap[0] = Math.max(gap[0], s.jointGap());
                }
                sink[0] = Math.max(sink[0], s.sink());
                peak[0] = Math.max(peak[0], s.torsoCentre().y);
                stillRun[0] = now > 5 && s.still() ? stillRun[0] + 1 : 0;
                if (stillRun[0] >= RigComparison.SETTLE_RUN || now >= RigComparison.SETTLE_CAP) {
                    settled[0] = now;
                    Vector3d end = s.torsoCentre();
                    double mass = s.liveMass();
                    ServerSubLevel torso = s.torso();
                    one.put("motion_start_ticks", motionStart[0] < 0 ? now : motionStart[0], "ticks")
                            .put("settle_ticks", now, "ticks")
                            .put("pose_change_deg", s.poseChange(), "degrees")
                            .put("joint_gap_max", gap[0], "blocks")
                            .put("sink_max", sink[0], "blocks")
                            .put("sink_rest", s.sink(), "blocks")
                            .put("travel", Math.hypot(end.x - start.x, end.z - start.z), "blocks")
                            .put("peak_height", peak[0] - start.y, "blocks")
                            .put("total_mass", mass, "sable mass")
                            .put("generated_weight", RigManager.forEntity(s.carcass().entity, s.carcass().baby).map(r -> (double) r.weight()).orElse(Double.NaN), "sable mass")
                            .put("torso_mass_share", torso == null || mass <= 0 ? Double.NaN : torso.getMassTracker().getMass() / mass, "share")
                            .put("bodies", s.bodies().size(), "count")
                            .put("joints", s.carcass().joints.size(), "count");
                    Vector3d up = s.up();
                    Vector3d flat = new Vector3d(up.x, 0, up.z);
                    double tilt = RigComparison.angleDeg(up, new Vector3d(0, 1, 0));
                    double away = flat.length() < 1.0e-3 ? 180.0 : RigComparison.angleDeg(flat, blow);
                    // a carcass still on its feet leans some way or other: it has gone down away from the blow only
                    // when it is on its side too
                    String head = RigComparison.generatedHead(type);
                    one.put("head_below_neck", neckMinusHead(s), "blocks")
                            .put("head_ground", headOnTheGround(s, head, helper.absolutePos(new BlockPos(0, RigComparison.FLOOR, 0)).getY()) ? 1 : 0, "yes/no");
                    four.put("tilt_deg", tilt, "degrees")
                            .put("tilt_dir_err_deg", away, "degrees")
                            .put("down_away", tilt >= 45.0 && away <= 60.0 ? 1 : 0, "yes/no")
                            .put("travel_along_blow", new Vector3d(end).sub(start).dot(blow), "blocks")
                            .put("start_tilt_deg", RigComparison.angleDeg(upAtStart, new Vector3d(0, 1, 0)), "degrees");
                    down(four, s, standing, floor);
                    ragdoll.accept(one);
                    flank.accept(four);
                }
            });
        });
    }

    /**
     * Killed by a blow from behind, two blocks back, on open ground: criterion 4's other half (it pitches onto its nose).
     * Played until it has settled (or {@link RigComparison#SETTLE_CAP} ticks): the highest its torso's forward went
     * below the horizon in the first {@link #PITCH_WINDOW} ticks, and how it lies once settled: its pitch, how far its
     * up is from straight up (90 on its side, 180 on its back), how far it is rolled onto a side, whether its head is on
     * the ground.
     */
    static void killedFromBehind(GameTestHelper helper, EntityType<? extends Mob> type, Consumer<Numbers> done) {
        RigComparison.killed(helper, type, KILLED_AT, SOUTH, KILLED_AT.add(0, 0, -KILLER_OFF), s -> {
            String head = RigComparison.generatedHead(type);
            String headBody = head;
            Vector3d start = s.torsoCentre();
            int[] t = {0};
            double[] pitch = {-90.0};
            int[] headDown = {-1};
            int[] stillRun = {0};
            boolean[] finished = {false};
            double floor = helper.absolutePos(new BlockPos(0, RigComparison.FLOOR, 0)).getY();
            double standing = belly(s, floor);
            helper.onEachTick(() -> {
                if (finished[0] || s.torso() == null) {
                    return;
                }
                int now = ++t[0];
                if (now <= PITCH_WINDOW) {
                    pitch[0] = Math.max(pitch[0], pitchOf(s));
                    if (headDown[0] < 0 && headOnTheGround(s, headBody, floor)) {
                        headDown[0] = now;
                    }
                }
                stillRun[0] = now > 5 && s.still() ? stillRun[0] + 1 : 0;
                if (stillRun[0] >= RigComparison.SETTLE_RUN || now >= RigComparison.SETTLE_CAP) {
                    finished[0] = true;
                    Vector3d end = s.torsoCentre();
                    done.accept(down(new Numbers().put("pitch_peak_deg", pitch[0], "degrees")
                            .put("head_ground_tick", headDown[0] < 0 ? 200 : headDown[0], "ticks")
                            .put("settle_ticks", now, "ticks")
                            .put("pitch_rest_deg", pitchOf(s), "degrees")
                            .put("tilt_rest_deg", RigComparison.angleDeg(s.up(), new Vector3d(0, 1, 0)), "degrees")
                            .put("roll_rest_deg", rollOf(s), "degrees")
                            .put("head_ground_rest", headOnTheGround(s, headBody, floor) ? 1 : 0, "yes/no")
                            .put("travel", Math.hypot(end.x - start.x, end.z - start.z), "blocks"), s, standing, floor));
                }
            });
        });
    }

    /**
     * Killed by a blow in the face: the stand-in player two and a half blocks in front of it, looking at its head (the
     * usual kill, walking up to an animal), on open ground. Does it go down, or is it left standing on its legs, and how
     * far does it go from where it stood? A body with no head is struck at its middle.
     */
    /** The tick after a blow in the face at which how it lies is looked at. */
    static final int FACE_LOOK = 160;

    static void killedInTheFace(GameTestHelper helper, EntityType<? extends Mob> type, Consumer<Numbers> done) {
        String head = RigComparison.generatedHead(type);
        Vec3 killer = KILLED_AT.add(0, 0, KILLER_OFF + 0.5);
        Consumer<Subject> then = s -> {
            double floor = helper.absolutePos(new BlockPos(0, RigComparison.FLOOR, 0)).getY();
            double standing = belly(s, floor);
            Vector3d start = s.torsoCentre();
            int[] t = {0};
            int[] stillRun = {0};
            int[] settled = {-1};
            boolean[] finished = {false};
            helper.onEachTick(() -> {
                if (finished[0] || s.torso() == null) {
                    return;
                }
                int now = ++t[0];
                stillRun[0] = now > 5 && s.still() ? stillRun[0] + 1 : 0;
                if (settled[0] < 0 && stillRun[0] >= RigComparison.SETTLE_RUN) {
                    settled[0] = now;
                }
                // looked at a while after the blow, not the moment it first keeps still: one left standing keeps still
                // on its legs a moment before they give way
                if (now >= FACE_LOOK) {
                    finished[0] = true;
                    Vector3d end = s.torsoCentre();
                    done.accept(down(new Numbers().put("struck_head", head != null && head.equals(s.carcass().hitBone) ? 1 : 0, "yes/no")
                            .put("settle_ticks", settled[0] < 0 ? now : settled[0], "ticks")
                            .put("travel", Math.hypot(end.x - start.x, end.z - start.z), "blocks"), s, standing, floor));
                }
            });
        };
        if (head == null) {
            RigComparison.killed(helper, type, KILLED_AT, SOUTH, killer, then);
        } else {
            RigComparison.killedAt(helper, type, KILLED_AT, SOUTH, killer, head, then);
        }
    }

    /**
     * How a struck carcass lies once settled: tipped how far from upright, its torso how high off the ground, and whether
     * it was left standing on its legs (upright within 45 degrees, its torso still more than half as high as it stood and
     * more than an eighth of a block up), which a dead animal never is.
     *
     * @param standing how high its torso was off the ground as it stood
     */
    static Numbers down(Numbers n, Subject s, double standing, double floor) {
        double tilt = RigComparison.angleDeg(s.up(), new Vector3d(0, 1, 0));
        double belly = belly(s, floor);
        return n.put("tilt_rest_deg", tilt, "degrees")
                .put("belly_stand", standing, "blocks")
                .put("belly_rest", belly, "blocks")
                .put("stood", standing(tilt, belly, standing) ? 1 : 0, "yes/no");
    }

    /** Whether a carcass tipped so far and its torso so high off the ground, having stood {@code standing} high, stands. */
    static boolean standing(double tilt, double belly, double standing) {
        return tilt < 45.0 && belly > Math.max(0.5 * standing, 0.125);
    }

    /** How high the lowest corner of the torso's drawn box is off the floor, blocks. */
    static double belly(Subject s, double floor) {
        ServerSubLevel torso = s.torso();
        return torso == null ? Double.NaN : lowest(torso, s.bone(s.torsoBody())) - floor;
    }

    /** How far the torso's forward points below the horizon, degrees (negative above it). */
    static double pitchOf(Subject s) {
        Vector3d forward = s.forward();
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, -forward.y / Math.max(1.0e-9, forward.length())))));
    }

    /** How far the torso is rolled onto a side: its right's angle from the horizontal, 0 upright or on its back, 90 on its side. */
    static double rollOf(Subject s) {
        Vector3d right = s.right();
        return Math.toDegrees(Math.asin(Math.min(1.0, Math.abs(right.y) / Math.max(1.0e-9, right.length()))));
    }

    /** Whether the head's drawn box reaches within a tenth of a block of the floor's top. */
    static boolean headOnTheGround(Subject s, @org.jetbrains.annotations.Nullable String headBody, double floor) {
        if (headBody == null) {
            return false;
        }
        ServerSubLevel body = s.body(headBody);
        Bone bone = s.rig.bone(headBody).orElse(null);
        return body != null && bone != null && lowest(body, bone) <= floor + 0.1;
    }

    static double lowest(ServerSubLevel body, Bone bone) {
        Vector3d origin = CarcassAssembler.boneOriginInPlot(body, bone);
        double lowest = Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vector3d corner = new Vector3d((i & 1) == 0 ? bone.boxMin().x : bone.boxMax().x, (i & 2) == 0 ? bone.boxMin().y : bone.boxMax().y,
                    (i & 4) == 0 ? bone.boxMin().z : bone.boxMax().z).div(16.0).add(origin);
            lowest = Math.min(lowest, body.logicalPose().transformPosition(corner).y);
        }
        return lowest;
    }

    /**
     * Criterion 2: held three blocks up by its torso (pinned at once, as a resting carcass is), for 100 ticks. On its
     * right side: do the legs hang down? Upright: does the head droop?
     */
    static void heldUp(GameTestHelper helper, EntityType<? extends Mob> type, boolean onItsSide, Consumer<Numbers> done) {
        Subject s = RigComparison.assembled(helper, type, new Vec3(5.5, 5, 5.5), SOUTH);
        if (s == null) {
            done.accept(new Numbers().broken("no carcass"));
            return;
        }
        if (onItsSide) {
            // a quarter turn about its own forward axis takes its up to its right: it lies on its right side
            Vector3d forward = s.forward();
            RigComparison.turn(s, s.torsoCentre(), new Quaterniond().rotateAxis(Math.PI / 2.0, forward.x, forward.y, forward.z));
        }
        PhysicsConstraintHandle pin = RigComparison.pin(s);
        if (pin == null) {
            done.accept(new Numbers().broken("could not pin the torso"));
            return;
        }
        s.wake();
        String head = RigComparison.generatedHead(type);
        String headBody = head;
        double[] pitchAtStart = {headBody == null ? Double.NaN : headPitch(s, headBody)};
        // held still in the air it would fold into its resting form after three seconds and its limbs would go with it;
        // this is about how the limbs hang, so it is kept from folding
        helper.onEachTick(() -> s.carcass().stillTicks = 0);
        helper.runAfterDelay(100, () -> {
            Numbers n = new Numbers();
            if (onItsSide) {
                double sum = 0.0;
                int legs = 0;
                for (String leg : s.legBodies()) {
                    double a = RigComparison.angleDeg(s.down(leg), new Vector3d(0, -1, 0));
                    if (Double.isFinite(a)) {
                        sum += a;
                        legs++;
                    }
                }
                n.put("leg_hang_deg", legs == 0 ? Double.NaN : sum / legs, "degrees").put("legs", legs, "count");
            } else {
                n.put("head_droop_deg", headBody == null ? Double.NaN : headPitch(s, headBody) - pitchAtStart[0], "degrees");
            }
            if (pin.isValid()) {
                pin.remove();
            }
            done.accept(n);
        });
    }

    /** How far the head's forward points below the torso's forward, in the torso's own up and forward plane, degrees. */
    static double headPitch(Subject s, String headBody) {
        ServerSubLevel body = s.body(headBody);
        if (body == null) {
            return Double.NaN;
        }
        Vector3d headForward = s.modelToWorld(headBody, body).transform(new Vector3d(0, 0, -1));
        Vector3d forward = s.forward();
        Vector3d up = s.up();
        return Math.toDegrees(Math.atan2(-headForward.dot(up), headForward.dot(forward)));
    }

    /**
     * Criterion 3: hooked by the rearmost leg and dragged away (it must come round rear first, or feet first for a body
     * that stands upright), or by the head (it must follow head first), hooked at the middle of that part; the way the
     * body points is its head end (Subject#headEnd). The stand-in player hooks it from beside it, a block and a
     * half off its middle line on the side of the part, and walks the way the other end points, passing beside the
     * carcass (not through it: the stand-in is not solid, and a drag holds off while the hooked part touches its dragger,
     * so a path through the carcass carried it along under the player's feet): seven blocks in seventy ticks. Judged on
     * the second half of the walk, while it is being dragged (the middle of how far the hooked end is off the way it is
     * dragged, ticks 36 to 70), and once more at tick 80, stopped.
     */
    static void hooked(GameTestHelper helper, EntityType<? extends Mob> type, boolean byTheHead, Consumer<Numbers> done) {
        // hind leg: facing east at x 3, dragged east; head: facing east at x 8, dragged west
        Subject s = RigComparison.assembled(helper, type, new Vec3(byTheHead ? 7.5 : 3.5, 2, 5.5), EAST);
        if (s == null) {
            done.accept(new Numbers().broken("no carcass"));
            return;
        }
        String body = byTheHead ? RigComparison.generatedHead(type) : RigComparison.generatedLegs(type).stream().findFirst().orElse(null);
        Vector3d travel = byTheHead ? new Vector3d(-1, 0, 0) : new Vector3d(1, 0, 0);
        Vector3d partAt = body == null ? null : s.middle(body);
        Vector3d torsoAt = s.torsoCentre();
        // beside the part, on its side (the head, on the middle line, from the south)
        double side = partAt == null || byTheHead || Math.abs(partAt.z - torsoAt.z) < 0.05 ? 1.0 : Math.signum(partAt.z - torsoAt.z);
        double laneZ = helper.relativeVec(new Vec3(0, 0, torsoAt.z)).z + 1.5 * side;
        double fromX = partAt == null ? 5.5 : helper.relativeVec(new Vec3(partAt.x, 0, 0)).x;
        double toX = fromX + 7.0 * travel.x;
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        int[] t = {0};
        int[] turned = {-1};
        double[] gapSum = {0.0};
        int[] gapN = {0};
        java.util.List<Double> walking = new java.util.ArrayList<>();
        boolean[] finished = {false};
        boolean[] started = {false};
        float yaw = byTheHead ? RigScenarios.WEST : RigScenarios.EAST;
        // the tick listener is set now: one added from inside another test tick would break the game's own list of them
        helper.runAfterDelay(5, () -> {
            place(helper, player, fromX, 2, laneZ, yaw);
            if (body == null || !s.hook(player, body)) {
                finished[0] = true;
                done.accept(new Numbers().broken("could not hook the " + (byTheHead ? "head" : "rearmost leg")));
                return;
            }
            started[0] = true;
        });
        helper.onEachTick(() -> {
            if (finished[0] || !started[0]) {
                return;
            }
            int now = ++t[0];
            double x = fromX + (toX - fromX) * Math.min(1.0, now / 70.0);
            place(helper, player, x, 2, laneZ, yaw);
            CarcassDrag.tick(s.level, player);
            Vector3d leading = byTheHead ? s.headEnd() : new Vector3d(s.headEnd()).negate();
            leading.y = 0;
            double angle = RigComparison.angleDeg(leading, travel);
            if (turned[0] < 0 && angle < 45.0) {
                turned[0] = now;
            }
            if (now >= 36 && now <= 70) {
                walking.add(angle);
            }
            if (now % 4 == 0) {
                Vector3d tc = s.torsoCentre();
                Vector3d pc = body == null ? null : s.middle(body);
                com.avicagan.bloodandbones.BloodAndBones.LOGGER.info(String.format("[hookdbg] %s %s " + helper.absolutePos(BlockPos.ZERO).toShortString().replace(" ", "") + " t=%d ang=%.0f tilt=%.0f up=(%.2f,%.2f,%.2f) fwd=(%.2f,%.2f,%.2f) torso=(%.2f,%.2f,%.2f) part=(%.2f,%.2f,%.2f) player=%.2f",
                        RigComparison.mobName(type), byTheHead ? "head" : "leg", now, angle, RigComparison.angleDeg(s.up(), new Vector3d(0, 1, 0)), s.up().x, s.up().y, s.up().z,
                        s.forward().x, s.forward().y, s.forward().z,
                        helper.relativeVec(new Vec3(tc.x, tc.y, tc.z)).x, tc.y, helper.relativeVec(new Vec3(tc.x, tc.y, tc.z)).z,
                        pc == null ? 0 : helper.relativeVec(new Vec3(pc.x, pc.y, pc.z)).x, pc == null ? 0 : pc.y, pc == null ? 0 : helper.relativeVec(new Vec3(pc.x, pc.y, pc.z)).z, x));
            }
            CarcassDrag.Drag drag = CarcassDrag.current(player);
            if (drag != null && SubLevelContainer.getContainer(s.level).getSubLevel(drag.subLevel) instanceof ServerSubLevel held) {
                Vector3d point = held.logicalPose().transformPosition(drag.anchorPlot, new Vector3d());
                gapSum[0] += point.distance(CarcassDrag.debugTarget(player));
                gapN[0]++;
            }
            if (now >= 80) {
                finished[0] = true;
                boolean broke = !CarcassDrag.isDragging(player);
                CarcassDrag.stop(s.level, player);
                java.util.List<Double> sorted = walking.stream().sorted().toList();
                done.accept(new Numbers().put(byTheHead ? "head_first_deg" : "rear_first_deg", sorted.isEmpty() ? Double.NaN : sorted.get(sorted.size() / 2), "degrees")
                        .put("stopped_deg", angle, "degrees")
                        .put("turn_ticks", turned[0] < 0 ? 200 : turned[0], "ticks")
                        .put("mean_gap", gapN[0] == 0 ? Double.NaN : gapSum[0] / gapN[0], "blocks")
                        .put("drag_broke", broke ? 1 : 0, "yes/no"));
            }
        });
    }

    /** Move the stand-in player to a spot of the arena, looking along the way it walks, ready for the drag to read it. */
    static void place(GameTestHelper helper, Player player, double x, double y, double z, float yaw) {
        Vec3 world = helper.absoluteVec(new Vec3(x, y, z));
        player.setPos(world);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(0.0F);
        player.setOldPosAndRot();
    }

    /** Move the stand-in player to a spot of the arena, looking along the way it walks, ready for the drag to read it. */
    static void place(GameTestHelper helper, Player player, double x, double y, float yaw) {
        Vec3 world = helper.absoluteVec(new Vec3(x, y, 5.5));
        player.setPos(world);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(0.0F);
        player.setOldPosAndRot();
    }

    /**
     * Criterion 5: hung on a Shackle Hook the way a player hangs one, then at tick 80 knocked: a blow to the torso of the
     * same impulse whatever the code under it ({@link RigComparison#knock}). Do the limbs hang, does it swing, do the legs trail the
     * swing?
     */
    static void hanging(GameTestHelper helper, EntityType<? extends Mob> type, Consumer<Numbers> done) {
        Subject s = RigComparison.assembled(helper, type, new Vec3(5.5, 2, 5.5), SOUTH);
        if (s == null) {
            done.accept(new Numbers().broken("no carcass"));
            return;
        }
        BlockPos hookAt = RigComparison.hook(helper, new BlockPos(5, 5, 5));
        String head = RigComparison.generatedHead(type);
        String headBody = head;
        boolean[] hung = {false};
        Numbers n = new Numbers();
        helper.runAfterDelay(5, () -> {
            if (!RigComparison.hang(s, hookAt)) {
                done.accept(new Numbers().broken("could not hang it"));
                return;
            }
            hung[0] = true;
            n.put("hung_by_neck", RigComparison.hungByTheNeck(s, hookAt) ? 1 : 0, "yes/no");
        });
        Vector3d[] at80 = {null};
        Map<String, Vector3d> legsAt80 = new java.util.LinkedHashMap<>();
        Map<String, Double> lag = new java.util.LinkedHashMap<>();
        double[] amp = {0.0};
        int[] calm = {0};
        int[] decay = {-1};
        int[] t = {0};
        boolean[] finished = {false};
        helper.onEachTick(() -> {
            if (finished[0] || !hung[0]) {
                return;
            }
            int now = ++t[0];
            if (now == 80) {
                double sum = 0.0;
                int legs = 0;
                for (String leg : s.legBodies()) {
                    Vector3d down = s.down(leg);
                    legsAt80.put(leg, down);
                    double a = RigComparison.angleDeg(down, new Vector3d(0, -1, 0));
                    if (Double.isFinite(a)) {
                        sum += a;
                        legs++;
                    }
                }
                n.put("limb_hang_deg", legs == 0 ? Double.NaN : sum / legs, "degrees")
                        .put("head_hang_deg", headBody == null ? Double.NaN : s.poseChange(headBody), "degrees")
                        .put("body_off_upright_deg", RigComparison.angleDeg(s.headEnd(), new Vector3d(0, 1, 0)), "degrees");
                at80[0] = s.torsoCentre();
                double[] knocked = RigComparison.knock(s, new Vector3d(1, 0, 0));
                n.put("knock_impulse", knocked[0], "sable mass x blocks a second").put("knock_speed", knocked[1], "blocks a second");
            } else if (now > 80) {
                Vector3d c = s.torsoCentre();
                double d = Math.hypot(c.x - at80[0].x, c.z - at80[0].z);
                amp[0] = Math.max(amp[0], d);
                for (Map.Entry<String, Vector3d> leg : legsAt80.entrySet()) {
                    double a = RigComparison.angleDeg(s.down(leg.getKey()), leg.getValue());
                    if (Double.isFinite(a)) {
                        lag.merge(leg.getKey(), a, Math::max);
                    }
                }
                ServerSubLevel torso = s.torso();
                double speed = torso == null ? 0.0 : SubLevelContainer.getContainer(s.level).physicsSystem().getPhysicsHandle(torso).getLinearVelocity(new Vector3d()).length();
                calm[0] = now > 85 && speed < 0.1 ? calm[0] + 1 : 0;
                if (decay[0] < 0 && calm[0] >= 10) {
                    decay[0] = now - 80 - 10;
                }
                if (now >= 180) {
                    finished[0] = true;
                    n.put("swing_amp", amp[0], "blocks")
                            .put("swing_decay_ticks", decay[0] < 0 ? 100 : decay[0], "ticks")
                            .put("limb_lag_deg", lag.isEmpty() ? Double.NaN : lag.values().stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN), "degrees");
                    done.accept(n);
                }
            }
        });
    }

    /**
     * Criterion 6: dragged up a one-block step. The floor steps up a block from x 6 on; hooked at the middle of its torso
     * (or of its rearmost leg), it is dragged from x 4 to x 9 over 100 ticks, the player up on the step from x 6; 200
     * ticks in all.
     */
    static void upAStep(GameTestHelper helper, EntityType<? extends Mob> type, boolean byTheLeg, Consumer<Numbers> done) {
        for (int x = 6; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
        }
        Subject s = RigComparison.assembled(helper, type, new Vec3(3.0, 2, 5.5), EAST);
        if (s == null) {
            done.accept(new Numbers().broken("no carcass"));
            return;
        }
        String part = byTheLeg ? RigComparison.generatedLegs(type).stream().findFirst().orElse(null) : s.torsoBody();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        double stepTop = helper.absolutePos(new BlockPos(0, 3, 0)).getY();
        double stepEdge = helper.absoluteVec(new Vec3(6.5, 0, 0)).x;
        int[] t = {0};
        int[] cleared = {-1};
        int[] stuck = {0};
        double[] maxGap = {0.0};
        boolean[] finished = {false};
        boolean[] started = {false};
        helper.runAfterDelay(5, () -> {
            place(helper, player, 4.0, 2, EAST);
            if (part == null || !s.hook(player, part)) {
                finished[0] = true;
                done.accept(new Numbers().broken("could not hook it"));
                return;
            }
            started[0] = true;
        });
        helper.onEachTick(() -> {
            if (finished[0] || !started[0]) {
                return;
            }
            int now = ++t[0];
            double x = 4.0 + 5.0 * Math.min(1.0, now / 100.0);
            place(helper, player, x, x >= 6.0 ? 3 : 2, EAST);
            CarcassDrag.tick(s.level, player);
            Vector3d c = s.torsoCentre();
            if (cleared[0] < 0 && c.y > stepTop && c.x > stepEdge) {
                cleared[0] = now;
            }
            CarcassDrag.Drag drag = CarcassDrag.current(player);
            double gap = 0.0;
            if (drag != null && SubLevelContainer.getContainer(s.level).getSubLevel(drag.subLevel) instanceof ServerSubLevel held) {
                gap = held.logicalPose().transformPosition(drag.anchorPlot, new Vector3d()).distance(CarcassDrag.debugTarget(player));
            }
            maxGap[0] = Math.max(maxGap[0], gap);
            ServerSubLevel torso = s.torso();
            double flat = 0.0;
            if (torso != null) {
                Vector3d v = SubLevelContainer.getContainer(s.level).physicsSystem().getPhysicsHandle(torso).getLinearVelocity(new Vector3d());
                flat = Math.hypot(v.x, v.z);
            }
            if (gap > 1.5 && flat < 0.05) {
                stuck[0]++;
            }
            if (now >= 200) {
                finished[0] = true;
                boolean broke = !CarcassDrag.isDragging(player);
                CarcassDrag.stop(s.level, player);
                done.accept(new Numbers().put("cleared", cleared[0] >= 0 ? 1 : 0, "yes/no")
                        .put("clear_ticks", cleared[0] < 0 ? 200 : cleared[0], "ticks")
                        .put("stuck_ticks", stuck[0], "ticks")
                        .put("max_gap", maxGap[0], "blocks")
                        .put("drag_broke", broke ? 1 : 0, "yes/no"));
            }
        });
    }

    /**
     * Criterion 7: two of the same hung side by side; one has its rearmost leg cut off (three Cleaver cuts through the
     * joint, the leg taken away). 80 ticks later, how differently do they hang?
     */
    static void cutLimb(GameTestHelper helper, EntityType<? extends Mob> type, Consumer<Numbers> done) {
        Subject whole = RigComparison.assembled(helper, type, new Vec3(2.5, 2, 5.5), SOUTH);
        Subject cut = RigComparison.assembled(helper, type, new Vec3(7.5, 2, 5.5), SOUTH);
        if (whole == null || cut == null) {
            done.accept(new Numbers().broken("no carcass"));
            return;
        }
        BlockPos hookA = RigComparison.hook(helper, new BlockPos(2, 5, 5));
        BlockPos hookB = RigComparison.hook(helper, new BlockPos(7, 5, 5));
        double[] lost = {0.0};
        double[] total = {0.0};
        boolean[] hung = {false};
        helper.runAfterDelay(5, () -> {
            if (!RigComparison.hang(whole, hookA) || !RigComparison.hang(cut, hookB)) {
                done.accept(new Numbers().broken("could not hang them"));
                return;
            }
            hung[0] = true;
        });
        helper.runAfterDelay(40, () -> {
            if (!hung[0]) {
                return;
            }
            String leg = RigComparison.generatedLegs(type).stream().findFirst().orElse(null);
            total[0] = cut.liveMass();
            lost[0] = leg == null ? 0.0 : RigComparison.cutOff(cut, leg);
            cut.wake();
            whole.wake();
        });
        // how far the side that lost the leg rides up, against the whole one, degrees: the middle of ticks 200 to 240
        java.util.List<Double> rise = new java.util.ArrayList<>();
        String cutLeg = RigComparison.generatedLegs(type).stream().findFirst().orElse(null);
        for (int tick = 200; tick <= 240; tick++) {
            boolean last = tick == 240;
            helper.runAfterDelay(tick, () -> {
                if (!hung[0]) {
                    return;
                }
                if (cutLeg != null) {
                    rise.add(sideHeightDeg(cut, cutLeg) - sideHeightDeg(whole, cutLeg));
                }
                if (last) {
                    java.util.List<Double> sorted = rise.stream().filter(Double::isFinite).sorted().toList();
                    done.accept(new Numbers()
                            .put("tilt_change_deg", RigComparison.angleDeg(whole.up(), cut.up()), "degrees")
                            .put("cut_side_rise_deg", sorted.isEmpty() ? Double.NaN : sorted.get(sorted.size() / 2), "degrees")
                            .put("mass_lost_pct", total[0] <= 0 ? Double.NaN : 100.0 * lost[0] / total[0], "percent"));
                }
            });
        }
    }

    /** How far the torso's side a leg is on points above level, degrees (0 level, above 0 that side higher). */
    static double sideHeightDeg(Subject s, String leg) {
        ServerSubLevel torso = s.torso();
        if (torso == null) {
            return Double.NaN;
        }
        Vector3d side = s.modelToWorld(s.torsoBody(), torso).transform(new Vector3d(Math.signum(s.bone(leg).offset().x), 0, 0));
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, side.y))));
    }

    /** Height of where the neck meets the torso, less the height of the head's middle, in blocks. */
    static double neckMinusHead(Subject s) {
        ServerSubLevel torso = s.torso();
        com.avicagan.bloodandbones.carcass.CarcassJoints.Spec neck = com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity.jointTowardHead(s.carcass());
        String headBone = RigComparison.generatedHead(s.type);
        Vector3d head = headBone == null ? null : s.middle(headBone);
        if (torso == null || neck == null || head == null) {
            return Double.NaN;
        }
        return torso.logicalPose().transformPosition(neck.anchorParent(torso), new Vector3d()).y - head.y;
    }

    /**
     * Criterion 8, the part a single carcass shows: killed from the flank on open ground, it settles and folds into its
     * resting form (one body, costing nothing while it lies there). When did it fold, how many bodies were moving
     * meanwhile?
     */
    static void comesToRest(GameTestHelper helper, EntityType<? extends Mob> type, Consumer<Numbers> done) {
        RigComparison.killed(helper, type, KILLED_AT, SOUTH, KILLED_AT.add(-KILLER_OFF, 0, 0), s -> {
            int[] t = {0};
            int[] moving = {0};
            int[] active = {0};
            boolean[] finished = {false};
            helper.onEachTick(() -> {
                if (finished[0]) {
                    return;
                }
                int now = ++t[0];
                if (now == 5) {
                    active[0] = s.bodies().size();
                }
                var physics = SubLevelContainer.getContainer(s.level).physicsSystem();
                for (ServerSubLevel body : s.bodies().values()) {
                    if (physics.getPhysicsHandle(body).getLinearVelocity(new Vector3d()).length() > 0.01) {
                        moving[0]++;
                    }
                }
                if (s.carcass().resting || now >= 600) {
                    finished[0] = true;
                    done.accept(new Numbers().put("fold_ticks", s.carcass().resting ? now : 600, "ticks")
                            .put("bodies_active", active[0], "count")
                            .put("bodies_rest", s.bodies().size(), "count")
                            .put("moving_body_ticks", moving[0], "body ticks"));
                }
            });
        });
    }

    /**
     * Criterion 8, measured on the whole lineup at once: this mob is killed on open ground on the test's first tick,
     * alongside the other eleven in the same batch, and watched for {@link RigComparison#COST_TICKS} ticks. When did it
     * fold into its resting form, how many bodies did it have at tick 5 and at tick 500, and how many body ticks was a
     * body moving (over 0.01 blocks a second)? With {@code timer}, this test also writes down how long every server
     * tick took.
     */
    static void cost(GameTestHelper helper, EntityType<? extends Mob> type, int round, boolean timer, Consumer<Numbers> done) {
        String batch = RigComparison.costBatch(RigComparison.COST_CARCASSES, round);
        double[] times = new double[RigComparison.COST_TICKS + 1];
        RigComparison.killed(helper, type, KILLED_AT, SOUTH, KILLED_AT.add(-KILLER_OFF, 0, 0), s -> {
            int[] t = {0};
            int[] moving = {0};
            int[] active = {0};
            int[] rest = {0};
            int[] folded = {-1};
            helper.onEachTick(() -> {
                int now = ++t[0];
                if (now > RigComparison.COST_TICKS) {
                    return;
                }
                if (timer) {
                    times[now - 1] = RigComparison.lastTickMs(s.level);
                }
                Map<String, ServerSubLevel> bodies = s.bodies();
                if (now == 5) {
                    active[0] = bodies.size();
                }
                if (now == 500) {
                    rest[0] = bodies.size();
                }
                if (folded[0] < 0 && s.carcass().resting) {
                    folded[0] = now;
                }
                var physics = SubLevelContainer.getContainer(s.level).physicsSystem();
                for (ServerSubLevel body : bodies.values()) {
                    if (physics.getPhysicsHandle(body).getLinearVelocity(new Vector3d()).length() > 0.01) {
                        moving[0]++;
                    }
                }
                if (now == RigComparison.COST_TICKS) {
                    if (timer) {
                        times[now] = RigComparison.lastTickMs(s.level);
                        RigComparison.costTimes(batch, times);
                    }
                    done.accept(new Numbers().put("fold_ticks", folded[0] < 0 ? RigComparison.COST_TICKS : folded[0], "ticks")
                            .put("bodies_active", active[0], "count")
                            .put("bodies_rest", rest[0], "count")
                            .put("moving_body_ticks", moving[0], "body ticks"));
                }
            });
        });
    }

    /** Criterion 8's empty batch: open ground with nothing on it for as long, and (with {@code timer}) its tick times. */
    static void baseline(GameTestHelper helper, int round, boolean timer, Runnable done) {
        double[] times = new double[RigComparison.COST_TICKS + 1];
        int[] t = {0};
        helper.onEachTick(() -> {
            int now = ++t[0];
            if (now > RigComparison.COST_TICKS) {
                return;
            }
            if (timer) {
                times[now - 1] = RigComparison.lastTickMs(helper.getLevel());
            }
            if (now == RigComparison.COST_TICKS) {
                if (timer) {
                    times[now] = RigComparison.lastTickMs(helper.getLevel());
                    RigComparison.costTimes(RigComparison.costBatch(RigComparison.COST_BASELINE, round), times);
                }
                done.run();
            }
        });
    }
}
