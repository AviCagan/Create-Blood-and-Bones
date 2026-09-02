package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.registry.BBItems;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class BBGameTests {
    /** Ticks to wait after the arena is placed before touching physics, so its colliders exist. */
    private static final int SETTLE_TICKS = 20;

    public static void register(RegisterGameTestsEvent event) {
        event.register(BBGameTests.class);
        event.register(ColliderProbeTests.class);
    }

    // Positions are relative to the structure block, which sits one block below the template: the
    // template's stone floor is at y=1 and the first air layer is y=2.

    /** Direct assembly: six limbs, five joints, everything stays put. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cowCarcassAssembles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        Vec3 cowPos = cow.position();
        cow.setNoAi(true);

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            if (!CarcassAssembler.assemble(cow, null)) {
                helper.fail("Carcass assembly returned false");
            }
            cow.discard();
        });

        helper.runAfterDelay(SETTLE_TICKS + 40, () -> {
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            if (carcass.bones.size() != 6) {
                helper.fail("Expected 6 cow bones, found " + carcass.bones.size());
            }
            if (carcass.joints.size() != 5) {
                helper.fail("Expected 5 cow joints, found " + carcass.joints.size());
            }
            requireLiveJoints(helper, carcass, 5);
            for (Map.Entry<String, ServerSubLevel> bone : liveBones(helper, level, carcass).entrySet()) {
                Vector3d pos = bone.getValue().logicalPose().position();
                double distance = pos.distance(cowPos.x, cowPos.y, cowPos.z);
                if (distance > 4.0) {
                    helper.fail("Bone " + bone.getKey() + " ended up " + distance + " blocks from the cow at " + pos);
                }
                if (pos.y < cowPos.y - 0.2) {
                    helper.fail("Bone " + bone.getKey() + " sank into the floor to " + pos);
                }
                if (pos.y > cowPos.y + 1.6) {
                    helper.fail("Bone " + bone.getKey() + " is floating at " + pos);
                }
            }
            helper.succeed();
        });
    }

    /** Joints are rebuilt from saved data when their handles are gone, as after a world reload. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cowCarcassJointsRestore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        if (!CarcassAssembler.assemble(cow, null)) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();

        helper.runAfterDelay(10, () -> {
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            requireLiveJoints(helper, carcass, 5);
            for (PhysicsConstraintHandle handle : carcass.liveJoints) {
                handle.remove();
            }
            if (carcass.jointsValid()) {
                helper.fail("Joints still reported valid after removal");
            }
        });
        helper.runAfterDelay(40, () -> {
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            requireLiveJoints(helper, carcass, 5);
            helper.succeed();
        });
    }

    /** The real path: a player holding a Meat Hook kills the cow. No drops, a carcass instead. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void meatHookKillMakesCarcass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        cow.hurt(level.damageSources().playerAttack(player), 1000.0F);

        helper.runAfterDelay(20, () -> {
            if (cow.isAlive() || !cow.isRemoved()) {
                helper.fail("Cow should be gone after a Meat Hook kill");
            }
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            requireLiveJoints(helper, carcass, 5);
            AABB area = AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(10, 6, 10)));
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area);
            if (!drops.isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (ItemEntity drop : drops) {
                    names.append(' ').append(drop.getItem());
                }
                helper.fail("A Meat Hook kill should drop nothing, found " + drops.size() + " item entities:" + names);
            }
            helper.succeed();
        });
    }

    /** Dragging: hook the body, walk away, the carcass follows; let go and it stops following. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void meatHookDragsByLeg(GameTestHelper helper) {
        dragTest(helper, "left_front_leg");
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void meatHookDragsByBody(GameTestHelper helper) {
        dragTest(helper, "body");
    }

    private static void dragTest(GameTestHelper helper, String grabBone) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        if (!CarcassAssembler.assemble(cow, null)) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5)));
        player.setPos(start);
        double[] hookedDistance = new double[1];

        helper.runAfterDelay(20, () -> {
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            ServerSubLevel body = liveBones(helper, level, carcass).get(carcass.rootBone);
            // grab by a leg: the whole carcass must still follow
            ServerSubLevel leg = liveBones(helper, level, carcass).get(grabBone);
            BlockPos legCell = leg.getPlot().getCenterBlock();
            if (!CarcassDrag.start(level, player, legCell, null)) {
                helper.fail("Could not start dragging the leg");
            }
            if (!CarcassDrag.isDragging(player)) {
                helper.fail("Player is not marked as dragging");
            }
            // stand 3 blocks away, facing away from the carcass, and keep ticking the tether
            player.setPos(start.add(3.0, 0.0, 0.0));
            player.setYRot(-90.0F);
            player.setOldPosAndRot(); // mock players never tick, so refresh the previous-tick position the tether interpolates from
            hookedDistance[0] = body.logicalPose().position().distance(player.getX(), player.getY(), player.getZ());
        });
        helper.onEachTick(() -> {
            if (CarcassDrag.isDragging(player)) {
                CarcassDrag.tick(level, player);
            }
        });
        helper.runAfterDelay(120, () -> {
            CarcassDrag.Drag drag = CarcassDrag.current(player);
            if (drag == null) {
                helper.fail("Drag ended on its own");
                return;
            }
            // the carcass has been pulled away from the arena center by now; find it by id
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(drag.carcass);
            if (carcass == null) {
                helper.fail("Dragged carcass vanished from saved data");
                return;
            }
            ServerSubLevel body = liveBones(helper, level, carcass).get(carcass.rootBone);
            CarcassDrag.Drag current = CarcassDrag.current(player);
            ServerSubLevel hooked = (ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(current.subLevel);
            org.joml.Vector3d hook = hooked.logicalPose().transformPosition(current.anchorPlot, new org.joml.Vector3d());
            org.joml.Vector3d target = CarcassDrag.debugTarget(player);
            double gap = hook.distance(target);
            // a grabbed leg cannot fully align with the target because the hip joint holds it, so allow slack there
            double allowed = grabBone.equals("body") ? 0.5 : 2.0;
            if (gap > allowed) {
                helper.fail("Hooked point did not reach the tether target: still " + gap + " blocks away (started " + hookedDistance[0] + " from the player)");
            }
            if (!CarcassDrag.isDragging(player)) {
                helper.fail("Drag ended on its own");
            }
            CarcassDrag.stop(level, player);
            if (CarcassDrag.isDragging(player)) {
                helper.fail("Drag did not stop");
            }
            requireLiveJoints(helper, carcass, 5);
            helper.succeed();
        });
    }

    /** Destroying limbs for good forgets them, and an empty carcass is forgotten entirely. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void removedLimbsAreForgotten(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        if (!CarcassAssembler.assemble(cow, null)) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();
        UUID[] id = new UUID[1];
        helper.runAfterDelay(10, () -> {
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            id[0] = carcass.id;
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            container.removeSubLevel(container.getSubLevel(carcass.bones.get("head")), dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        });
        helper.runAfterDelay(20, () -> {
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(id[0]);
            if (carcass == null || carcass.bones.size() != 5 || carcass.bones.containsKey("head") || carcass.joints.size() != 4) {
                helper.fail("Removing the head should leave 5 bones and 4 joints, got " + (carcass == null ? "no carcass" : carcass.bones.size() + " bones, " + carcass.joints.size() + " joints"));
            }
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            for (UUID sub : List.copyOf(carcass.bones.values())) {
                SubLevel s = container.getSubLevel(sub);
                if (s != null) {
                    container.removeSubLevel(s, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
                }
            }
        });
        helper.runAfterDelay(30, () -> {
            if (CarcassSavedData.get(level).carcass(id[0]) != null) {
                helper.fail("A carcass with no limbs left should be forgotten");
            }
            helper.succeed();
        });
    }

    /** Looking at a leg from the side must hit that leg's cell, so the Meat Hook can grab any limb. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void raycastHitsLegs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        Vec3 cowPos = cow.position();
        if (!CarcassAssembler.assemble(cow, null)) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();
        helper.runAfterDelay(10, () -> {
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            Map<String, ServerSubLevel> bones = liveBones(helper, level, carcass);
            StringBuilder report = new StringBuilder();
            int hits = 0;
            for (String bone : List.of("left_front_leg", "right_hind_leg", "head", "body")) {
                ServerSubLevel target = bones.get(bone);
                org.joml.Vector3d com = target.logicalPose().position();
                // shoot from 3 blocks out on the limb's own side of the cow at its center of mass
                double side = com.x >= cowPos.x ? 3.0 : -3.0;
                Vec3 from = new Vec3(com.x + side, com.y, com.z);
                Vec3 to = new Vec3(com.x, com.y, com.z);
                net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(from, to,
                        net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, cow));
                String got = "miss";
                if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                        && level.getBlockEntity(hit.getBlockPos()) instanceof com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity part) {
                    got = part.bone();
                    if (got.equals(bone)) {
                        hits++;
                    }
                } else if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                    got = "block " + level.getBlockState(hit.getBlockPos()).getBlock().getName().getString() + " at " + hit.getBlockPos();
                }
                report.append(' ').append(bone).append("->").append(got);
            }
            BloodAndBones.LOGGER.info("[raycast test]{}", report);
            if (hits != 4) {
                helper.fail("Raycasts did not hit the aimed limbs:" + report);
            }
            helper.succeed();
        });
    }

    /** Hang a carcass on a Shackle Hook under a ceiling block: the hooked limb stays at the tip, the body dangles. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void shackleHookHangsCarcass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        if (!CarcassAssembler.assemble(cow, null)) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();
        // ceiling block with the hook under it, 4 blocks up
        helper.setBlock(new BlockPos(5, 6, 5), net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(new BlockPos(5, 5, 5), com.avicagan.bloodandbones.registry.BBBlocks.SHACKLE_HOOK.get().defaultBlockState()
                .setValue(com.avicagan.bloodandbones.carcass.ShackleHookBlock.FACING, net.minecraft.core.Direction.UP));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))));
        player.setOldPosAndRot();
        helper.runAfterDelay(10, () -> {
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            ServerSubLevel leg = liveBones(helper, level, carcass).get("right_hind_leg");
            if (!CarcassDrag.start(level, player, leg.getPlot().getCenterBlock(), null)) {
                helper.fail("Could not start dragging");
            }
            if (!(level.getBlockEntity(helper.absolutePos(new BlockPos(5, 5, 5))) instanceof com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity hook)) {
                helper.fail("No shackle hook block entity");
                return;
            }
            hook.toggle(level, player);
            if (!hook.isOccupied() || CarcassDrag.isDragging(player)) {
                helper.fail("Hook did not take the dragged limb");
            }
        });
        helper.runAfterDelay(140, () -> {
            com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity hook = (com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 5, 5)));
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            Map<String, ServerSubLevel> bones = liveBones(helper, level, carcass);
            ServerSubLevel body = bones.get(carcass.rootBone);
            if (!hook.hookedBone().equals(carcass.rootBone)) {
                helper.fail("A carcass should always hang by its torso, got " + hook.hookedBone());
            }
            Vec3 tip = com.avicagan.bloodandbones.carcass.ShackleHookBlock.tip(helper.absolutePos(new BlockPos(5, 5, 5)), hook.getBlockState());
            org.joml.Vector3d hooked = body.logicalPose().transformPosition(hook.hookedAnchor(), new org.joml.Vector3d());
            double gap = hooked.distance(tip.x, tip.y, tip.z);
            if (gap > 0.35) {
                helper.fail("Hooked point is " + gap + " blocks from the hook tip");
            }
            org.joml.Vector3d bodyPos = body.logicalPose().position();
            if (bodyPos.y > tip.y - 0.3) {
                helper.fail("Body should hang below the hook, it is at " + bodyPos.y + " vs tip " + tip.y);
            }
            // head end (part-local -y) up, belly (part-local -z) horizontal
            org.joml.Vector3d headEnd = body.logicalPose().orientation().transform(new org.joml.Vector3d(0, -1, 0));
            org.joml.Vector3d belly = body.logicalPose().orientation().transform(new org.joml.Vector3d(0, 0, -1));
            if (headEnd.y < 0.7) {
                helper.fail("Body should hang head-up; head end direction is " + headEnd);
            }
            if (Math.abs(belly.y) > 0.5) {
                helper.fail("Belly should face sideways, not up or down; belly direction is " + belly);
            }
            hook.release(level);
            if (hook.isOccupied()) {
                helper.fail("Hook did not release");
            }
            helper.succeed();
        });
    }

    /** A still carcass folds into one body; grabbing it unfolds it at the same poses with its joints back. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void restingFormFoldsAndUnfolds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        if (!CarcassAssembler.assemble(cow, null)) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();
        UUID[] id = new UUID[1];
        Map<String, org.joml.Vector3d> before = new java.util.HashMap<>();
        helper.runAfterDelay(30, () -> {
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            id[0] = carcass.id;
            for (Map.Entry<String, ServerSubLevel> e : liveBones(helper, level, carcass).entrySet()) {
                before.put(e.getKey(), new org.joml.Vector3d(e.getValue().logicalPose().position()));
            }
        });
        // stillness (60 ticks) plus a margin
        helper.runAfterDelay(30 + com.avicagan.bloodandbones.carcass.CarcassRest.STILL_TICKS + 40, () -> {
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(id[0]);
            if (carcass == null || !carcass.resting) {
                helper.fail("Carcass should be resting after standing still, resting=" + (carcass != null && carcass.resting));
                return;
            }
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            int loaded = 0;
            for (UUID sub : carcass.bones.values()) {
                if (container.getSubLevel(sub) != null) {
                    loaded++;
                }
            }
            if (loaded != 1) {
                helper.fail("A resting carcass should be one body, found " + loaded);
            }
            if (carcass.restPoses.size() != 5) {
                helper.fail("Expected 5 remembered limb poses, got " + carcass.restPoses.size());
            }
            SubLevel torso = container.getSubLevel(carcass.bones.get(carcass.rootBone));
            BlockPos center = torso.getPlot().getCenterBlock();
            if (!(level.getBlockEntity(center) instanceof com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity root) || root.merged().size() != 5) {
                helper.fail("Torso root cell should carry 5 merged parts for rendering");
            }
            // now grab it: it must unfold
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
            player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))));
            player.setOldPosAndRot();
            if (!CarcassDrag.start(level, player, center, null)) {
                helper.fail("Could not grab the resting carcass");
            }
            CarcassDrag.stop(level, player);
        });
        helper.runAfterDelay(30 + com.avicagan.bloodandbones.carcass.CarcassRest.STILL_TICKS + 45, () -> {
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(id[0]);
            if (carcass.resting) {
                helper.fail("Carcass should have unfolded when grabbed");
            }
            Map<String, ServerSubLevel> bones = liveBones(helper, level, carcass);
            if (bones.size() != 6) {
                helper.fail("Expected 6 bodies after unfolding, got " + bones.size());
            }
            requireLiveJoints(helper, carcass, 5);
            for (Map.Entry<String, ServerSubLevel> e : bones.entrySet()) {
                org.joml.Vector3d was = before.get(e.getKey());
                double moved = was == null ? 0 : was.distance(e.getValue().logicalPose().position());
                if (moved > 0.35) {
                    helper.fail("Bone " + e.getKey() + " moved " + moved + " blocks through fold/unfold");
                }
            }
            if (!carcass.restCells.isEmpty()) {
                helper.fail("Rest cells should be gone after unfolding");
            }
            helper.succeed();
        });
    }

    /** Freshness falls at the rig's rate, keeps falling once the carcass is resting, and reaches the torso's root cell. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void carcassRots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        if (!CarcassAssembler.assemble(cow, null)) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();
        UUID[] id = new UUID[1];
        float[] early = new float[1];
        helper.runAfterDelay(20, () -> {
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            id[0] = carcass.id;
            early[0] = carcass.freshness;
            if (carcass.freshness > 1.0F || carcass.freshness < 0.99F) {
                helper.fail("A fresh carcass should start near 1.0, got " + carcass.freshness);
            }
        });
        helper.runAfterDelay(160, () -> {
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(id[0]);
            if (carcass == null) {
                helper.fail("Carcass vanished");
                return;
            }
            if (!carcass.resting) {
                helper.fail("Carcass should be resting by now");
            }
            float rate = com.avicagan.bloodandbones.carcass.CarcassRot.biomeRate(level.getBiome(helper.absolutePos(new BlockPos(5, 2, 5))).value(), helper.absolutePos(new BlockPos(5, 2, 5)));
            float expected = rate * 140.0F / com.avicagan.bloodandbones.carcass.rig.Rig.DEFAULT_ROT_TIME;
            float drop = early[0] - carcass.freshness;
            if (drop < expected * 0.6F || drop > expected * 1.4F) {
                helper.fail("Freshness fell by " + drop + " over 140 ticks, expected about " + expected);
            }
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            SubLevel torso = container.getSubLevel(carcass.bones.get(carcass.rootBone));
            BlockPos center = torso.getPlot().getCenterBlock();
            if (!(level.getBlockEntity(center) instanceof com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity root)) {
                helper.fail("No root cell for the torso");
                return;
            }
            if (root.freshness() >= 1.0F) {
                helper.fail("The torso's root cell was never told the freshness (still " + root.freshness() + ")");
            }
            helper.succeed();
        });
    }

    /** Blue ice under the carcass stops rot outright; plain ice only slows it. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void coldSlowsAndStopsRot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 3; x <= 7; x++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.BLUE_ICE);
            }
        }
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        if (!CarcassAssembler.assemble(cow, null)) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();
        UUID[] id = new UUID[1];
        float[] afterIce = new float[1];
        helper.runAfterDelay(5, () -> id[0] = onlyCarcass(helper, level).id);
        helper.runAfterDelay(120, () -> {
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(id[0]);
            if (carcass.freshness != 1.0F) {
                helper.fail("Blue ice should stop rot, freshness is " + carcass.freshness);
            }
            for (int x = 3; x <= 7; x++) {
                for (int z = 3; z <= 7; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.ICE);
                }
            }
            afterIce[0] = carcass.freshness;
        });
        helper.runAfterDelay(260, () -> {
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(id[0]);
            float drop = afterIce[0] - carcass.freshness;
            float rate = com.avicagan.bloodandbones.carcass.CarcassRot.biomeRate(level.getBiome(helper.absolutePos(new BlockPos(5, 2, 5))).value(), helper.absolutePos(new BlockPos(5, 2, 5)));
            float plain = rate * 140.0F / com.avicagan.bloodandbones.carcass.rig.Rig.DEFAULT_ROT_TIME;
            if (drop <= 0.0F) {
                helper.fail("Ice should only slow rot, but freshness did not fall at all");
            }
            if (drop > plain * 0.5F) {
                helper.fail("Ice should slow rot to a quarter, but freshness fell by " + drop + " against " + plain + " in the open");
            }
            helper.succeed();
        });
    }

    /** The carcass whose root limb is nearest this test's arena center; tests are placed side by side. */
    private static CarcassSavedData.Carcass onlyCarcass(GameTestHelper helper, ServerLevel level) {
        CarcassSavedData data = CarcassSavedData.get(level);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            helper.fail("No Sable container");
        }
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 2, 5)));
        CarcassSavedData.Carcass nearest = null;
        double best = 6.0;
        for (CarcassSavedData.Carcass carcass : data.all()) {
            UUID rootId = carcass.bones.get(carcass.rootBone);
            SubLevel root = rootId == null ? null : container.getSubLevel(rootId);
            if (root instanceof ServerSubLevel serverRoot && !serverRoot.isRemoved()) {
                double distance = serverRoot.logicalPose().position().distance(origin.x, origin.y, origin.z);
                if (distance < best) {
                    best = distance;
                    nearest = carcass;
                }
            }
        }
        if (nearest == null) {
            helper.fail("No loaded carcass within 6 blocks of this test at tick " + helper.getTick());
        }
        return nearest;
    }

    private static void requireLiveJoints(GameTestHelper helper, CarcassSavedData.Carcass carcass, int expected) {
        if (carcass.liveJoints.size() != expected) {
            helper.fail("Expected " + expected + " live joints, found " + carcass.liveJoints.size());
        }
        for (PhysicsConstraintHandle handle : carcass.liveJoints) {
            if (!handle.isValid()) {
                helper.fail("A joint handle went invalid");
            }
        }
    }

    private static Map<String, ServerSubLevel> liveBones(GameTestHelper helper, ServerLevel level, CarcassSavedData.Carcass carcass) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        Map<String, ServerSubLevel> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, UUID> bone : carcass.bones.entrySet()) {
            SubLevel subLevel = container.getSubLevel(bone.getValue());
            if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
                helper.fail("Bone " + bone.getKey() + " has no live sub-level");
                continue;
            }
            result.put(bone.getKey(), serverSubLevel);
        }
        return result;
    }
}
