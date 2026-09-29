package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
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
import com.avicagan.bloodandbones.registry.BBItems;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
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
 * lands it on its side, away from the blow, and one from behind lands it nose down; dragged by a hind leg it gets up a
 * one-block step; a punch knocks a hung carcass. Then the parts the physics stands on: a lying carcass is hooked by the
 * part aimed at, the killing blow lands on the part it hits, bone and plate weigh more than flesh, and the drag's
 * slowdown follows the mass actually on the hook (docs/ARCHITECTURE-PROPOSAL.md 15.19). The head lolling, and a hung
 * carcass swinging freely and hanging differently with a leg off, wait on the owner's decisions 4 and 5.
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
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
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
            // what a click that missed every cell does (the look went through the drawn part to the floor)
            if (!CarcassDrag.useOnDrawn(s.level, player, 10.0)) {
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

    private static void kill(GameTestHelper helper, Mob mob, Vec3 from, Vec3 lookAt, String expected) {
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
    }

    // ---------------------------------------------------------------- hanging

    /**
     * A punch lands on a hung carcass too: punched from the east it is pushed west, away from the punch, and comes back
     * under its hook. How far it swings is the hang's: held belly-out as it is (ShackleHookBlockEntity#turn), it gives a
     * few hundredths of a block; a looser hang, which swings for seconds, waits on the owner's decision 5
     * (docs/ARCHITECTURE-PROPOSAL.md 15.19).
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void hungCarcassIsKnockedByAPunch(GameTestHelper helper) {
        Subject s = RigComparison.assembled(helper, COW, new Vec3(5.5, 2, 5.5), RigScenarios.SOUTH);
        if (s == null) {
            helper.fail("no carcass");
            return;
        }
        BlockPos hookAt = RigComparison.hook(helper, new BlockPos(5, 5, 5));
        helper.runAfterDelay(5, () -> helper.assertTrue(RigComparison.hang(s, hookAt), "could not hang it"));
        Vector3d[] atPunch = {null};
        Vector3d way = new Vector3d(-1, 0, 0);
        double[] awayEarly = {0.0};
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
                if (now <= 110) {
                    awayEarly[0] = Math.max(awayEarly[0], along);
                }
                if (now == 200) {
                    helper.assertTrue(awayEarly[0] >= KNOCKED, "punched from the east it should be pushed west, away from the punch, but moved only "
                            + fmt(awayEarly[0]) + " blocks that way");
                    double left = new Vector3d(s.torsoCentre()).sub(atPunch[0]).length();
                    helper.assertTrue(left <= 0.1, "knocked, it should come back under its hook, but hangs " + fmt(left) + " blocks from where it hung");
                    helper.succeed();
                }
            }
        });
    }

    /** How far a punch pushes a hung cow's torso away from it, at least, in blocks. */
    private static final double KNOCKED = 0.03;

    /** Ready a stand-in player's swing: a fresh one has only just swung and hits at a tenth of its strength. */
    static void charge(Player player) {
        net.neoforged.fml.util.ObfuscationReflectionHelper.setPrivateValue(LivingEntity.class, player, 100, "attackStrengthTicker");
    }

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
            helper.succeed();
        });
    }

    /**
     * The carcass cells' masses, by size and tissue, stay cheap to load. Sable sends each physics properties file to every
     * joining client as one packet, which a client refuses over 2 MB (it disconnects); and it matches every state of a
     * block against every override of its file, for each world as it loads and for each joining client, so each file
     * must stay within one block's 4,096 states times 4,096 overrides (a tissue property on one block made it nine
     * times that, some twenty seconds a world).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cellMassesAreCheapToLoad(GameTestHelper helper) {
        int files = 0;
        for (var definition : dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader.INSTANCE.getDefinitions()) {
            if (!definition.selector().id().getNamespace().equals(BloodAndBones.MOD_ID)) {
                continue;
            }
            files++;
            net.minecraft.nbt.Tag tag = dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinition.CODEC
                    .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, definition).getOrThrow();
            long states = BuiltInRegistries.BLOCK.get(definition.selector().id()).getStateDefinition().getPossibleStates().size();
            long matches = states * definition.overrides().map(Map::size).orElse(0);
            BloodAndBones.LOGGER.info("[physics] {} takes {} bytes to send and {} matches to apply", definition.selector().id(), tag.sizeInBytes(), matches);
            helper.assertTrue(tag.sizeInBytes() < 1_500_000, "the physics properties for " + definition.selector().id() + " take " + tag.sizeInBytes()
                    + " bytes, too near the 2 MB a client takes in one packet");
            helper.assertTrue(matches <= 4096L * 4096L, "the physics properties for " + definition.selector().id() + " take " + matches
                    + " state matches to apply, more than one carcass block's 4,096 states by 4,096 sizes");
        }
        helper.assertTrue(files >= Tissue.values().length, "expected a physics properties file for each tissue, found " + files);
        helper.succeed();
    }

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
            lighter[0] = CarcassDrag.penalty(player);
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
