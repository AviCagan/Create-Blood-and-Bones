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
            if (CarcassAssembler.assemble(cow, null) == null) {
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
        if (CarcassAssembler.assemble(cow, null) == null) {
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
        if (CarcassAssembler.assemble(cow, null) == null) {
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
        // the hooked point's distance to its target over the last second, judged by its middle value: one
        // tick caught mid-swing does not fail a drag that holds on, and one that keeps swinging still fails
        List<Double> gaps = new java.util.ArrayList<>();
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (CarcassDrag.isDragging(player)) {
                CarcassDrag.tick(level, player);
                if (++ticks[0] >= 80) {
                    CarcassDrag.Drag now = CarcassDrag.current(player);
                    if (now != null && SubLevelContainer.getContainer(level).getSubLevel(now.subLevel) instanceof ServerSubLevel held) {
                        org.joml.Vector3d point = held.logicalPose().transformPosition(now.anchorPlot, new org.joml.Vector3d());
                        gaps.add(point.distance(CarcassDrag.debugTarget(player)));
                    }
                }
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
            gaps.add(hook.distance(target));
            List<Double> sorted = gaps.stream().sorted().toList();
            double gap = sorted.get(sorted.size() / 2);
            // a grabbed leg cannot fully align with the target because the hip joint holds it back against the
            // body's weight; since the leg is also steered to point at the hand it settles right about 2 blocks off
            double allowed = grabBone.equals("body") ? 0.5 : 2.25;
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
        if (CarcassAssembler.assemble(cow, null) == null) {
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
        if (CarcassAssembler.assemble(cow, null) == null) {
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
        if (CarcassAssembler.assemble(cow, null) == null) {
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

    /**
     * A Create contraption carries a Shackle Hook's data but not its carcass. Put back down somewhere else,
     * the hook must let the carcass go, not pull it across to its new place.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void movedShackleHookLetsGo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        if (CarcassAssembler.assemble(cow, null) == null) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();
        BlockPos hookPos = new BlockPos(5, 5, 5);
        BlockPos farPos = new BlockPos(5, 5, 1);
        net.minecraft.world.level.block.state.BlockState hookState = com.avicagan.bloodandbones.registry.BBBlocks.SHACKLE_HOOK.get().defaultBlockState()
                .setValue(com.avicagan.bloodandbones.carcass.ShackleHookBlock.FACING, net.minecraft.core.Direction.UP);
        helper.setBlock(hookPos.above(), net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(hookPos, hookState);
        helper.setBlock(farPos.above(), net.minecraft.world.level.block.Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))));
        player.setOldPosAndRot();
        net.minecraft.nbt.CompoundTag[] carried = new net.minecraft.nbt.CompoundTag[1];
        helper.runAfterDelay(10, () -> {
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            ServerSubLevel leg = liveBones(helper, level, carcass).get("right_hind_leg");
            CarcassDrag.start(level, player, leg.getPlot().getCenterBlock(), null);
            com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity hook = (com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(hookPos));
            hook.toggle(level, player);
            if (!hook.isOccupied()) {
                helper.fail("Hook did not take the dragged limb");
            }
        });
        helper.runAfterDelay(80, () -> {
            // what Contraption#removeBlocksFromWorld does: the block entity goes first, then the block
            com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity hook = (com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(hookPos));
            carried[0] = hook.saveWithFullMetadata(level.registryAccess());
            level.removeBlockEntity(helper.absolutePos(hookPos));
            level.setBlock(helper.absolutePos(hookPos), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
            // ...and later, where the contraption stops, the block and its data come back
            helper.setBlock(farPos, hookState);
            level.getBlockEntity(helper.absolutePos(farPos)).loadWithComponents(carried[0], level.registryAccess());
        });
        helper.runAfterDelay(140, () -> {
            com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity moved = (com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(farPos));
            if (moved.isOccupied()) {
                helper.fail("A hook put down away from its carcass should have let it go");
            }
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            ServerSubLevel body = liveBones(helper, level, carcass).get(carcass.rootBone);
            Vec3 tip = com.avicagan.bloodandbones.carcass.ShackleHookBlock.tip(helper.absolutePos(farPos), hookState);
            if (body.logicalPose().position().distance(tip.x, tip.y, tip.z) < 1.5) {
                helper.fail("The carcass was pulled over to the moved hook");
            }
            helper.succeed();
        });
    }

    /** A still carcass folds into one body; grabbing it unfolds it at the same poses with its joints back. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void restingFormFoldsAndUnfolds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        if (CarcassAssembler.assemble(cow, null) == null) {
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
        if (CarcassAssembler.assemble(cow, null) == null) {
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
        if (CarcassAssembler.assemble(cow, null) == null) {
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

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pigCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.PIG, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sheepCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.SHEEP, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chickenCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.CHICKEN, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void horseCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.HORSE, 7);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void wolfCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.WOLF, 8);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void zombieCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.ZOMBIE, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void skeletonCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.SKELETON, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void mooshroomCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.MOOSHROOM, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void huskCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.HUSK, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void strayCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.STRAY, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void boggedCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.BOGGED, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void witherSkeletonCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.WITHER_SKELETON, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void zombieHorseCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.ZOMBIE_HORSE, 7);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void skeletonHorseCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.SKELETON_HORSE, 7);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void donkeyCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.DONKEY, 7);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void muleCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.MULE, 7);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void llamaCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.LLAMA, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void traderLlamaCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.TRADER_LLAMA, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void goatCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.GOAT, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void polarBearCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.POLAR_BEAR, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pandaCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.PANDA, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void catCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.CAT, 7);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ocelotCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.OCELOT, 7);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void hoglinCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.HOGLIN, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void zoglinCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.ZOGLIN, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void spiderCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.SPIDER, 11);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void caveSpiderCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.CAVE_SPIDER, 11);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void creeperCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.CREEPER, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void foxCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.FOX, 7);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void rabbitCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.RABBIT, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void turtleCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.TURTLE, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void camelCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.CAMEL, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ironGolemCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.IRON_GOLEM, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ravagerCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.RAVAGER, 7);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void endermanCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.ENDERMAN, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void villagerCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.VILLAGER, 5);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void wanderingTraderCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.WANDERING_TRADER, 5);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void witchCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.WITCH, 5);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void zombieVillagerCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.ZOMBIE_VILLAGER, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pillagerCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.PILLAGER, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void vindicatorCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.VINDICATOR, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void evokerCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.EVOKER, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void illusionerCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.ILLUSIONER, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void piglinCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.PIGLIN, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void zombifiedPiglinCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.ZOMBIFIED_PIGLIN, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void piglinBruteCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.PIGLIN_BRUTE, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void drownedCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.DROWNED, 6);
    }

    /** A desert farmer of trade level 3 wears the desert clothes, the farmer's apron and a gold badge. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void villagerKeepsItsClothes(GameTestHelper helper) {
        net.minecraft.world.entity.npc.Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
        villager.setVillagerData(new net.minecraft.world.entity.npc.VillagerData(net.minecraft.world.entity.npc.VillagerType.DESERT,
                net.minecraft.world.entity.npc.VillagerProfession.FARMER, 3));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(villager, null);
        villager.discard();
        if (carcass == null) {
            helper.fail("Carcass assembly returned false");
        }
        java.util.List<String> coats = carcass.look.passes().stream().map(c -> c.texture().getPath()).toList();
        java.util.List<String> expected = java.util.List.of("textures/entity/villager/type/desert.png",
                "textures/entity/villager/profession/farmer.png", "textures/entity/villager/profession_level/gold.png");
        if (!coats.equals(expected)) {
            helper.fail("Expected coats " + expected + ", got " + coats);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void frogCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.FROG, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void armadilloCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.ARMADILLO, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void snifferCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.SNIFFER, 8);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void striderCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.STRIDER, 3);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void batCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.BAT, 2);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void beeCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.BEE, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void parrotCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.PARROT, 4);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void squidCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.SQUID, 9);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void glowSquidCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.GLOW_SQUID, 9);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void dolphinCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.DOLPHIN, 3);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void codCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.COD, 2);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void salmonCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.SALMON, 3);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void axolotlCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.AXOLOTL, 2);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void allayCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.ALLAY, 2);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void vexCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.VEX, 2);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void phantomCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.PHANTOM, 5);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void blazeCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.BLAZE, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pufferfishCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.PUFFERFISH, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void witherCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.WITHER, 6);
    }

    /** Knocked over as a kill would, the wither's overlapping heads and shoulders must not fling it away. */
    @GameTest(template = "empty", timeoutTicks = 260)
    public static void shovedWitherStaysPut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.boss.wither.WitherBoss wither = helper.spawn(EntityType.WITHER, new BlockPos(5, 2, 5));
        Vec3 start = wither.position();
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(wither, null);
        wither.discard();
        if (carcass == null) {
            helper.fail("Carcass assembly returned null");
            return;
        }
        CarcassAssembler.shove(level, carcass, new Vec3(1, 0, 0));
        double[] farthest = {0};
        Vector3d[] landed = {null};
        helper.runAfterDelay(120, () -> {
            if (SubLevelContainer.getContainer(level).getSubLevel(carcass.bones.get(carcass.rootBone)) instanceof ServerSubLevel body) {
                landed[0] = new Vector3d(body.logicalPose().position());
            }
        });
        for (int t = 1; t <= 200; t++) {
            helper.runAfterDelay(t, () -> {
                for (UUID id : carcass.bones.values()) {
                    if (SubLevelContainer.getContainer(level).getSubLevel(id) instanceof ServerSubLevel body) {
                        Vector3d p = body.logicalPose().position();
                        farthest[0] = Math.max(farthest[0], p.distance(start.x, start.y, start.z));
                    }
                }
            });
        }
        helper.runAfterDelay(210, () -> {
            BloodAndBones.LOGGER.info("[wither] farthest bone {} blocks from where it died", farthest[0]);
            if (farthest[0] > 6.0) {
                helper.fail("The wither carcass flew " + farthest[0] + " blocks");
            }
            // once down it must lie still, not crawl about on its own
            if (landed[0] != null && SubLevelContainer.getContainer(level).getSubLevel(carcass.bones.get(carcass.rootBone)) instanceof ServerSubLevel body) {
                double crawl = body.logicalPose().position().distance(landed[0]);
                BloodAndBones.LOGGER.info("[wither] moved {} blocks after landing", crawl);
                if (crawl > 0.75) {
                    helper.fail("The wither carcass crawled " + crawl + " blocks after it landed");
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ghastCarcassAssembles(GameTestHelper helper) {
        // dropped straight down with no knock at all, nine tentacles hold the body up like stilts; a kill
        // always knocks it, and knocked over it lies down
        animalTest(helper, EntityType.GHAST, 10, true);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void slimeCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.SLIME, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void magmaCubeCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.MAGMA_CUBE, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void guardianCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.GUARDIAN, 4);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void elderGuardianCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.ELDER_GUARDIAN, 4);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void silverfishCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.SILVERFISH, 7);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void endermiteCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.ENDERMITE, 4);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void snowGolemCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.SNOW_GOLEM, 5);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void wardenCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.WARDEN, 6);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void tadpoleCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.TADPOLE, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void breezeCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.BREEZE, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void shulkerCarcassAssembles(GameTestHelper helper) {
        animalTest(helper, EntityType.SHULKER, 3);
    }

    /** A white llama and a brown panda keep their colours on the carcass. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void llamaAndPandaKeepTheirColours(GameTestHelper helper) {
        net.minecraft.world.entity.animal.horse.Llama llama = helper.spawn(EntityType.LLAMA, new BlockPos(3, 2, 3));
        llama.setVariant(net.minecraft.world.entity.animal.horse.Llama.Variant.WHITE);
        net.minecraft.world.entity.animal.Panda panda = helper.spawn(EntityType.PANDA, new BlockPos(7, 2, 7));
        panda.setMainGene(net.minecraft.world.entity.animal.Panda.Gene.BROWN);
        panda.setHiddenGene(net.minecraft.world.entity.animal.Panda.Gene.BROWN);
        CarcassSavedData.Carcass llamaCarcass = CarcassAssembler.assemble(llama, null);
        CarcassSavedData.Carcass pandaCarcass = CarcassAssembler.assemble(panda, null);
        if (llamaCarcass == null || pandaCarcass == null) {
            helper.fail("Carcass assembly returned false");
        }
        if (!llamaCarcass.look.texture().getPath().equals("textures/entity/llama/white.png")) {
            helper.fail("The white llama should wear white.png, not " + llamaCarcass.look.texture());
        }
        if (!pandaCarcass.look.texture().getPath().equals("textures/entity/panda/brown_panda.png")) {
            helper.fail("The brown panda should wear brown_panda.png, not " + pandaCarcass.look.texture());
        }
        llama.discard();
        panda.discard();
        helper.succeed();
    }

    /** A Meat Hook kill keeps the meat in the carcass but drops what the donkey carried: saddle, chest, and its contents. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void meatHookKillDropsBelongings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.animal.horse.Donkey donkey = helper.spawn(EntityType.DONKEY, new BlockPos(5, 2, 5));
        donkey.setTamed(true);
        donkey.getSlot(499).set(new ItemStack(net.minecraft.world.item.Items.CHEST));
        donkey.getSlot(400).set(new ItemStack(net.minecraft.world.item.Items.SADDLE));
        donkey.getSlot(500).set(new ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        donkey.hurt(level.damageSources().playerAttack(player), 1000.0F);
        helper.runAfterDelay(com.avicagan.bloodandbones.carcass.CarcassHandover.TICKS + 2, () -> {
            if (!donkey.isRemoved()) {
                helper.fail("The donkey should be gone once the carcass has taken over");
            }
            if (itemsInArena(helper, net.minecraft.world.item.Items.CHEST) != 1
                    || itemsInArena(helper, net.minecraft.world.item.Items.SADDLE) != 1
                    || itemsInArena(helper, net.minecraft.world.item.Items.DIAMOND) != 3) {
                helper.fail("Expected the chest, the saddle and 3 diamonds on the ground; chest " + itemsInArena(helper, net.minecraft.world.item.Items.CHEST)
                        + ", saddle " + itemsInArena(helper, net.minecraft.world.item.Items.SADDLE)
                        + ", diamonds " + itemsInArena(helper, net.minecraft.world.item.Items.DIAMOND));
            }
            helper.succeed();
        });
    }

    /** A nether mob drains Soul Blood, not blood: a hoglin hung over Bleeding Racks fills them with it. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void hoglinBleedsSoulBlood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.monster.hoglin.Hoglin hoglin = helper.spawn(EntityType.HOGLIN, new BlockPos(5, 2, 5));
        hoglin.setImmuneToZombification(true);
        if (CarcassAssembler.assemble(hoglin, null) == null) {
            helper.fail("Carcass assembly returned false");
        }
        hoglin.discard();
        helper.setBlock(new BlockPos(5, 7, 5), net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(new BlockPos(5, 6, 5), com.avicagan.bloodandbones.registry.BBBlocks.SHACKLE_HOOK.get().defaultBlockState()
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
            ((com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 6, 5)))).toggle(level, player);
        });
        helper.runAfterDelay(80, () -> {
            for (int x = 3; x <= 7; x++) {
                for (int z = 3; z <= 7; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), com.avicagan.bloodandbones.registry.BBBlocks.BLEEDING_RACK.getDefaultState());
                }
            }
            // the rack right under it already holds some blood: the Soul Blood goes to the others
            ((com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 1, 5))))
                    .collect(new net.neoforged.neoforge.fluids.FluidStack(com.avicagan.bloodandbones.registry.BBFluids.blood(), 200),
                            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        });
        helper.runAfterDelay(300, () -> {
            var bloodRack = (com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 1, 5)));
            if (!bloodRack.getFluid().is(com.avicagan.bloodandbones.registry.BBFluids.blood()) || bloodRack.getFluid().getAmount() != 200) {
                helper.fail("The rack of blood should be left as it was, has " + bloodRack.getFluid().getAmount() + " of " + bloodRack.getFluid().getFluid());
            }
            int soul = 0;
            for (int x = 3; x <= 7; x++) {
                for (int z = 3; z <= 7; z++) {
                    if (level.getBlockEntity(helper.absolutePos(new BlockPos(x, 1, z))) instanceof com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity rack
                            && rack != bloodRack) {
                        net.neoforged.neoforge.fluids.FluidStack fluid = rack.getFluid();
                        if (!fluid.isEmpty() && !fluid.is(com.avicagan.bloodandbones.registry.BBFluids.soulBlood())) {
                            helper.fail("A hoglin should drain Soul Blood, a rack holds " + fluid.getFluid());
                        }
                        soul += fluid.getAmount();
                    }
                }
            }
            if (soul <= 0) {
                helper.fail("No Soul Blood in the racks under a hung hoglin");
            }
            helper.succeed();
        });
    }

    /** A cow hung over Bleeding Racks drains into them; every drop that leaves the body is in a rack. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void hangingCarcassBleedsIntoRack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        if (CarcassAssembler.assemble(cow, null) == null) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();
        helper.setBlock(new BlockPos(5, 7, 5), net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(new BlockPos(5, 6, 5), com.avicagan.bloodandbones.registry.BBBlocks.SHACKLE_HOOK.get().defaultBlockState()
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
            ((com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 6, 5)))).toggle(level, player);
        });
        helper.runAfterDelay(80, () -> {
            for (int x = 3; x <= 7; x++) {
                for (int z = 3; z <= 7; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), com.avicagan.bloodandbones.registry.BBBlocks.BLEEDING_RACK.getDefaultState());
                }
            }
        });
        helper.runAfterDelay(300, () -> {
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            if (carcass.bloodMax <= 0.0F) {
                helper.fail("A cow should hold blood, bloodMax " + carcass.bloodMax);
            }
            int inRacks = 0;
            for (int x = 3; x <= 7; x++) {
                for (int z = 3; z <= 7; z++) {
                    if (level.getBlockEntity(helper.absolutePos(new BlockPos(x, 1, z))) instanceof com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity rack) {
                        net.neoforged.neoforge.fluids.FluidStack fluid = rack.getFluid();
                        if (!fluid.isEmpty() && !fluid.is(com.avicagan.bloodandbones.registry.BBFluids.blood())) {
                            helper.fail("A rack holds " + fluid.getFluid() + ", not blood");
                        }
                        inRacks += fluid.getAmount();
                    }
                }
            }
            // before the racks were down the blood fell on the floor; count only what left after
            if (inRacks < 100) {
                helper.fail("Expected blood in the racks after 11 seconds of hanging over them, found " + inRacks + " mB (body has " + carcass.blood + " of " + carcass.bloodMax + ")");
            }
            helper.succeed();
        });
    }

    /** Skeletons have no blood; a cow has about a bucket, all of it in the body, none in a severed leg. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void bloodBelongsToTheBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.monster.Skeleton skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(2, 2, 2));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(7, 2, 7));
        CarcassSavedData.Carcass bones = CarcassAssembler.assemble(skeleton, null);
        CarcassSavedData.Carcass meat = CarcassAssembler.assemble(cow, null);
        skeleton.discard();
        cow.discard();
        com.avicagan.bloodandbones.carcass.CarcassBleeding.ensureBlood(bones);
        com.avicagan.bloodandbones.carcass.CarcassBleeding.ensureBlood(meat);
        if (bones.bloodMax != 0.0F) {
            helper.fail("A skeleton should have no blood, has " + bones.bloodMax);
        }
        if (meat.bloodMax < 500.0F || meat.blood != meat.bloodMax) {
            helper.fail("A cow should hold most of a bucket, has " + meat.blood + " of " + meat.bloodMax);
        }
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            for (int i = 0; i < com.avicagan.bloodandbones.carcass.CarcassButchery.CUTS_TO_SEVER; i++) {
                com.avicagan.bloodandbones.carcass.CarcassButchery.cut(level, null, meat, "left_front_leg", null);
            }
            for (CarcassSavedData.Carcass other : CarcassSavedData.get(level).all()) {
                if (other.rootBone.equals("left_front_leg") && other.bloodMax != 0.0F) {
                    helper.fail("A severed leg should hold no blood, has " + other.bloodMax);
                }
            }
            meat.blood = 0.0F;
            if (!meat.isBled()) {
                helper.fail("A drained cow should count as bled");
            }
            helper.succeed();
        });
    }

    /**
     * A carcass that has been rotten long enough falls apart into rotten flesh and bones and its bodies go;
     * one kept on blue ice does not, however long it has been rotten.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void rottenCarcassFallsApart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 6; x <= 9; x++) {
            for (int z = 6; z <= 9; z++) {
                helper.setBlock(new BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.BLUE_ICE);
            }
        }
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 2, 3));
        Cow iced = helper.spawn(EntityType.COW, new BlockPos(7, 2, 7));
        Cow still = helper.spawn(EntityType.COW, new BlockPos(2, 2, 8));
        CarcassSavedData.Carcass rotting = CarcassAssembler.assemble(cow, null);
        CarcassSavedData.Carcass kept = CarcassAssembler.assemble(iced, null);
        CarcassSavedData.Carcass folded = CarcassAssembler.assemble(still, null);
        cow.discard();
        iced.discard();
        still.discard();
        if (rotting == null || kept == null || folded == null) {
            helper.fail("Carcass assembly returned null");
            return;
        }
        float due = com.avicagan.bloodandbones.config.BBServerConfig.crumbleTicks();
        rotting.freshness = 0.0F;
        rotting.decay = due - 5.0F;
        kept.freshness = 0.0F;
        kept.decay = due + 100.0F;
        UUID torso = rotting.bones.get(rotting.rootBone);
        helper.runAfterDelay(60, () -> {
            if (CarcassSavedData.get(level).carcass(rotting.id) != null) {
                helper.fail("The rotten carcass is still here (decay " + rotting.decay + " of " + due + ")");
            }
            SubLevel body = SubLevelContainer.getContainer(level).getSubLevel(torso);
            if (body != null && !body.isRemoved()) {
                helper.fail("The rotten carcass's torso body is still here");
            }
            AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12);
            // the body alone leaves at least one rotten flesh (4.2 beef, halved for crumbling, halved for rot)
            if (level.getEntitiesOfClass(ItemEntity.class, area, item -> item.getItem().is(net.minecraft.world.item.Items.ROTTEN_FLESH)).isEmpty()) {
                helper.fail("Nothing was left where the carcass fell apart");
            }
            if (CarcassSavedData.get(level).carcass(kept.id) == null) {
                helper.fail("The carcass on blue ice fell apart");
            }
        });
        // a still carcass folds its limbs into the torso's body: falling apart must still count every piece
        int allPieces = folded.bones.size();
        helper.runAfterDelay(200, () -> {
            if (!folded.resting) {
                helper.fail("The third carcass should be resting by now");
            }
            if (com.avicagan.bloodandbones.carcass.CarcassRot.pieces(folded).size() != allPieces) {
                helper.fail("A resting carcass lists " + com.avicagan.bloodandbones.carcass.CarcassRot.pieces(folded) + " of its " + allPieces + " pieces");
            }
            folded.freshness = 0.0F;
            folded.decay = due - 5.0F;
        });
        helper.runAfterDelay(260, () -> {
            if (CarcassSavedData.get(level).carcass(folded.id) != null) {
                helper.fail("The resting rotten carcass is still here");
            }
            helper.succeed();
        });
    }

    /** The server-wide bloodless rule exists, is off by default, and can be switched with players about. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bloodlessGameRule(GameTestHelper helper) {
        var rules = helper.getLevel().getGameRules();
        var rule = rules.getRule(com.avicagan.bloodandbones.registry.BBGameRules.BLOODLESS);
        if (rule.get()) {
            helper.fail("Bloodless should be off by default");
        }
        helper.makeMockPlayer(GameType.SURVIVAL);
        rule.set(true, helper.getLevel().getServer());
        boolean on = rules.getBoolean(com.avicagan.bloodandbones.registry.BBGameRules.BLOODLESS);
        rule.set(false, helper.getLevel().getServer());
        if (!on) {
            helper.fail("The rule did not switch on");
        }
        helper.succeed();
    }

    /**
     * A leg cut off shows a wound on both ends, the stump on the body and the leg's own cut end, and both
     * pour for a while: the body loses blood and the floor gets stained.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cutLimbsShowWounds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        if (carcass == null) {
            helper.fail("Carcass assembly returned null");
            return;
        }
        if (!com.avicagan.bloodandbones.carcass.CarcassRot.cuts(carcass).isEmpty()) {
            helper.fail("A whole carcass has no wounds, got " + com.avicagan.bloodandbones.carcass.CarcassRot.cuts(carcass));
        }
        helper.runAfterDelay(10, () -> {
            UUID legId = carcass.bones.get("right_front_leg");
            com.avicagan.bloodandbones.carcass.CarcassButchery.sever(level, carcass, "right_front_leg", null);
            CarcassSavedData.Carcass leg = CarcassSavedData.get(level).carcassOfSubLevel(legId);
            List<String> expected = List.of("body>right_front_leg");
            if (!com.avicagan.bloodandbones.carcass.CarcassRot.cuts(carcass).equals(expected)) {
                helper.fail("The body should have one stump, got " + com.avicagan.bloodandbones.carcass.CarcassRot.cuts(carcass));
            }
            if (leg == null || leg == carcass || !com.avicagan.bloodandbones.carcass.CarcassRot.cuts(leg).equals(expected)) {
                helper.fail("The leg should have its own cut end, got " + (leg == null ? "no record" : com.avicagan.bloodandbones.carcass.CarcassRot.cuts(leg)));
            }
            // and the cells that draw them were told at once
            for (UUID id : List.of(carcass.bones.get(carcass.rootBone), legId)) {
                if (!(SubLevelContainer.getContainer(level).getSubLevel(id) instanceof ServerSubLevel body)
                        || !(level.getBlockEntity(body.getPlot().getCenterBlock()) instanceof com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity cell)
                        || !cell.cuts().equals(expected)) {
                    helper.fail("A root cell was not told about the cut");
                    return;
                }
            }
            if (!carcass.gushing.containsKey("body>right_front_leg") || !leg.gushing.containsKey("body>right_front_leg")) {
                helper.fail("Both ends of a fresh cut should pour");
            }
        });
        float[] bloodBefore = {0};
        helper.runAfterDelay(11, () -> bloodBefore[0] = carcass.blood);
        helper.runAfterDelay(150, () -> {
            if (carcass.blood >= bloodBefore[0]) {
                helper.fail("The stump should drain the body, blood " + carcass.blood + " of " + bloodBefore[0]);
            }
            int stains = 0;
            for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(10, 2, 10)))) {
                if (level.getBlockState(pos).is(com.avicagan.bloodandbones.registry.BBBlocks.BLOOD_STAIN.get())) {
                    stains++;
                }
            }
            if (stains == 0) {
                helper.fail("A fresh cut left no blood on the floor");
            }
            helper.succeed();
        });
    }

    /** Every recipe file parsed: a broken one only logs an error, so check they all loaded. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recipesLoad(GameTestHelper helper) {
        for (String name : new String[]{"meat_hook", "cleaver", "flensing_knife", "shackle_hook", "bleeding_rack", "raw_hide_splashing",
                "cooked_meat_from_raw_meat_smelting", "mangler", "guillotine", "beheader", "deglover", "blood_steel_ingot_filling", "blood_diamond_filling", "soul_blood_mixing",
                "soul_blood_fermenting", "blood_steel_block", "blood_steel_ingot_from_block", "blood_steel_ingot_from_nuggets", "blood_steel_nugget",
                "blood_steel_cleaver", "spit_roast", "specimen_jar", "butcher_table", "butcher_hook", "gut_chain", "bloody_casing_filling",
                "surgery_table", "peg_leg", "hook_hand", "copper_fluid_backtank", "gold_fluid_backtank", "iron_fluid_backtank", "diamond_fluid_backtank",
                "blood_steel_fluid_backtank", "blood_diamond_fluid_backtank", "soul_netherite_fluid_backtank", "soul_netherite_ingot"}) {
            if (helper.getLevel().getRecipeManager().byKey(com.avicagan.bloodandbones.BloodAndBones.asResource(name)).isEmpty()) {
                helper.fail("Recipe " + name + " did not load");
            }
        }
        helper.succeed();
    }

    /** The mod's own sounds are registered (their subtitles and the vanilla sounds they play are client data). */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void soundsRegistered(GameTestHelper helper) {
        for (String name : new String[]{"carcass.cut", "carcass.sever", "carcass.skin", "carcass.pick_up", "carcass.thud",
                "carcass.clatter", "carcass.crumble", "machine.blade"}) {
            if (!net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(com.avicagan.bloodandbones.BloodAndBones.asResource(name))) {
                helper.fail("Sound " + name + " is not registered");
            }
        }
        helper.succeed();
    }

    /** Every advancement parsed and loaded. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void advancementsLoad(GameTestHelper helper) {
        for (String name : new String[]{"butchery", "cleaver", "offal", "skinned", "hanging", "blood", "machine", "blood_steel", "soul_blood", "blood_diamond", "spit_roast", "specimen",
                "butcher_table", "butcher_hook", "bloody_casing", "gut_chain", "surgery_table", "severed", "prosthetic", "backtank", "soul_netherite"}) {
            if (helper.getLevel().getServer().getAdvancements().get(com.avicagan.bloodandbones.BloodAndBones.asResource(name)) == null) {
                helper.fail("Advancement " + name + " did not load");
            }
        }
        helper.succeed();
    }

    /** Shoved from the side, a tall four-legged carcass falls over instead of standing dead on stiff legs. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sidewaysPushTopplesALlama(GameTestHelper helper) {
        toppleTest(helper, EntityType.LLAMA);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sidewaysPushTopplesAHorse(GameTestHelper helper) {
        toppleTest(helper, EntityType.HORSE);
    }

    private static void toppleTest(GameTestHelper helper, EntityType<? extends net.minecraft.world.entity.Mob> type) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.Mob mob = helper.spawn(type, new BlockPos(5, 2, 5));
        mob.setYRot(0);
        mob.yBodyRot = 0;
        mob.setYHeadRot(0);
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(mob, null);
        mob.discard();
        if (carcass == null) {
            helper.fail("Carcass assembly returned false");
            return;
        }
        UUID id = carcass.id;
        double[] start = new double[1];
        helper.runAfterDelay(2, () -> {
            ServerSubLevel torso = liveBones(helper, level, carcass).get(carcass.rootBone);
            start[0] = torso.logicalPose().position().y();
            // facing south, so east is square to its side
            CarcassAssembler.shove(level, carcass, new Vec3(1, 0, 0));
        });
        helper.runAfterDelay(150, () -> {
            CarcassSavedData.Carcass now = CarcassSavedData.get(level).carcass(id);
            ServerSubLevel torso = liveBones(helper, level, now).get(now.rootBone);
            double drop = start[0] - torso.logicalPose().position().y();
            if (drop < 0.25) {
                helper.fail(type + " is still standing after a push from the side: torso dropped only " + drop);
            }
            helper.succeed();
        });
    }

    /** A red mooshroom wears the red coat and yields red mushrooms when skinned. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void mooshroomKeepsItsColour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.animal.MushroomCow cow = helper.spawn(EntityType.MOOSHROOM, new BlockPos(5, 2, 5));
        cow.setVariant(net.minecraft.world.entity.animal.MushroomCow.MushroomType.BROWN);
        if (CarcassAssembler.assemble(cow, null) == null) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            CarcassSavedData.Carcass carcass = nearestCarcass(helper, level, new BlockPos(5, 2, 5), EntityType.MOOSHROOM);
            if (!carcass.look.texture().getPath().equals("textures/entity/cow/brown_mooshroom.png")) {
                helper.fail("A brown mooshroom should wear brown_mooshroom.png, got " + carcass.look.texture());
            }
            for (int i = 0; i < com.avicagan.bloodandbones.carcass.CarcassButchery.STROKES_TO_SKIN; i++) {
                com.avicagan.bloodandbones.carcass.CarcassButchery.skin(level, null, carcass, null);
            }
        });
        helper.runAfterDelay(SETTLE_TICKS + 5, () -> {
            if (itemsInArena(helper, net.minecraft.world.item.Items.BROWN_MUSHROOM) != 2) {
                helper.fail("Skinning a brown mooshroom should give two brown mushrooms");
            }
            helper.succeed();
        });
    }

    /** Every rigged mob: the right number of bodies and joints, all of them near the spawn, none in the floor. */
    private static void animalTest(GameTestHelper helper, EntityType<? extends net.minecraft.world.entity.Mob> type, int bones) {
        animalTest(helper, type, bones, false);
    }

    private static void animalTest(GameTestHelper helper, EntityType<? extends net.minecraft.world.entity.Mob> type, int bones, boolean knock) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.Mob mob = helper.spawn(type, new BlockPos(5, 2, 5));
        mob.setBaby(false);
        if (mob instanceof net.minecraft.world.entity.monster.Slime slime) {
            slime.setSize(4, true);
        }
        Vec3 pos = mob.position();
        // a tall mob (an enderman) may not have finished falling over yet; nothing may be above its own height
        double ceiling = Math.max(2.6, mob.getBbHeight() + 0.2);
        // a big mob's tail reaches further than a cow's
        double reach = Math.max(4.0, 2.5 * mob.getBbWidth());
        CarcassSavedData.Carcass assembled = CarcassAssembler.assemble(mob, null);
        if (assembled == null) {
            helper.fail("Carcass assembly returned false for " + type);
        } else if (knock) {
            CarcassAssembler.shove(level, assembled, new Vec3(1, 0, 0));
        }
        mob.discard();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            CarcassSavedData.Carcass carcass = nearestCarcass(helper, level, new BlockPos(5, 2, 5), type);
            if (carcass.bones.size() != bones) {
                helper.fail("Expected " + bones + " bones for " + type + ", found " + carcass.bones.keySet());
            }
            requireLiveJoints(helper, carcass, bones - 1);
            for (Map.Entry<String, ServerSubLevel> bone : liveBones(helper, level, carcass).entrySet()) {
                Vector3d p = bone.getValue().logicalPose().position();
                double distance = p.distance(pos.x, pos.y, pos.z);
                if (distance > reach) {
                    helper.fail("Bone " + bone.getKey() + " of " + type + " ended up " + distance + " blocks away at " + p);
                }
                // sinking is judged by the box's lowest corner: a body's reference point can sit below the floor
                // while the body itself lies on it (a thin ghast tentacle on its side)
                double lowest = lowestCorner(carcass, bone.getKey(), bone.getValue());
                if (lowest < pos.y - 0.2) {
                    helper.fail("Bone " + bone.getKey() + " of " + type + " sank into the floor: lowest corner at " + lowest + ", body at " + p);
                }
                if (p.y > pos.y + ceiling) {
                    helper.fail("Bone " + bone.getKey() + " of " + type + " is floating at " + p);
                }
            }
            helper.succeed();
        });
    }

    /** A sheep keeps its wool colour; a sheared one has no wool coat at all. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sheepCarcassKeepsWool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.animal.Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(5, 2, 5));
        sheep.setColor(net.minecraft.world.item.DyeColor.RED);
        if (CarcassAssembler.assemble(sheep, null) == null) {
            helper.fail("Carcass assembly returned false");
        }
        sheep.discard();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            CarcassSavedData.Carcass carcass = nearestCarcass(helper, level, new BlockPos(5, 2, 5), EntityType.SHEEP);
            ServerSubLevel torso = liveBones(helper, level, carcass).get(carcass.rootBone);
            BlockPos center = torso.getPlot().getCenterBlock();
            if (!(level.getBlockEntity(center) instanceof com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity root)) {
                helper.fail("No root cell");
                return;
            }
            if (root.passes().size() != 1) {
                helper.fail("A woolly sheep should have one wool coat, found " + root.passes().size());
            }
            int expected = net.minecraft.world.entity.animal.Sheep.getColor(net.minecraft.world.item.DyeColor.RED);
            if (root.passes().get(0).tint() != expected || !root.passes().get(0).layer().equals("fur")) {
                helper.fail("Wool coat should be red on the fur layer, got " + root.passes().get(0));
            }
            if (!root.texture().getPath().endsWith("sheep/sheep.png")) {
                helper.fail("Sheep skin texture wrong: " + root.texture());
            }
            helper.succeed();
        });
    }

    /** A horse's carcass wears its own coat colour and markings. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void horseCarcassKeepsVariant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.animal.horse.Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(5, 2, 5));
        horse.setBaby(false);
        horse.setVariant(net.minecraft.world.entity.animal.horse.Variant.DARK_BROWN);
        net.minecraft.world.entity.animal.horse.Markings markings = horse.getMarkings();
        if (CarcassAssembler.assemble(horse, null) == null) {
            helper.fail("Carcass assembly returned false");
        }
        horse.discard();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            CarcassSavedData.Carcass carcass = nearestCarcass(helper, level, new BlockPos(5, 2, 5), EntityType.HORSE);
            ServerSubLevel torso = liveBones(helper, level, carcass).get(carcass.rootBone);
            BlockPos center = torso.getPlot().getCenterBlock();
            if (!(level.getBlockEntity(center) instanceof com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity root)) {
                helper.fail("No root cell");
                return;
            }
            if (!root.texture().getPath().equals("textures/entity/horse/horse_darkbrown.png")) {
                helper.fail("Dark brown horse should wear horse_darkbrown.png, got " + root.texture());
            }
            if (markings == net.minecraft.world.entity.animal.horse.Markings.NONE) {
                if (!root.passes().isEmpty()) {
                    helper.fail("An unmarked horse should have no markings coat, got " + root.passes());
                }
            } else if (root.passes().size() != 1 || !root.passes().get(0).texture().getPath().startsWith("textures/entity/horse/horse_markings_")
                    || root.passes().get(0).texture().getPath().contains("{")) {
                helper.fail("A marked horse (" + markings + ") should have one markings coat, got " + root.passes());
            }
            helper.succeed();
        });
    }

    /** What a carcass remembers survives a save: rest cells, rest poses, joints, look, rot. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void carcassRecordRoundTrips(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        if (CarcassAssembler.assemble(cow, null) == null) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();
        helper.runAfterDelay(5, () -> {
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            carcass.restCells.add(new BlockPos(1, -2, 3));
            carcass.restCells.add(new BlockPos(-4, 5, 6));
            carcass.restPoses.put("head", new CarcassSavedData.RestPose(new Vector3d(0.5, 0.25, -0.75), new org.joml.Quaterniond(0, 0.7071, 0, 0.7071)));
            carcass.freshness = 0.5F;
            carcass.rotClock = 1234L;
            CarcassSavedData.Carcass copy = CarcassSavedData.Carcass.load(carcass.save());
            if (!copy.restCells.equals(carcass.restCells)) {
                helper.fail("Rest cells did not survive a save: " + copy.restCells);
            }
            if (copy.restPoses.size() != 1 || copy.restPoses.get("head").position().distance(0.5, 0.25, -0.75) > 1.0E-9) {
                helper.fail("Rest poses did not survive a save: " + copy.restPoses);
            }
            if (copy.joints.size() != carcass.joints.size() || copy.bones.size() != carcass.bones.size()) {
                helper.fail("Joints or bones did not survive a save");
            }
            if (copy.freshness != 0.5F || copy.rotClock != 1234L || !copy.look.texture().equals(carcass.look.texture())) {
                helper.fail("Rot or look did not survive a save");
            }
            helper.succeed();
        });
    }

    /** A punch on any cell of a resting carcass unfolds it. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void punchWakesRestingCarcass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        if (CarcassAssembler.assemble(cow, null) == null) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();
        UUID[] id = new UUID[1];
        helper.runAfterDelay(5, () -> id[0] = onlyCarcass(helper, level).id);
        int folded = 30 + com.avicagan.bloodandbones.carcass.CarcassRest.STILL_TICKS + 40;
        helper.runAfterDelay(folded, () -> {
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(id[0]);
            if (carcass == null || !carcass.resting) {
                helper.fail("Carcass should be resting");
                return;
            }
            if (carcass.bones.size() != 1) {
                helper.fail("A resting carcass should remember only its torso body, found " + carcass.bones.keySet());
            }
            if (!carcass.restCells.isEmpty()) {
                helper.fail("A resting carcass should be the torso body alone, no extra cells");
            }
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            SubLevel torso = container.getSubLevel(carcass.bones.get(carcass.rootBone));
            BlockPos cell = torso.getPlot().getCenterBlock();
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))));
            player.setOldPosAndRot();
            level.getBlockState(cell).attack(level, cell, player);
        });
        helper.runAfterDelay(folded + 10, () -> {
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(id[0]);
            if (carcass.resting) {
                helper.fail("A punched carcass should have unfolded");
            }
            if (liveBones(helper, level, carcass).size() != 6) {
                helper.fail("Expected 6 bodies after the punch");
            }
            if (!carcass.restCells.isEmpty()) {
                helper.fail("Rest cells should be gone");
            }
            helper.succeed();
        });
    }

    /** Mining the floor out from under a resting carcass lets it fall again. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void restingCarcassFallsWhenUnsupported(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // a one-block-high stone platform on top of the floor, so there is somewhere to fall to
        for (int x = 2; x <= 8; x++) {
            for (int z = 2; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 2, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 3, 5));
        if (CarcassAssembler.assemble(cow, null) == null) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();
        UUID[] id = new UUID[1];
        double[] restY = new double[1];
        helper.runAfterDelay(5, () -> id[0] = nearestCarcass(helper, level, new BlockPos(5, 3, 5)).id);
        int folded = 30 + com.avicagan.bloodandbones.carcass.CarcassRest.STILL_TICKS + 40;
        helper.runAfterDelay(folded, () -> {
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(id[0]);
            if (carcass == null || !carcass.resting) {
                helper.fail("Carcass should be resting on the platform");
                return;
            }
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            restY[0] = container.getSubLevel(carcass.bones.get(carcass.rootBone)).logicalPose().position().y();
            for (int x = 2; x <= 8; x++) {
                for (int z = 2; z <= 8; z++) {
                    helper.setBlock(new BlockPos(x, 2, z), net.minecraft.world.level.block.Blocks.AIR);
                }
            }
        });
        for (int k = 1; k <= 5; k++) {
            int at = folded + k * 20;
            helper.runAfterDelay(at, () -> {
                CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(id[0]);
                if (carcass == null) {
                    return;
                }
                ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
                StringBuilder trace = new StringBuilder();
                for (Map.Entry<String, UUID> bone : carcass.bones.entrySet()) {
                    SubLevel s = container.getSubLevel(bone.getValue());
                    trace.append(' ').append(bone.getKey()).append('=').append(s == null ? "?" : String.format("%.2f", s.logicalPose().position().y()));
                }
                com.avicagan.bloodandbones.BloodAndBones.LOGGER.info("[support test] +{} resting {} lock {} cells {}:{}", at - folded, carcass.resting,
                        carcass.restLock == null ? "none" : carcass.restLock.isValid(), carcass.restCells.size(), trace);
            });
        }
        helper.runAfterDelay(folded + 100, () -> {
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(id[0]);
            if (carcass == null) {
                helper.fail("Carcass vanished");
                return;
            }
            // by now it has unfolded, fallen and may well have gone still and folded again on the lower floor;
            // the ragdoll may also land on its feet, so judge the fall by its lowest body
            double lowest = Double.MAX_VALUE;
            for (ServerSubLevel bone : liveBones(helper, level, carcass).values()) {
                lowest = Math.min(lowest, bone.logicalPose().position().y());
            }
            if (lowest > restY[0] - 0.75) {
                helper.fail("The carcass should have fallen to the lower floor: torso rested at " + restY[0] + ", lowest body now " + lowest + ", resting=" + carcass.resting);
            }
            helper.succeed();
        });
    }

    /** A Meat Hook kill keeps the mob visible and frozen for a few ticks, then swaps it for the carcass. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void meatHookKillHandsOver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        Vec3 start = cow.position();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        cow.hurt(level.damageSources().playerAttack(player), 1000.0F);
        helper.runAfterDelay(1, () -> {
            if (cow.isRemoved() || cow.isDeadOrDying()) {
                helper.fail("The cow should still be standing there for a moment after the kill");
            }
            if (cow.position().distanceTo(start) > 0.01) {
                helper.fail("The dying cow moved: " + cow.position() + " from " + start);
            }
            onlyCarcass(helper, level);
        });
        helper.runAfterDelay(com.avicagan.bloodandbones.carcass.CarcassHandover.TICKS + 2, () -> {
            if (!cow.isRemoved()) {
                helper.fail("The cow should be gone once the carcass has taken over");
            }
            helper.succeed();
        });
    }

    /** Three Cleaver cuts take a leg off: one joint fewer, the leg still a body of the carcass. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cleaverSeversLeg(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        if (CarcassAssembler.assemble(cow, null) == null) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();
        UUID[] id = new UUID[1];
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            id[0] = carcass.id;
            for (int i = 0; i < com.avicagan.bloodandbones.carcass.CarcassButchery.CUTS_TO_SEVER - 1; i++) {
                com.avicagan.bloodandbones.carcass.CarcassButchery.cut(level, null, carcass, "left_front_leg", null);
            }
            if (carcass.joints.size() != 5 || !carcass.severed.isEmpty()) {
                helper.fail("Two cuts should not sever yet");
            }
            com.avicagan.bloodandbones.carcass.CarcassButchery.cut(level, null, carcass, "left_front_leg", null);
            if (carcass.joints.size() != 4 || carcass.bones.containsKey("left_front_leg")) {
                helper.fail("Three cuts should sever the leg into its own record: joints " + carcass.joints.size() + ", bones " + carcass.bones.keySet());
            }
            if (com.avicagan.bloodandbones.carcass.CarcassButchery.cut(level, null, carcass, "body", null)) {
                helper.fail("The body must not be cuttable");
            }
        });
        helper.runAfterDelay(SETTLE_TICKS + 30, () -> {
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(id[0]);
            requireLiveJoints(helper, carcass, 4);
            if (liveBones(helper, level, carcass).size() != 5 || carcass.bones.containsKey("left_front_leg")) {
                helper.fail("The severed leg should have left the cow's record, bones now " + carcass.bones.keySet());
            }
            // the leg is a carcass of its own now
            CarcassSavedData.Carcass leg = null;
            for (CarcassSavedData.Carcass other : CarcassSavedData.get(level).all()) {
                if (other.rootBone.equals("left_front_leg") && other.entity.equals(carcass.entity) && other.look.texture().equals(carcass.look.texture())) {
                    leg = other;
                }
            }
            if (leg == null || leg.bones.size() != 1) {
                helper.fail("Expected a one-bone carcass record for the severed leg");
                return;
            }
            // light and loose: it can be picked up, and put back down
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))));
            if (!com.avicagan.bloodandbones.carcass.CarcassButchery.canPickUp(level, leg, "left_front_leg")) {
                helper.fail("A cow leg should be light enough to pick up");
            }
            if (com.avicagan.bloodandbones.carcass.CarcassButchery.canPickUp(level, carcass, "body")) {
                helper.fail("A cow body should be too heavy to pick up");
            }
            if (!com.avicagan.bloodandbones.carcass.CarcassButchery.pickUp(level, player, leg, "left_front_leg")) {
                helper.fail("Could not pick up the leg");
            }
            ItemStack held = player.getInventory().getItem(0);
            com.avicagan.bloodandbones.item.CarcassPieceItem.Piece piece = com.avicagan.bloodandbones.item.CarcassPieceItem.piece(held);
            if (piece == null || !piece.bone().equals("left_front_leg") || !piece.entity().equals(carcass.entity)) {
                helper.fail("Picking up should give a piece item for the cow leg, got " + held);
                return;
            }
            com.avicagan.bloodandbones.carcass.rig.Rig rig = com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(piece.entity()).orElseThrow();
            CarcassSavedData.Carcass placed = CarcassAssembler.assemblePiece(level, rig, rig.bone("left_front_leg").orElseThrow(), piece.look(), piece.freshness(),
                    Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))), 0.0F);
            if (placed == null || placed.bones.size() != 1 || !placed.rootBone.equals("left_front_leg")) {
                helper.fail("Putting the piece down should make a one-bone carcass");
            }
            helper.succeed();
        });
    }

    /**
     * A piece picked up and put down keeps its blood and its rot (it must not come back full of blood), and a
     * dragged limb cut off stays dragged, now as a carcass of its own.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void piecesKeepTheirBloodAndDragsFollowCuts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.animal.Pufferfish fish = helper.spawn(EntityType.PUFFERFISH, new BlockPos(2, 2, 2));
        CarcassSavedData.Carcass drained = CarcassAssembler.assemble(fish, null);
        fish.discard();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(6, 2, 5));
        CarcassSavedData.Carcass body = CarcassAssembler.assemble(cow, null);
        cow.discard();
        if (drained == null || body == null) {
            helper.fail("Carcass assembly returned null");
            return;
        }
        drained.blood = 40.0F;
        drained.bloodMax = 125.0F;
        drained.decay = 1234.0F;
        ItemStack item = com.avicagan.bloodandbones.item.CarcassPieceItem.of(drained, drained.rootBone);
        Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
        placer.setItemInHand(InteractionHand.MAIN_HAND, item);
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 8));
        item.useOn(new net.minecraft.world.item.context.UseOnContext(placer, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), net.minecraft.core.Direction.UP, floor, false)));
        CarcassSavedData.Carcass placed = null;
        for (CarcassSavedData.Carcass other : CarcassSavedData.get(level).all()) {
            if (other != drained && other.entity.equals(drained.entity) && Math.abs(other.decay - 1234.0F) < 0.5F) {
                placed = other;
            }
        }
        if (placed == null) {
            helper.fail("Putting the piece down did not make a carcass that kept its rot");
            return;
        }
        if (Math.abs(placed.blood - 40.0F) > 0.01F || Math.abs(placed.bloodMax - 125.0F) > 0.01F) {
            helper.fail("A piece put back down should keep its blood: " + placed.blood + " of " + placed.bloodMax);
        }

        Player dragger = helper.makeMockPlayer(GameType.SURVIVAL);
        dragger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        dragger.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 2, 5))));
        dragger.setOldPosAndRot();
        helper.runAfterDelay(10, () -> {
            UUID legId = body.bones.get("right_hind_leg");
            if (!(SubLevelContainer.getContainer(level).getSubLevel(legId) instanceof ServerSubLevel leg)
                    || !CarcassDrag.start(level, dragger, leg.getPlot().getCenterBlock(), null)) {
                helper.fail("Could not start dragging the leg");
                return;
            }
            // a piece put down by clicking on another carcass lands by that carcass, not out in its plot
            UUID torsoId = body.bones.get(body.rootBone);
            if (SubLevelContainer.getContainer(level).getSubLevel(torsoId) instanceof ServerSubLevel torso) {
                ItemStack another = com.avicagan.bloodandbones.item.CarcassPieceItem.of(drained, drained.rootBone);
                placer.setItemInHand(InteractionHand.MAIN_HAND, another);
                BlockPos cell = torso.getPlot().getCenterBlock();
                java.util.Set<UUID> before = new java.util.HashSet<>();
                CarcassSavedData.get(level).all().forEach(c -> before.add(c.id));
                another.useOn(new net.minecraft.world.item.context.UseOnContext(placer, InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(cell).add(0, 0.5, 0), net.minecraft.core.Direction.UP, cell, false)));
                Vector3d torsoAt = torso.logicalPose().position();
                boolean near = false;
                for (CarcassSavedData.Carcass other : CarcassSavedData.get(level).all()) {
                    if (!before.contains(other.id) && other.entity.equals(drained.entity)
                            && SubLevelContainer.getContainer(level).getSubLevel(other.bones.get(other.rootBone)) instanceof ServerSubLevel fishBody) {
                        double distance = fishBody.logicalPose().position().distance(torsoAt);
                        near = distance < 6.0;
                        if (!near) {
                            helper.fail("A piece put down on a carcass landed " + distance + " blocks away");
                        }
                    }
                }
                if (!near) {
                    helper.fail("A piece put down on a carcass did not appear");
                }
            }
            com.avicagan.bloodandbones.carcass.CarcassButchery.sever(level, body, "right_hind_leg", null);
            CarcassSavedData.Carcass cut = CarcassSavedData.get(level).carcassOfSubLevel(legId);
            if (cut == null || cut == body || !CarcassDrag.isDraggingCarcass(cut.id) || CarcassDrag.isDraggingCarcass(body.id)) {
                helper.fail("The drag should follow the leg into its own record");
            }
            CarcassDrag.stop(level, dragger);
            helper.succeed();
        });
    }

    /** World height of the lowest corner of a bone's physics box. */
    private static double lowestCorner(CarcassSavedData.Carcass carcass, String boneName, ServerSubLevel body) {
        com.avicagan.bloodandbones.carcass.rig.Bone bone = com.avicagan.bloodandbones.carcass.rig.RigManager.forCarcass(carcass)
                .flatMap(rig -> rig.bone(boneName)).orElse(null);
        if (bone == null) {
            return body.logicalPose().position().y;
        }
        Vector3d origin = CarcassAssembler.boneOriginInPlot(body, bone);
        double lowest = Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vector3d corner = new Vector3d((i & 1) == 0 ? bone.boxMin().x : bone.boxMax().x, (i & 2) == 0 ? bone.boxMin().y : bone.boxMax().y,
                    (i & 4) == 0 ? bone.boxMin().z : bone.boxMax().z).div(16.0).add(origin);
            lowest = Math.min(lowest, body.logicalPose().transformPosition(corner).y);
        }
        return lowest;
    }

    /** Items of a kind lying in this test's arena. */
    private static int itemsInArena(GameTestHelper helper, net.minecraft.world.item.Item item) {
        AABB area = AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(10, 6, 10)));
        int count = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area)) {
            if (entity.getItem().is(item)) {
                count += entity.getItem().getCount();
            }
        }
        return count;
    }

    /** Four Flensing Knife strokes skin a red sheep: raw hide and red wool drop, and it shows bare meat. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void flensingKnifeSkinsSheep(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.animal.Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(5, 2, 5));
        sheep.setColor(net.minecraft.world.item.DyeColor.RED);
        if (CarcassAssembler.assemble(sheep, null) == null) {
            helper.fail("Carcass assembly returned false");
        }
        sheep.discard();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            CarcassSavedData.Carcass carcass = nearestCarcass(helper, level, new BlockPos(5, 2, 5), EntityType.SHEEP);
            for (int i = 0; i < com.avicagan.bloodandbones.carcass.CarcassButchery.STROKES_TO_SKIN - 1; i++) {
                com.avicagan.bloodandbones.carcass.CarcassButchery.skin(level, null, carcass, null);
            }
            if (carcass.skinned) {
                helper.fail("Skinned too early");
            }
            com.avicagan.bloodandbones.carcass.CarcassButchery.skin(level, null, carcass, null);
            if (!carcass.skinned || !carcass.look.texture().equals(com.avicagan.bloodandbones.carcass.CarcassLook.FLESH)) {
                helper.fail("The sheep should be skinned and wear bare flesh, look " + carcass.look);
            }
            if (com.avicagan.bloodandbones.carcass.CarcassButchery.skin(level, null, carcass, null)) {
                helper.fail("A skinned carcass cannot be skinned again");
            }
            ServerSubLevel torso = liveBones(helper, level, carcass).get(carcass.rootBone);
            if (!(level.getBlockEntity(torso.getPlot().getCenterBlock()) instanceof com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity root)
                    || !root.texture().equals(com.avicagan.bloodandbones.carcass.CarcassLook.FLESH) || !root.passes().isEmpty()) {
                helper.fail("The torso's root cell should draw bare flesh with no wool");
            }
        });
        helper.runAfterDelay(SETTLE_TICKS + 5, () -> {
            if (itemsInArena(helper, BBItems.RAW_HIDE.get()) < 1) {
                helper.fail("Skinning a sheep should drop raw hide");
            }
            if (itemsInArena(helper, net.minecraft.world.item.Items.RED_WOOL) != 2) {
                helper.fail("Skinning a red sheep should drop two red wool, found " + itemsInArena(helper, net.minecraft.world.item.Items.RED_WOOL));
            }
            helper.succeed();
        });
    }

    /** Cut every limb off a cow, then cut the body down: beef, bone, offal. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cleaverButchersBody(GameTestHelper helper) {
        butcherBodyTest(helper, 1.0F, false);
    }

    /** The same, badly rotten: the beef has turned to rotten flesh and the offal is gone. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void rottenBodyGivesRottenFlesh(GameTestHelper helper) {
        butcherBodyTest(helper, 0.1F, true);
    }

    private static void butcherBodyTest(GameTestHelper helper, float freshness, boolean rotten) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        if (CarcassAssembler.assemble(cow, null) == null) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();
        UUID[] body = new UUID[1];
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            CarcassSavedData.Carcass carcass = nearestCarcass(helper, level, new BlockPos(5, 2, 5), EntityType.COW);
            carcass.freshness = freshness;
            carcass.rotRate = 0.0F; // hold it where the test put it
            carcass.rotSampleTicks = Integer.MIN_VALUE + 1000;
            if (com.avicagan.bloodandbones.carcass.CarcassButchery.cut(level, null, carcass, carcass.rootBone, null)) {
                helper.fail("The body must not be cuttable while limbs hang off it");
            }
            for (String limb : List.of("head", "left_front_leg", "right_front_leg", "left_hind_leg", "right_hind_leg")) {
                for (int i = 0; i < com.avicagan.bloodandbones.carcass.CarcassButchery.CUTS_TO_SEVER; i++) {
                    com.avicagan.bloodandbones.carcass.CarcassButchery.cut(level, null, carcass, limb, null);
                }
            }
            if (!carcass.joints.isEmpty() || carcass.bones.size() != 1) {
                helper.fail("Every limb should be off, left " + carcass.bones.keySet() + " joints " + carcass.joints.size());
            }
            body[0] = carcass.bones.get(carcass.rootBone);
            for (int i = 0; i < com.avicagan.bloodandbones.carcass.CarcassButchery.CUTS_TO_BUTCHER; i++) {
                com.avicagan.bloodandbones.carcass.CarcassButchery.cut(level, null, carcass, carcass.rootBone, null);
            }
        });
        helper.runAfterDelay(SETTLE_TICKS + 5, () -> {
            if (SubLevelContainer.getContainer(level).getSubLevel(body[0]) != null) {
                helper.fail("The butchered body should be gone");
            }
            int beef = itemsInArena(helper, net.minecraft.world.item.Items.BEEF);
            int rottenFlesh = itemsInArena(helper, net.minecraft.world.item.Items.ROTTEN_FLESH);
            int bones = itemsInArena(helper, net.minecraft.world.item.Items.BONE);
            int offal = itemsInArena(helper, BBItems.OFFAL.get());
            if (bones < 1) {
                helper.fail("A cow body should give at least one bone, got " + bones);
            }
            if (rotten) {
                if (beef != 0 || rottenFlesh < 2 || offal != 0) {
                    helper.fail("A rotten body should give rotten flesh and no beef or offal: beef " + beef + ", rotten " + rottenFlesh + ", offal " + offal);
                }
            } else if (beef < 4 || offal < 1 || rottenFlesh != 0) {
                helper.fail("A fresh cow body should give at least 4 beef and some offal: beef " + beef + ", offal " + offal + ", rotten " + rottenFlesh);
            }
            helper.succeed();
        });
    }

    /** The carcass whose root limb is nearest this test's arena center; tests are placed side by side. */
    private static CarcassSavedData.Carcass onlyCarcass(GameTestHelper helper, ServerLevel level) {
        return nearestCarcass(helper, level, new BlockPos(5, 2, 5));
    }

    private static CarcassSavedData.Carcass nearestCarcass(GameTestHelper helper, ServerLevel level, BlockPos relative) {
        return nearestCarcass(helper, level, relative, null);
    }

    /** Tests sit side by side and carcasses travel, so when the animal is known only its own kind counts. */
    private static CarcassSavedData.Carcass nearestCarcass(GameTestHelper helper, ServerLevel level, BlockPos relative, @org.jetbrains.annotations.Nullable EntityType<?> type) {
        CarcassSavedData data = CarcassSavedData.get(level);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            helper.fail("No Sable container");
        }
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(relative));
        CarcassSavedData.Carcass nearest = null;
        double best = 6.0;
        for (CarcassSavedData.Carcass carcass : data.all()) {
            if (type != null && !carcass.entity.equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type))) {
                continue;
            }
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
