package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAim;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassJoints;
import com.avicagan.bloodandbones.carcass.CarcassPartBlock;
import com.avicagan.bloodandbones.carcass.CarcassRest;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity;
import com.avicagan.bloodandbones.carcass.Tissue;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.gametest.RigComparison.Numbers;
import com.avicagan.bloodandbones.gametest.RigComparison.Subject;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBItems;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The brief's physics, one test a sentence ("Physics: what I actually want"), each held to a direction or an amount: a
 * carcass hooked by a hind leg comes round rear first and one hooked by the head follows head first; a blow to the flank
 * lands it on its side, away from the blow, and one from behind lands it nose down; lying, its head rests below its neck;
 * hung, it swings when knocked, and with a leg off it hangs lower on the side that kept its leg; dragged by a hind leg it
 * gets up a one-block step. Then the parts the physics stands on: a lying carcass is hooked by the part aimed at, the
 * killing blow lands on the part it hits, bone and plate weigh more than flesh, and the drag's slowdown follows the mass
 * actually on the hook (docs/ARCHITECTURE-PROPOSAL.md 15.21).
 * <p>
 * The scenarios are the physics measurement's own (RigScenarios), played on a cow; each test only looks at the carcass it
 * made. Each was run many times over with {@code -Dbloodandbones.debug.repeat} to show it holds.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class PhysicsTests {
    private static final EntityType<? extends Mob> COW = EntityType.COW;

    // ---------------------------------------------------------------- where you hook it

    /** "Hook a hind leg and walk away and the animal comes round arse-first": dragged the way its head points, it turns. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void hindLegHookComesRoundRearFirst(GameTestHelper helper) {
        RigScenarios.hooked(helper, COW, false, n -> {
            require(helper, n);
            helper.assertTrue(n.get("drag_broke") == 0, "the drag let go");
            helper.assertTrue(n.get("rear_first_deg") <= 60.0, "hooked by a hind leg and dragged the way its head pointed, its rear should come round to "
                    + "lead, but while dragged it stayed " + fmt(n.get("rear_first_deg")) + " degrees off the way it went");
            helper.succeed();
        });
    }

    /** "Hook the head and it follows head-first": dragged the way its tail points, it turns to follow its head. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void headHookFollowsHeadFirst(GameTestHelper helper) {
        RigScenarios.hooked(helper, COW, true, n -> {
            require(helper, n);
            helper.assertTrue(n.get("drag_broke") == 0, "the drag let go");
            helper.assertTrue(n.get("head_first_deg") <= 45.0, "hooked by the head and dragged the way its tail pointed, it should follow head first, but "
                    + "while dragged its head stayed " + fmt(n.get("head_first_deg")) + " degrees off the way it went");
            helper.succeed();
        });
    }

    /** "It can be dragged up a one-block step without jamming", hooked by a hind leg. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void draggedByHindLegUpAStep(GameTestHelper helper) {
        RigScenarios.upAStep(helper, COW, true, n -> {
            require(helper, n);
            helper.assertTrue(n.get("drag_broke") == 0, "the drag let go");
            helper.assertTrue(n.get("cleared") == 1, "dragged by a hind leg it should come up the one-block step, but its torso stayed below it");
            helper.succeed();
        });
    }

    /**
     * A carcass lying still has one body, its torso; its legs and head are drawn from where they lay. A Meat Hook used at
     * a drawn hind leg (the look passing through it to the floor behind) hooks that leg, unfolding the carcass, not its
     * torso; used at the drawn head, the head.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void lyingCarcassHooksThePartAimedAt(GameTestHelper helper) {
        Subject s = RigComparison.assembled(helper, COW, new Vec3(5.5, 2, 5.5), RigScenarios.SOUTH);
        if (s == null) {
            helper.fail("no carcass");
            return;
        }
        Player player = standIn(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        String[] parts = {"right_hind_leg", "head"};
        int[] next = {0};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            CarcassSavedData.Carcass carcass = s.carcass();
            if (done[0] || !carcass.resting || CarcassDrag.isDragging(player)) {
                return;
            }
            String part = parts[next[0]];
            Vector3d at = drawnMiddle(s, part);
            if (at == null) {
                helper.fail("the resting carcass does not draw its " + part);
                return;
            }
            // stand off from the part, away from the torso's middle, and look at it
            Vector3d torso = s.torsoCentre();
            Vector3d out = new Vector3d(at.x - torso.x, 0.0, at.z - torso.z);
            if (out.lengthSquared() < 1.0e-4) {
                out.set(1.0, 0.0, 0.0);
            }
            out.normalize().mul(2.5);
            player.setPos(at.x + out.x, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), at.z + out.z);
            RigComparison.lookAt(player, new Vec3(at.x, at.y, at.z));
            player.setOldPosAndRot();
            // a right click there, as the game sends it: the look goes through the drawn part to the floor behind
            if (!use(player)) {
                helper.fail("a Meat Hook used at the drawn " + part + " of a resting carcass did nothing");
                return;
            }
            CarcassDrag.Drag drag = CarcassDrag.current(player);
            if (drag == null || s.carcass().resting) {
                helper.fail("hooking the drawn " + part + " should unfold the carcass and drag it");
                return;
            }
            if (!drag.bone.equals(part)) {
                helper.fail("aimed at the drawn " + part + ", the hook took the " + drag.bone);
                return;
            }
            ServerSubLevel hooked = s.body(part);
            Bone bone = s.bone(part);
            Vector3d[] box = com.avicagan.bloodandbones.carcass.CarcassAim.plotBox(hooked, bone);
            if (drag.anchorPlot.x < box[0].x - 0.05 || drag.anchorPlot.y < box[0].y - 0.05 || drag.anchorPlot.z < box[0].z - 0.05
                    || drag.anchorPlot.x > box[1].x + 0.05 || drag.anchorPlot.y > box[1].y + 0.05 || drag.anchorPlot.z > box[1].z + 0.05) {
                helper.fail("the hook should go into the " + part + " where it was aimed, but it is at " + drag.anchorPlot + " outside its box");
                return;
            }
            CarcassDrag.stop(s.level, player);
            if (++next[0] == parts.length) {
                done[0] = true;
                helper.succeed();
            }
        });
    }

    /**
     * A wall stops the hook. A cow lies still with a wall between a player and its drawn right hind leg, which the player
     * looks at, within reach, and right-clicks with a Meat Hook, as the game sends it: at the wall, then in the air (a
     * stone wall has no use of its own, so the game uses the item as well). The carcass stays folded. The same with the
     * wall a Sable sub-level (the side of a ship), whose block the game names in its plot, far off; and while the player
     * drags another carcass, which they keep. With the wall gone, the same click hooks the leg.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void meatHookDoesNotHookThroughAWall(GameTestHelper helper) {
        Subject lying = RigComparison.assembled(helper, COW, new Vec3(5.5, 2, 5.5), RigScenarios.SOUTH);
        Subject other = RigComparison.assembled(helper, COW, new Vec3(8.5, 2, 8.5), RigScenarios.SOUTH);
        if (lying == null || other == null) {
            helper.fail("no carcass");
            return;
        }
        ServerLevel level = helper.getLevel();
        Player player = standIn(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        String part = "right_hind_leg";
        List<BlockPos> wall = new ArrayList<>();
        ServerSubLevel[] ship = {null};
        int[] step = {0};
        helper.onEachTick(() -> {
            if (step[0] == 0) {
                if (!lying.carcass().resting || !other.carcass().resting) {
                    return;
                }
                // stand three and a half blocks out from the drawn leg, away from the torso's middle, and look at it
                Vector3d at = drawnMiddle(lying, part);
                helper.assertTrue(at != null, "the resting carcass does not draw its " + part);
                Vector3d torso = lying.torsoCentre();
                Vector3d out = new Vector3d(at.x - torso.x, 0.0, at.z - torso.z).normalize().mul(3.5);
                player.setPos(at.x + out.x, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), at.z + out.z);
                RigComparison.lookAt(player, new Vec3(at.x, at.y, at.z));
                player.setOldPosAndRot();
                // with nothing in the way the look meets the leg, within the hook's reach
                CarcassAim.RestingHit open = CarcassAim.nearestResting(level, player.getEyePosition(), player.getLookAngle(), CarcassDrag.HOOK_REACH);
                helper.assertTrue(open != null && lying.carcass().id.equals(open.root().carcassId()) && open.hit().bone().equals(part),
                        "with nothing in the way the look should meet the drawn " + part);
                // a stone wall across the look, halfway, two blocks high
                Vec3 eye = player.getEyePosition();
                for (int i = 0; i <= 10; i++) {
                    Vec3 on = eye.lerp(new Vec3(at.x, at.y, at.z), 0.45 + 0.01 * i);
                    for (int dy = 0; dy <= 1; dy++) {
                        BlockPos stone = BlockPos.containing(on).above(dy);
                        if (!wall.contains(stone)) {
                            level.setBlock(stone, Blocks.STONE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                            wall.add(stone);
                        }
                    }
                }
                helper.assertTrue(CarcassDrag.hookReach(player) < open.hit().distance() - 0.5, "the wall should be in the way of the look");
                helper.assertFalse(use(player), "a Meat Hook used at a wall should not reach the carcass lying behind it");
                helper.assertTrue(lying.carcass().resting && !CarcassDrag.isDragging(player), "hooked a carcass through a wall");
                // dragging another carcass, a use at the wall keeps hold of it (only a carcass in reach lets it go)
                helper.assertTrue(other.hook(player, other.torsoBody()), "could not hook the other cow");
                use(player);
                helper.assertTrue(CarcassDrag.isDragging(player) && other.carcass().id.equals(CarcassDrag.current(player).carcass),
                        "dragging a carcass, a use at a wall with another lying behind it let go");
                CarcassDrag.stop(level, player);
                // the wall made a sub-level, as the side of a ship is
                BlockPos lo = wall.get(0);
                BlockPos hi = wall.get(0);
                for (BlockPos stone : wall) {
                    lo = new BlockPos(Math.min(lo.getX(), stone.getX()), Math.min(lo.getY(), stone.getY()), Math.min(lo.getZ(), stone.getZ()));
                    hi = new BlockPos(Math.max(hi.getX(), stone.getX()), Math.max(hi.getY(), stone.getY()), Math.max(hi.getZ(), stone.getZ()));
                }
                ship[0] = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level, wall.get(0), wall,
                        new dev.ryanhcode.sable.companion.math.BoundingBox3i(lo, hi));
                helper.assertTrue(ship[0] != null && !ship[0].isRemoved() && level.getBlockState(wall.get(0)).isAir(), "the wall did not become a sub-level");
                CarcassAssembler.bindColliders(level, ship[0]);
                step[0] = 1;
            } else if (step[0] == 1) {
                // the game names a block of a sub-level in its plot
                BlockHitResult seen = sight(player);
                helper.assertTrue(seen.getType() == HitResult.Type.BLOCK && dev.ryanhcode.sable.Sable.HELPER.getContaining(level, seen.getBlockPos()) == ship[0],
                        "the look should meet the sub-level's wall");
                helper.assertFalse(use(player), "a Meat Hook used at a ship's side should not reach the carcass lying behind it");
                helper.assertTrue(lying.carcass().resting && !CarcassDrag.isDragging(player), "hooked a carcass through a ship's side");
                SubLevelContainer.getContainer(level).removeSubLevel(ship[0], dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
                step[0] = 2;
            } else if (step[0] == 2) {
                // nothing in the way now: the same click hooks the leg
                helper.assertTrue(use(player), "with the wall gone, a Meat Hook used at the drawn " + part + " did nothing");
                CarcassDrag.Drag drag = CarcassDrag.current(player);
                helper.assertTrue(drag != null && drag.bone.equals(part) && !lying.carcass().resting, "with the wall gone the " + part + " should be hooked");
                CarcassDrag.stop(level, player);
                step[0] = 3;
                helper.succeed();
            }
        });
    }

    /** What the player's look meets first, as the game picks it: a block's outline, within their reach. */
    static BlockHitResult sight(Player player) {
        Vec3 eye = player.getEyePosition();
        return player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(player.blockInteractionRange())), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
    }

    /**
     * A stand-in player, as GameTestHelper#makeMockPlayer makes one, but not a client's own: that one says it is the local
     * player, and a use it makes is taken for a client's by other mods listening (Create Simulated reaches for the client
     * then, which a server does not have).
     */
    static Player standIn(GameTestHelper helper) {
        return new Player(helper.getLevel(), BlockPos.ZERO, 0.0F, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
    }

    /**
     * A right click with what the player holds in their main hand, as the game sends it to the server: at the block their
     * look meets, if any is within reach, and, unless that use was taken, in the air (NeoForge's events, which every
     * mod's handlers hear, with nothing but the events played).
     *
     * @return whether a handler took the use
     */
    static boolean use(Player player) {
        BlockHitResult seen = sight(player);
        if (seen.getType() == HitResult.Type.BLOCK
                && net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, seen.getBlockPos(), seen).isCanceled()) {
            return true;
        }
        return net.neoforged.neoforge.common.CommonHooks.onItemRightClick(player, InteractionHand.MAIN_HAND) != null;
    }

    /** Where a part of a resting carcass is drawn: the middle of its box, placed by its remembered pose on the torso. */
    @org.jetbrains.annotations.Nullable
    private static Vector3d drawnMiddle(Subject s, String part) {
        CarcassSavedData.RestPose pose = s.carcass().restPoses.get(part);
        ServerSubLevel torso = s.torso();
        if (pose == null || torso == null) {
            return null;
        }
        Bone bone = s.bone(part);
        Vector3d middle = new Vector3d(bone.boxMin()).add(new Vector3d(bone.boxMax())).div(32.0);
        pose.orientation().transform(middle).add(pose.position()).add(CarcassAssembler.boneOriginInPlot(torso, s.bone(s.torsoBody())));
        return torso.logicalPose().transformPosition(middle, new Vector3d());
    }

    // ---------------------------------------------------------------- where the blow lands

    /** "An animal shot in the flank goes down sideways": killed from its right, it ends on its side, fallen away from the blow. */
    @GameTest(template = "open_ground", timeoutTicks = 400)
    public static void flankBlowLandsItOnItsSide(GameTestHelper helper) {
        RigScenarios.killedFromTheFlank(helper, COW, ragdoll -> {
        }, n -> {
            require(helper, n);
            helper.assertTrue(n.get("tilt_deg") >= 45.0, "killed from the flank it should go down on its side, but it lies tilted only "
                    + fmt(n.get("tilt_deg")) + " degrees");
            helper.assertTrue(n.get("tilt_dir_err_deg") <= 60.0, "it should fall away from the blow, but went down " + fmt(n.get("tilt_dir_err_deg"))
                    + " degrees off the way the blow went");
            helper.succeed();
        });
    }

    /**
     * "One hit from behind pitches onto its nose": killed from behind, it pitches forward onto its nose (its front well
     * down, its head striking the ground) and comes to rest front down, not rolled onto a side.
     */
    @GameTest(template = "open_ground", timeoutTicks = 300)
    public static void blowFromBehindLandsItNoseDown(GameTestHelper helper) {
        RigScenarios.killedFromBehind(helper, COW, n -> {
            require(helper, n);
            helper.assertTrue(n.get("pitch_peak_deg") >= 30.0, "hit from behind it should pitch forward onto its nose, but its front went only "
                    + fmt(n.get("pitch_peak_deg")) + " degrees down");
            helper.assertTrue(n.get("head_ground_tick") < RigScenarios.PITCH_WINDOW, "hit from behind its head should strike the ground as it goes down");
            helper.assertTrue(n.get("pitch_rest_deg") >= 10.0, "hit from behind it should come to rest front down, but lies with its front "
                    + fmt(n.get("pitch_rest_deg")) + " degrees down");
            helper.assertTrue(n.get("roll_rest_deg") <= 30.0, "hit from behind it should go down forward, not onto a side, but lies rolled "
                    + fmt(n.get("roll_rest_deg")) + " degrees");
            helper.succeed();
        });
    }

    /**
     * The killing blow lands on the part it hits, where it hits it: a Meat Hook swung at a standing cow's head from in
     * front lands on its head, one at its flank on its body.
     */
    @GameTest(template = "open_ground", timeoutTicks = 100)
    public static void killingBlowLandsOnThePartItHits(GameTestHelper helper) {
        Vec3 at = RigScenarios.KILLED_AT;
        // facing south: the head is to the south of the middle, low; a killer in front, looking at the head's middle
        Mob cow = RigComparison.standing(helper, COW, at, RigScenarios.SOUTH);
        Bone head = RigManager.forEntity(BuiltInRegistries.ENTITY_TYPE.getKey(COW)).flatMap(rig -> rig.bone("head")).orElseThrow();
        Vec3 feet = cow.position();
        Vec3 headAt = new Vec3(feet.x, feet.y + 1.501 - (head.offset().y + 1.0) / 16.0, feet.z - head.offset().z / 16.0 + 0.2);
        kill(helper, cow, feet.add(0.0, 0.0, 2.5), headAt, "head");
        Mob other = RigComparison.standing(helper, COW, at.add(0, 0, -8), RigScenarios.SOUTH);
        kill(helper, other, other.position().add(-2.0, 0.0, 0.0), other.getBoundingBox().getCenter(), "body");
        helper.succeed();
    }

    /**
     * A Meat Hook's killing blow to a cow's face, the usual kill from in front, lands on its head, a light part of it:
     * the head snaps back, no faster than a blow may set the spot it lands on moving, and the cow goes down about where
     * it stood, its joints holding. Sized for the whole cow and put on its head alone, the blow once set the head moving
     * at 23 blocks a second and the cow slid 1.7 blocks; now the head goes at 7 and the cow 1.2 (much of it folding up).
     */
    @GameTest(template = "open_ground", timeoutTicks = 200)
    public static void blowToTheHeadSnapsItBackWithoutFlingingIt(GameTestHelper helper) {
        Mob cow = RigComparison.standing(helper, COW, RigScenarios.KILLED_AT, RigScenarios.SOUTH);
        Bone head = RigManager.forEntity(BuiltInRegistries.ENTITY_TYPE.getKey(COW)).flatMap(rig -> rig.bone("head")).orElseThrow();
        Vec3 feet = cow.position();
        Vec3 headAt = new Vec3(feet.x, feet.y + 1.501 - (head.offset().y + 1.0) / 16.0, feet.z - head.offset().z / 16.0 + 0.2);
        Subject s = new Subject(helper, COW);
        s.carcass = kill(helper, cow, feet.add(0.0, 0.0, 2.5), headAt, "head");
        s.rig = RigManager.forCarcass(s.carcass).orElseThrow();
        watchStruck(helper, s, "head", 0, () -> {
        }, 100, CarcassAssembler.BLOW_MAX_STRUCK + 0.5, 1.5);
    }

    /**
     * Strike a carcass on one part at a tick, then watch it for some ticks: that part's fastest point never goes faster
     * than {@code maxSpeed}, its joints hold (none comes a quarter block apart), and it stays about where it was struck
     * (its torso no more than {@code maxTravel} from where it was, nor rising half a block).
     */
    static void watchStruck(GameTestHelper helper, Subject s, String struck, int strikeAt, Runnable strike, int ticks, double maxSpeed, double maxTravel) {
        Vector3d[] start = {null};
        double[] fastest = {0.0};
        double[] gap = {0.0};
        double[] travel = {0.0};
        double[] rise = {0.0};
        int[] t = {0};
        helper.onEachTick(() -> {
            if (t[0] > strikeAt + ticks) {
                return;
            }
            int now = t[0]++ - strikeAt;
            if (now < 0) {
                return;
            }
            if (now == 0) {
                start[0] = s.torsoCentre();
                strike.run();
                return;
            }
            fastest[0] = Math.max(fastest[0], fastestPoint(s, struck));
            gap[0] = Math.max(gap[0], s.jointGap());
            Vector3d at = s.torsoCentre();
            travel[0] = Math.max(travel[0], Math.hypot(at.x - start[0].x, at.z - start[0].z));
            rise[0] = Math.max(rise[0], at.y - start[0].y);
            if (now == ticks) {
                BloodAndBones.LOGGER.info("[physics] struck on the {}: its fastest point {} blocks a second, joints {} apart at most, torso {} off and {} up",
                        struck, fmt(fastest[0]), fmt(gap[0]), fmt(travel[0]), fmt(rise[0]));
                helper.assertTrue(fastest[0] > 0.3, "struck on the " + struck + ", it should have moved");
                helper.assertTrue(fastest[0] <= maxSpeed, "struck on the " + struck + ", that part should go no faster than " + fmt(maxSpeed)
                        + " blocks a second, but went " + fmt(fastest[0]));
                helper.assertTrue(gap[0] <= 0.25, "struck on the " + struck + ", its joints should hold, but one came " + fmt(gap[0]) + " blocks apart");
                helper.assertTrue(travel[0] <= maxTravel && rise[0] <= 0.5, "struck on the " + struck + ", it should stay about where it was, but its torso "
                        + "went " + fmt(travel[0]) + " blocks off and " + fmt(rise[0]) + " up");
                helper.succeed();
            }
        });
    }

    /** How fast the fastest corner of a part's box is going, in blocks a second: its body's speed and its turn. */
    static double fastestPoint(Subject s, String part) {
        ServerSubLevel body = s.body(part);
        if (body == null || body.getMassTracker().isInvalid()) {
            return 0.0;
        }
        var handle = SubLevelContainer.getContainer(s.level).physicsSystem().getPhysicsHandle(body);
        Vector3d linear = handle.getLinearVelocity(new Vector3d());
        Vector3d angular = handle.getAngularVelocity(new Vector3d());
        Vector3d centre = body.logicalPose().transformPosition(new Vector3d(body.getMassTracker().getCenterOfMass()), new Vector3d());
        Vector3d[] box = CarcassAim.plotBox(body, s.bone(part));
        double fastest = 0.0;
        for (int i = 0; i < 8; i++) {
            Vector3d corner = body.logicalPose().transformPosition(new Vector3d((i & 1) == 0 ? box[0].x : box[1].x, (i & 2) == 0 ? box[0].y : box[1].y,
                    (i & 4) == 0 ? box[0].z : box[1].z));
            fastest = Math.max(fastest, new Vector3d(angular).cross(corner.sub(centre)).add(linear).length());
        }
        return fastest;
    }

    /** The carcass a killing blow of a stand-in player makes of a mob, which must land on the part expected. */
    private static CarcassSavedData.Carcass kill(GameTestHelper helper, Mob mob, Vec3 from, Vec3 lookAt, String expected) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        player.setPos(from);
        RigComparison.lookAt(player, lookAt);
        java.util.Set<java.util.UUID> before = new java.util.HashSet<>();
        CarcassSavedData.get(helper.getLevel()).all().forEach(c -> before.add(c.id));
        mob.hurt(helper.getLevel().damageSources().playerAttack(player), 10000.0F);
        CarcassSavedData.Carcass made = null;
        for (CarcassSavedData.Carcass c : CarcassSavedData.get(helper.getLevel()).all()) {
            if (!before.contains(c.id)) {
                made = c;
            }
        }
        helper.assertTrue(made != null, "the kill made no carcass");
        helper.assertTrue(expected.equals(made.hitBone), "a blow aimed at the " + expected + " landed on the " + made.hitBone);
        helper.assertTrue(made.hitPoint != null, "the blow should know where on the " + expected + " it landed");
        return made;
    }

    // ---------------------------------------------------------------- limbs hang, the head lolls

    /** "The head lolls": a carcass lying where it fell rests its head lower than where its neck meets its body. */
    @GameTest(template = "open_ground", timeoutTicks = 400)
    public static void lyingHeadRestsBelowItsNeck(GameTestHelper helper) {
        RigComparison.killed(helper, COW, RigScenarios.KILLED_AT, RigScenarios.SOUTH, RigScenarios.KILLED_AT.add(-RigScenarios.KILLER_OFF, 0, 0), s -> {
            int[] still = {0};
            int[] t = {0};
            boolean[] finished = {false};
            helper.onEachTick(() -> {
                if (finished[0] || s.torso() == null) {
                    return;
                }
                t[0]++;
                still[0] = t[0] > 5 && s.still() ? still[0] + 1 : 0;
                if (still[0] < RigComparison.SETTLE_RUN && t[0] < RigComparison.SETTLE_CAP) {
                    return;
                }
                finished[0] = true;
                double drop = neckMinusHead(s);
                helper.assertTrue(drop >= HEAD_DROP, "lying where it fell its head should rest below its neck, but the head's middle is only "
                        + fmt(drop) + " blocks below where the neck meets the body");
                helper.assertTrue(headDown(s, helper), "lying where it fell its head should rest on the ground");
                helper.succeed();
            });
        });
    }

    /**
     * How far below where its neck meets the body a lying cow's head's middle rests, at least, in blocks. On its side the
     * neck meets the body half the body's width up, and the head, half as wide, can drop an eighth of a block before its
     * cheek is on the ground; it does, most of that way.
     */
    private static final double HEAD_DROP = 0.04;

    /** Height of where the neck meets the torso, less the height of the head's middle. */
    static double neckMinusHead(Subject s) {
        return RigScenarios.neckMinusHead(s);
    }

    /** Whether a carcass's head rests on the ground (its lowest corner within a tenth of a block of the floor's top). */
    static boolean headDown(Subject s, GameTestHelper helper) {
        return RigScenarios.headOnTheGround(s, RigComparison.generatedHead(s.type), helper.absolutePos(new BlockPos(0, RigComparison.FLOOR, 0)).getY());
    }

    // ---------------------------------------------------------------- hanging

    /** "Swinging when knocked": a hung carcass punched from the side swings away from the punch and back. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void hungCarcassSwingsWhenKnocked(GameTestHelper helper) {
        Subject s = RigComparison.assembled(helper, COW, new Vec3(5.5, 2, 5.5), RigScenarios.SOUTH);
        if (s == null) {
            helper.fail("no carcass");
            return;
        }
        BlockPos hookAt = RigComparison.hook(helper, new BlockPos(5, 5, 5));
        helper.runAfterDelay(5, () -> helper.assertTrue(RigComparison.hang(s, hookAt), "could not hang it"));
        Vector3d[] atPunch = {null};
        Vector3d way = new Vector3d(-1, 0, 0);
        double[] away = {0.0};
        double[] awayEarly = {0.0};
        double[] back = {0.0};
        int[] t = {0};
        helper.onEachTick(() -> {
            int now = ++t[0];
            if (now == 90) {
                // a full-strength punch at its torso's middle, from two blocks east of it
                Vector3d middle = s.middle(s.torsoBody());
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                player.setPos(middle.x + 2.0, middle.y - 1.2, middle.z);
                RigComparison.lookAt(player, new Vec3(middle.x, middle.y, middle.z));
                charge(player);
                ServerSubLevel torso = s.torso();
                BlockPos cell = torso.getPlot().getCenterBlock();
                atPunch[0] = s.torsoCentre();
                s.level.getBlockState(cell).attack(s.level, cell, player);
            } else if (now > 90 && atPunch[0] != null) {
                Vector3d moved = new Vector3d(s.torsoCentre()).sub(atPunch[0]);
                double along = moved.x * way.x + moved.z * way.z;
                away[0] = Math.max(away[0], along);
                back[0] = Math.min(back[0], along);
                if (now <= 110) {
                    awayEarly[0] = Math.max(awayEarly[0], along);
                }
                if (now == 200) {
                    helper.assertTrue(awayEarly[0] >= 0.1, "punched from the east it should first swing west, away from the punch, but moved only "
                            + fmt(awayEarly[0]) + " blocks that way");
                    helper.assertTrue(away[0] - back[0] >= SWING, "knocked, it should swing, but it moved over only " + fmt(away[0] - back[0]) + " blocks");
                    helper.assertTrue(back[0] <= -0.02, "it should swing back past where it hung, but came back only to " + fmt(back[0]));
                    helper.succeed();
                }
            }
        });
    }

    /** How far a punched cow's torso swings, end to end, at least, in blocks. */
    private static final double SWING = 0.15;

    /**
     * A hung cow punched on a hind leg, near eye level: the leg swings on its hip, about as fast as a punch may set the
     * spot it lands on moving (its far end a little faster, as it swings about the hip), the joints hold and the cow stays
     * on its hook. A punch sized for the whole cow and put on its leg alone once set that leg moving at 26 blocks a second
     * (7.4 after the hip took hold of it, a tick on); now at 3 (2 to 2.5 a tick on).
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void punchOnAHungLegSwingsItWithoutFlingingIt(GameTestHelper helper) {
        Subject s = RigComparison.assembled(helper, COW, new Vec3(5.5, 2, 5.5), RigScenarios.SOUTH);
        if (s == null) {
            helper.fail("no carcass");
            return;
        }
        BlockPos hookAt = RigComparison.hook(helper, new BlockPos(5, 5, 5));
        helper.runAfterDelay(5, () -> helper.assertTrue(RigComparison.hang(s, hookAt), "could not hang it"));
        String leg = "right_hind_leg";
        watchStruck(helper, s, leg, 90, () -> {
            // a full-strength punch at the leg's middle, from two blocks east of it, standing on the floor
            Vector3d middle = s.middle(leg);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(middle.x + 2.0, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), middle.z);
            RigComparison.lookAt(player, new Vec3(middle.x, middle.y, middle.z));
            charge(player);
            BlockPos cell = s.body(leg).getPlot().getCenterBlock();
            s.level.getBlockState(cell).attack(s.level, cell, player);
        }, 60, 1.5 * CarcassRest.PUNCH_MAX_STRUCK, 0.3);
    }

    /**
     * "Weight matters": the same full-strength punch to the middle of a chicken and of a ravager, both lying where they
     * fell. A punch is one push, whatever it lands on, so the ravager, some fifty times the chicken's weight, is barely
     * moved, and the chicken is knocked away (as fast as a punch may set the spot it lands on moving).
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void punchMovesALightCarcassMoreThanAHeavyOne(GameTestHelper helper) {
        Subject chicken = RigComparison.assembled(helper, EntityType.CHICKEN, new Vec3(2.5, 2, 5.5), RigScenarios.SOUTH);
        Subject ravager = RigComparison.assembled(helper, EntityType.RAVAGER, new Vec3(7.0, 2, 5.5), RigScenarios.SOUTH);
        if (chicken == null || ravager == null) {
            helper.fail("no carcass");
            return;
        }
        helper.runAfterDelay(10, () -> {
            double[] speeds = new double[2];
            Subject[] both = {chicken, ravager};
            for (int i = 0; i < 2; i++) {
                Subject s = both[i];
                // from two blocks south of its torso's middle, looking at it
                Vector3d middle = s.middle(s.torsoBody());
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                player.setPos(middle.x, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), middle.z + 2.0);
                RigComparison.lookAt(player, new Vec3(middle.x, middle.y, middle.z));
                charge(player);
                Vector3d before = momentum(s);
                BlockPos cell = s.torso().getPlot().getCenterBlock();
                s.level.getBlockState(cell).attack(s.level, cell, player);
                speeds[i] = momentum(s).sub(before).length() / s.liveMass();
            }
            BloodAndBones.LOGGER.info("[physics] one punch sets a chicken moving at {} and a ravager at {} blocks a second", fmt(speeds[0]), fmt(speeds[1]));
            helper.assertTrue(speeds[1] > 0.0 && speeds[1] <= CarcassRest.PUNCH / ravager.liveMass() + 1.0e-3, "a punch should move a ravager as one push "
                    + "moves its weight, at " + fmt(CarcassRest.PUNCH / ravager.liveMass()) + " blocks a second, but moved it at " + fmt(speeds[1]));
            helper.assertTrue(speeds[0] >= 4.0 * speeds[1], "the same punch should knock a chicken away far faster than a ravager, but moved it at "
                    + fmt(speeds[0]) + " blocks a second against the ravager's " + fmt(speeds[1]));
            helper.succeed();
        });
    }

    /** A carcass's momentum: each body's velocity by its mass, summed. */
    static Vector3d momentum(Subject s) {
        var physics = SubLevelContainer.getContainer(s.level).physicsSystem();
        Vector3d sum = new Vector3d();
        for (ServerSubLevel body : s.bodies().values()) {
            sum.add(physics.getPhysicsHandle(body).getLinearVelocity(new Vector3d()).mul(body.getMassTracker().getMass()));
        }
        return sum;
    }

    /** Ready a stand-in player's swing: a fresh one has only just swung and hits at a tenth of its strength. */
    static void charge(Player player) {
        net.neoforged.fml.util.ObfuscationReflectionHelper.setPrivateValue(LivingEntity.class, player, 100, "attackStrengthTicker");
    }

    /**
     * "Hanging differently once a leg's been cut off": two cows hung side by side, the right hind leg cut off one. The
     * whole one hangs level across its hips; the other hangs lower on its left, the side that kept its leg, its stump's
     * side riding up (its weight is no longer balanced across the hook). Hung by the neck, the whole of it swings round
     * under the hook to put its weight below it again, so the tilt is small (about a degree and a half for a cow, whose
     * hind leg is a sixteenth of it), but it is always that way.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void legOffHangsLowerOnThatSide(GameTestHelper helper) {
        Subject whole = RigComparison.assembled(helper, COW, new Vec3(2.5, 2, 5.5), RigScenarios.SOUTH);
        Subject cut = RigComparison.assembled(helper, COW, new Vec3(7.5, 2, 5.5), RigScenarios.SOUTH);
        if (whole == null || cut == null) {
            helper.fail("no carcass");
            return;
        }
        BlockPos hookA = RigComparison.hook(helper, new BlockPos(2, 5, 5));
        BlockPos hookB = RigComparison.hook(helper, new BlockPos(7, 5, 5));
        helper.runAfterDelay(5, () -> helper.assertTrue(RigComparison.hang(whole, hookA) && RigComparison.hang(cut, hookB), "could not hang them"));
        helper.runAfterDelay(40, () -> {
            RigComparison.cutOff(cut, "right_hind_leg");
            cut.wake();
            whole.wake();
        });
        List<Double> wholeSide = new ArrayList<>();
        List<Double> rise = new ArrayList<>();
        int[] t = {0};
        helper.onEachTick(() -> {
            int now = ++t[0];
            if (now < 200) {
                return;
            }
            // how far each one's right side points above level, degrees. A hung cow's head falls to one side or the other
            // of its neck, which tips the whole of it a degree or so that way; the whole cow with its head fallen the other
            // way from the cut one's is its mirror image, so it is compared mirrored (its right side as high as its left).
            double w = RigScenarios.sideHeightDeg(whole, "right_hind_leg");
            wholeSide.add(w);
            boolean sameSide = headSide(whole) == headSide(cut);
            rise.add(RigScenarios.sideHeightDeg(cut, "right_hind_leg") - (sameSide ? w : -w));
            if (now == 240) {
                double level = median(wholeSide);
                double up = median(rise);
                BloodAndBones.LOGGER.info("[physics] hung cows: the whole one's right side {} degrees above level; with its right hind leg off it rides {} "
                        + "degrees higher (heads fallen to their {} / {}; cut one has {}, head ends {} / {}, centres {} / {})", fmt(level), fmt(up),
                        headSide(whole) > 0 ? "right" : "left", headSide(cut) > 0 ? "right" : "left", cut.carcass().bones.keySet(),
                        whole.headEnd(), cut.headEnd(), whole.torsoCentre(), cut.torsoCentre());
                helper.assertTrue(Math.abs(level) <= LEVEL, "a whole cow should hang level across its hips, but it is tipped " + fmt(level) + " degrees");
                helper.assertTrue(up >= RIDES_UP, "with its right hind leg off it should hang lower on its left, the side that kept its leg, but its right "
                        + "side is only " + fmt(up) + " degrees higher than a whole cow's");
                helper.succeed();
            }
        });
    }

    /** Which side of its torso a hung carcass's head has fallen to: 1 its right, -1 its left. */
    private static double headSide(Subject s) {
        ServerSubLevel torso = s.torso();
        String head = RigComparison.generatedHead(s.type);
        if (torso == null || head == null) {
            return 0.0;
        }
        Vector3d right = s.modelToWorld(s.torsoBody(), torso).transform(new Vector3d(Math.signum(s.bone("right_hind_leg").offset().x), 0, 0));
        return Math.signum(new Vector3d(s.middle(head)).sub(s.torsoCentre()).dot(right));
    }

    /** How level a whole hung cow's hips are, and how much higher its stump's side rides with a hind leg off, in degrees. */
    private static final double LEVEL = 2.0;
    private static final double RIDES_UP = 0.8;

    // ---------------------------------------------------------------- weight

    /** Bone and plate weigh more than flesh for their size, as their groups say: a skeleton's bone, a golem's plate. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void boneAndPlateWeighMoreThanFlesh(GameTestHelper helper) {
        Map<EntityType<? extends Mob>, Tissue> made = new java.util.LinkedHashMap<>();
        made.put(EntityType.ZOMBIE, Tissue.FLESH);
        made.put(EntityType.SKELETON, Tissue.BONE);
        made.put(EntityType.IRON_GOLEM, Tissue.PLATE);
        List<Subject> subjects = new ArrayList<>();
        int x = 2;
        for (EntityType<? extends Mob> type : made.keySet()) {
            Subject s = RigComparison.assembled(helper, type, new Vec3(x + 0.5, 2, 5.5), RigScenarios.SOUTH);
            helper.assertTrue(s != null, "no " + RigComparison.mobName(type) + " carcass");
            subjects.add(s);
            x += 3;
        }
        helper.runAfterDelay(5, () -> {
            int i = 0;
            for (Map.Entry<EntityType<? extends Mob>, Tissue> entry : made.entrySet()) {
                Subject s = subjects.get(i++);
                String mob = RigComparison.mobName(entry.getKey());
                Tissue tissue = entry.getValue();
                helper.assertTrue(PartsData.SERVER.resolve(s.carcass().entity, false).tissue() == tissue, "a " + mob + "'s groups should make it of " + tissue);
                ServerSubLevel torso = s.torso();
                helper.assertTrue(s.level.getBlockState(torso.getPlot().getCenterBlock()).getBlock() instanceof CarcassPartBlock cell && cell.tissue() == tissue,
                        "a " + mob + "'s cells should be of " + tissue);
                double perSize = s.liveMass() / s.rig.weight();
                helper.assertTrue(Math.abs(perSize - tissue.density) <= 0.05 * tissue.density, "a " + mob + " should weigh " + tissue.density
                        + " times its size as flesh, but weighs " + fmt(perSize) + " times");
            }
            // carried by its size, not its weight: a skeleton's skull, half as heavy again as a zombie's head, is carried as that is
            helper.assertTrue(subjects.get(1).body("head").getMassTracker().getMass() > subjects.get(0).body("head").getMassTracker().getMass() * 1.4,
                    "a skeleton's skull should weigh more than a zombie's head");
            helper.assertTrue(CarcassButchery.canPickUp(subjects.get(0).level, subjects.get(0).carcass(), "head")
                    && CarcassButchery.canPickUp(subjects.get(1).level, subjects.get(1).carcass(), "head"), "a skeleton's skull should be carried as a zombie's head is");
            helper.succeed();
        });
    }

    /**
     * The carcass cells' masses, by size and tissue, stay cheap to load. Sable sends each physics properties file to every
     * joining client as one packet, which a client refuses over 2 MB (it disconnects); and each time it applies them (for
     * every world as it loads, and on each client as it joins or reloads) it matches every state of a block against every
     * override of its file. So all the carcass blocks together must stay near what the one carcass block cost before bone
     * and plate (its 4,096 sizes by 4,096 overrides): flesh weighs every size, bone and plate only the sizes the rigs make.
     * Every rig's cells, grown or baby, are weighed in every tissue, so a datapack may make any rigged mob bone or plate;
     * a size no rig makes is built of flesh instead of weighing as a whole block. How long applying them takes is logged.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cellMassesAreCheapToLoad(GameTestHelper helper) {
        long matches = 0;
        long nanos = 0;
        int files = 0;
        for (var definition : dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader.INSTANCE.getDefinitions()) {
            if (!definition.selector().id().getNamespace().equals(BloodAndBones.MOD_ID)) {
                continue;
            }
            files++;
            net.minecraft.nbt.Tag tag = dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinition.CODEC
                    .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, definition).getOrThrow();
            long states = BuiltInRegistries.BLOCK.get(definition.selector().id()).getStateDefinition().getPossibleStates().size();
            long these = states * definition.overrides().map(Map::size).orElse(0);
            matches += these;
            // what Sable does for this file each time it applies them (the same masses again, so nothing changes)
            long start = System.nanoTime();
            dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader.applyToBlocks(definition);
            long took = System.nanoTime() - start;
            nanos += took;
            BloodAndBones.LOGGER.info("[physics] {} takes {} bytes to send, {} matches and {} ms to apply", definition.selector().id(), tag.sizeInBytes(),
                    these, took / 1_000_000);
            helper.assertTrue(tag.sizeInBytes() < 1_500_000, "the physics properties for " + definition.selector().id() + " take " + tag.sizeInBytes()
                    + " bytes, too near the 2 MB a client takes in one packet");
        }
        BloodAndBones.LOGGER.info("[physics] the carcass blocks' masses take {} matches and {} ms to apply, each world and each joining client", matches,
                nanos / 1_000_000);
        helper.assertTrue(files == Tissue.values().length, "expected a physics properties file for each tissue, found " + files);
        helper.assertTrue(matches <= MATCHES_BEFORE_TISSUES * 5 / 4, "the carcass blocks' masses take " + matches + " state matches to apply, more than a "
                + "quarter over the one carcass block's " + MATCHES_BEFORE_TISSUES + " before bone and plate");
        // every rig's cells are weighed in every tissue
        java.util.Set<List<Integer>> made = new java.util.HashSet<>();
        for (var rig : RigManager.all().values()) {
            for (var each : rig.baby().isPresent() ? List.of(rig, RigManager.forEntity(rig.entity(), true).orElseThrow()) : List.of(rig)) {
                each.bones().forEach(bone -> made.addAll(CarcassAssembler.cellSizes(bone)));
            }
        }
        for (Tissue tissue : Tissue.values()) {
            for (List<Integer> size : made) {
                helper.assertTrue(CarcassAssembler.cellState(tissue, size.get(0), size.get(1), size.get(2)).getBlock() == BBBlocks.carcassPart(tissue),
                        "a cell " + size + " of " + tissue + " (a size a rig makes) should be weighed as " + tissue);
            }
        }
        // a size no rig makes: flesh, weighed by its size, not a whole block of bone
        List<Integer> odd = null;
        for (int x = 1; x <= 16 && odd == null; x++) {
            for (int y = 1; y <= 16 && odd == null; y++) {
                for (int z = 1; z <= 16 && odd == null; z++) {
                    if (!made.contains(List.of(x, y, z))) {
                        odd = List.of(x, y, z);
                    }
                }
            }
        }
        helper.assertTrue(odd != null, "every size is made by some rig");
        var state = CarcassAssembler.cellState(Tissue.BONE, odd.get(0), odd.get(1), odd.get(2));
        helper.assertTrue(state.getBlock() == BBBlocks.carcassPart(Tissue.FLESH) && CarcassPartBlock.weighed(state),
                "a cell " + odd + " of bone, a size no rig makes, should be built of flesh, which is weighed at every size");
        helper.succeed();
    }

    /** What the one carcass block's masses took to apply before bone and plate: 4,096 sizes by 4,096 overrides. */
    private static final long MATCHES_BEFORE_TISSUES = 4096L * 4096L;

    /**
     * "Roughly 5% for a chicken up to 55% for a ravager": dragging each slows its dragger by that much, and a cow sits
     * between, near what it cost before (29%).
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void dragPenaltyRunsFromChickenToRavager(GameTestHelper helper) {
        Map<EntityType<? extends Mob>, double[]> expected = new java.util.LinkedHashMap<>();
        expected.put(EntityType.CHICKEN, new double[]{0.04, 0.06});
        expected.put(EntityType.COW, new double[]{0.26, 0.32});
        expected.put(EntityType.RAVAGER, new double[]{0.54, 0.551});
        List<Subject> subjects = new ArrayList<>();
        double[] xs = {2.5, 5.0, 8.0};
        int i = 0;
        for (EntityType<? extends Mob> type : expected.keySet()) {
            Subject s = RigComparison.assembled(helper, type, new Vec3(xs[i++], 2, 5.5), RigScenarios.SOUTH);
            helper.assertTrue(s != null, "no " + RigComparison.mobName(type) + " carcass");
            subjects.add(s);
        }
        helper.runAfterDelay(5, () -> {
            int j = 0;
            for (Map.Entry<EntityType<? extends Mob>, double[]> entry : expected.entrySet()) {
                Subject s = subjects.get(j++);
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
                player.setPos(new Vec3(s.torsoCentre().x, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), s.torsoCentre().z + 1.5));
                double base = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
                helper.assertTrue(s.hook(player, s.torsoBody()), "could not hook the " + RigComparison.mobName(entry.getKey()));
                double penalty = CarcassDrag.penalty(player);
                double slowed = 1.0 - player.getAttributeValue(Attributes.MOVEMENT_SPEED) / base;
                CarcassDrag.stop(s.level, player);
                String mob = RigComparison.mobName(entry.getKey());
                BloodAndBones.LOGGER.info("[physics] dragging a {} ({} mass) costs {}", mob, fmt(s.liveMass()), fmt(penalty));
                helper.assertTrue(penalty >= entry.getValue()[0] && penalty <= entry.getValue()[1], "dragging a " + mob + " should cost "
                        + pct(entry.getValue()[0]) + " to " + pct(entry.getValue()[1]) + " of walking speed, but costs " + pct(penalty));
                helper.assertTrue(Math.abs(slowed - penalty) < 1.0e-3, "the " + mob + "'s penalty should be what slows its dragger (" + pct(penalty)
                        + "), but their speed fell by " + pct(slowed));
            }
            helper.succeed();
        });
    }

    /**
     * The penalty is the mass actually on the hook: a cow's severed hind leg costs what a leg weighs, a fraction of the
     * whole cow; and a leg cut off the body being dragged lightens the drag at once.
     */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void severedLegCostsWhatALegWeighs(GameTestHelper helper) {
        Subject s = RigComparison.assembled(helper, COW, new Vec3(5.5, 2, 5.5), RigScenarios.SOUTH);
        if (s == null) {
            helper.fail("no carcass");
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        double[] whole = {Double.NaN};
        double[] lighter = {Double.NaN};
        java.util.UUID[] legId = {null};
        helper.runAfterDelay(5, () -> {
            player.setPos(new Vec3(s.torsoCentre().x + 1.5, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), s.torsoCentre().z));
            player.setOldPosAndRot();
            helper.assertTrue(s.hook(player, s.torsoBody()), "could not hook the cow");
            whole[0] = CarcassDrag.penalty(player);
            // cut a hind leg off the body being dragged
            legId[0] = s.carcass().bones.get("right_hind_leg");
            for (int i = 0; i < CarcassButchery.CUTS_TO_SEVER; i++) {
                CarcassButchery.cut(s.level, null, s.carcass(), "right_hind_leg", null);
            }
        });
        helper.onEachTick(() -> {
            if (CarcassDrag.isDragging(player)) {
                CarcassDrag.tick(s.level, player);
            }
        });
        helper.runAfterDelay(20, () -> {
            // still dragging the body, which the cut took the leg from (a drag that let go would cost nothing)
            CarcassDrag.Drag held = CarcassDrag.current(player);
            helper.assertTrue(held != null && held.carcass.equals(s.carcass().id), "cutting a leg off the body being dragged should not end the drag");
            lighter[0] = CarcassDrag.penalty(player);
            double onHook = s.liveMass();
            helper.assertTrue(lighter[0] > 0.0 && Math.abs(lighter[0] - CarcassDrag.penaltyFor(onHook)) < 1.0e-4, "dragging the cow less its hind leg ("
                    + fmt(onHook) + " mass) should cost " + pct(CarcassDrag.penaltyFor(onHook)) + ", but costs " + pct(lighter[0]));
            CarcassDrag.stop(s.level, player);
            // now the leg on its own: the record its body went to (found by the body: tests share one world)
            CarcassSavedData.Carcass leg = legId[0] == null ? null : CarcassSavedData.get(s.level).carcassOfSubLevel(legId[0]);
            helper.assertTrue(leg != null && leg != s.carcass() && leg.bones.size() == 1, "the cut should leave the leg as a piece of its own");
            ServerSubLevel legBody = (ServerSubLevel) SubLevelContainer.getContainer(s.level).getSubLevel(legId[0]);
            helper.assertTrue(CarcassDrag.start(s.level, player, legBody.getPlot().getCenterBlock(), null), "could not hook the severed leg");
            double alone = CarcassDrag.penalty(player);
            double legMass = legBody.getMassTracker().getMass();
            CarcassDrag.stop(s.level, player);
            BloodAndBones.LOGGER.info("[physics] dragging a cow costs {}, less a hind leg {}, its hind leg alone ({} mass) {}", fmt(whole[0]), fmt(lighter[0]),
                    fmt(legMass), fmt(alone));
            helper.assertTrue(Math.abs(alone - CarcassDrag.penaltyFor(legMass)) < 1.0e-4, "a severed leg should cost what its own mass does");
            helper.assertTrue(alone < whole[0] / 5.0, "a severed hind leg should cost a fraction of its whole cow (" + pct(whole[0]) + "), but costs " + pct(alone));
            helper.assertTrue(lighter[0] < whole[0] - 0.005, "cutting a leg off the body being dragged should lighten the drag, but it went from "
                    + pct(whole[0]) + " to " + pct(lighter[0]));
            helper.succeed();
        });
    }

    /**
     * A player's real speed while dragging: three stand-in players walk side by side on the same floor, one free, one
     * dragging a chicken, one a ravager, and the draggers cover that much less ground (about 5% and 55% less).
     */
    @GameTest(template = "open_ground", timeoutTicks = 120)
    public static void draggingSlowsThePlayerByThePenalty(GameTestHelper helper) {
        Subject chicken = RigComparison.assembled(helper, EntityType.CHICKEN, new Vec3(15.5, 2, 4.5), RigScenarios.SOUTH);
        Subject ravager = RigComparison.assembled(helper, EntityType.RAVAGER, new Vec3(20.5, 2, 4.5), RigScenarios.SOUTH);
        if (chicken == null || ravager == null) {
            helper.fail("no carcass");
            return;
        }
        Player free = helper.makeMockPlayer(GameType.SURVIVAL);
        Player light = helper.makeMockPlayer(GameType.SURVIVAL);
        Player heavy = helper.makeMockPlayer(GameType.SURVIVAL);
        Player[] walkers = {free, light, heavy};
        double[] start = new double[3];
        double[] end = new double[3];
        int[] t = {0};
        helper.runAfterDelay(5, () -> {
            // each just south of what it drags (the free one in a lane of its own), facing south; the ravager by its head,
            // which it faces south with, so its big body trails behind the head and never reaches its dragger
            Vector3d head = ravager.middle("neck/head");
            Vec3[] at = {helper.absoluteVec(new Vec3(10.5, 2, 6.5)), new Vec3(chicken.torsoCentre().x, 0, chicken.torsoCentre().z + 1.0),
                    new Vec3(head.x, 0, head.z + 2.0)};
            double floor = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
            for (int i = 0; i < 3; i++) {
                walkers[i].setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
                walkers[i].setPos(at[i].x, floor, at[i].z);
                walkers[i].setYRot(RigScenarios.SOUTH);
                walkers[i].setOldPosAndRot();
            }
            helper.assertTrue(chicken.hook(light, chicken.torsoBody()) && ravager.hook(heavy, "neck/head"), "could not hook them");
        });
        helper.onEachTick(() -> {
            int now = ++t[0];
            if (now <= 5 || now > 45) {
                return;
            }
            for (int i = 0; i < 3; i++) {
                Player walker = walkers[i];
                walker.setOldPosAndRot();
                // walk south, as a player holding forward does
                walker.travel(new Vec3(0.0, 0.0, 1.0));
                if (i > 0) {
                    CarcassDrag.tick(helper.getLevel(), walker);
                }
                if (now == 20) {
                    start[i] = walker.getZ();
                }
                if (now == 45) {
                    end[i] = walker.getZ();
                }
            }
            if (now == 45) {
                double freeWay = end[0] - start[0];
                String report = String.format(Locale.ROOT, "free %.3f, with a chicken %.3f, with a ravager %.3f blocks", freeWay, end[1] - start[1], end[2] - start[2]);
                BloodAndBones.LOGGER.info("[physics] walked in 25 ticks: {} (on the ground: {} {} {})", report, free.onGround(), light.onGround(), heavy.onGround());
                helper.assertTrue(freeWay > 1.0, "the free walker should have walked (" + report + ")");
                for (int i = 1; i < 3; i++) {
                    helper.assertTrue(CarcassDrag.isDragging(walkers[i]), "a walker let go of what it dragged (" + report + ")");
                    double expected = 1.0 - CarcassDrag.penalty(walkers[i]);
                    double ratio = (end[i] - start[i]) / freeWay;
                    helper.assertTrue(Math.abs(ratio - expected) <= 0.03, "dragging, a walker should cover " + pct(expected) + " of the free walker's ground, "
                            + "but covered " + pct(ratio) + " (" + report + ")");
                }
                for (Player walker : walkers) {
                    CarcassDrag.stop(helper.getLevel(), walker);
                }
                helper.succeed();
            }
        });
    }

    // ---------------------------------------------------------------- the dragger

    /**
     * Walking forward while dragging never carries the dragger off: a player hooks a lying cow's body and walks straight
     * on, as a player holding forward does, facing where they go, for two and a half seconds, then stands. Walking off
     * away from it, the cow trails behind them. Walking on into it (it lies in their way, so it is held in front of them
     * and they catch it up), they must not be pushed along by it: once, walking into it and being pushed by it moved the
     * point it was pulled to, which pulled it on, and it carried a player about twenty blocks after they stopped. Either
     * way no step of theirs is longer than a walk's, and once they stand they stay where they stopped, still dragging it.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void walkingOffWhileDraggingNeverCarriesTheDragger(GameTestHelper helper) {
        walkForwardDragging(helper, false);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void walkingIntoWhatYouDragNeverCarriesYou(GameTestHelper helper) {
        walkForwardDragging(helper, true);
    }

    /** How long the stand-in walks, then stands, in ticks. */
    private static final int WALK = 50;
    private static final int STAND = 40;

    private static void walkForwardDragging(GameTestHelper helper, boolean into) {
        // a cow lying with its head east; the player walks east, from its west side into it, or from its east side away from it
        Subject s = RigComparison.assembled(helper, COW, new Vec3(into ? 4.5 : 2.5, 2, 5.5), RigScenarios.EAST);
        if (s == null) {
            helper.fail("no carcass");
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        double floor = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        int[] t = {0};
        double[] longest = {0.0};
        double[] highest = {0.0};
        Vec3[] stoppedAt = {null};
        double[] drift = {0.0};
        helper.onEachTick(() -> {
            int now = ++t[0];
            if (now < 20) {
                return;
            }
            if (now == 20) {
                Vector3d torso = s.torsoCentre();
                player.setPos(torso.x + (into ? -2.0 : 1.5), floor, torso.z);
                player.setYRot(RigScenarios.EAST);
                player.setYHeadRot(RigScenarios.EAST);
                player.setXRot(into ? 30.0F : 0.0F);
                player.setOldPosAndRot();
                helper.assertTrue(s.hook(player, s.torsoBody()), "could not hook the cow");
                return;
            }
            player.setOldPosAndRot();
            boolean walking = now <= 20 + WALK;
            // holding forward, then nothing: the game's own walk, with the drag's slowdown, and Sable's collisions
            player.travel(walking ? new Vec3(0.0, 0.0, 1.0) : Vec3.ZERO);
            CarcassDrag.tick(s.level, player);
            longest[0] = Math.max(longest[0], Math.hypot(player.getX() - player.xo, player.getZ() - player.zo));
            highest[0] = Math.max(highest[0], player.getY() - floor);
            if (now == 20 + WALK + 5) {
                stoppedAt[0] = player.position();
            } else if (stoppedAt[0] != null) {
                drift[0] = Math.max(drift[0], Math.hypot(player.getX() - stoppedAt[0].x, player.getZ() - stoppedAt[0].z));
            }
            if (now == 20 + WALK + STAND) {
                boolean held = CarcassDrag.isDragging(player);
                CarcassDrag.stop(s.level, player);
                BloodAndBones.LOGGER.info("[physics] walking {} what it drags: longest step {} blocks, highest {} over the floor, then drifted {} standing",
                        into ? "into" : "away from", fmt(longest[0]), fmt(highest[0]), fmt(drift[0]));
                helper.assertTrue(held, "the drag let go");
                helper.assertTrue(longest[0] <= WALK_STEP, "walking " + (into ? "into" : "away from") + " what they drag, a player should step no further "
                        + "than a walk takes them (" + fmt(WALK_STEP) + " a tick), but went " + fmt(longest[0]) + " in one tick");
                helper.assertTrue(highest[0] <= 0.6, "walking " + (into ? "into" : "away from") + " what they drag, a player was lifted " + fmt(highest[0])
                        + " blocks off the floor");
                helper.assertTrue(drift[0] <= 0.1, "standing still after walking " + (into ? "into" : "away from") + " what they drag, a player should stay "
                        + "where they stopped, but was carried " + fmt(drift[0]) + " blocks");
                helper.succeed();
            }
        });
    }

    /** The furthest a player walking on flat ground goes in a tick, in blocks: a little over a free walk's 0.216. */
    private static final double WALK_STEP = 0.25;

    // ---------------------------------------------------------------- helpers

    private static void require(GameTestHelper helper, Numbers n) {
        if (n.broken != null) {
            helper.fail("could not be played out: " + n.broken);
        }
    }

    static double median(List<Double> values) {
        List<Double> sorted = values.stream().filter(Double::isFinite).sorted().toList();
        return sorted.isEmpty() ? Double.NaN : sorted.get(sorted.size() / 2);
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.1f%%", 100.0 * v);
    }
}
