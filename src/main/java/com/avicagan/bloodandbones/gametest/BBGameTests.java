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
                helper.fail("A Meat Hook kill should drop nothing, found " + drops.size() + " item entities");
            }
            helper.succeed();
        });
    }

    /** Dragging: hook the body, walk away, the carcass follows; let go and it stops following. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void meatHookDragsCarcass(GameTestHelper helper) {
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
            BlockPos rootCell = body.getPlot().getCenterBlock();
            if (!CarcassDrag.start(level, player, rootCell, null)) {
                helper.fail("Could not start dragging the body");
            }
            if (!CarcassDrag.isDragging(player)) {
                helper.fail("Player is not marked as dragging");
            }
            // stand 3 blocks away, facing away from the carcass, and keep ticking the tether
            player.setPos(start.add(3.0, 0.0, 0.0));
            player.setYRot(-90.0F);
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
            org.joml.Vector3d hook = body.logicalPose().transformPosition(CarcassDrag.current(player).anchorPlot, new org.joml.Vector3d());
            org.joml.Vector3d target = CarcassDrag.debugTarget(player);
            double gap = hook.distance(target);
            if (gap > 1.0) {
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

    private static CarcassSavedData.Carcass onlyCarcass(GameTestHelper helper, ServerLevel level) {
        CarcassSavedData data = CarcassSavedData.get(level);
        List<CarcassSavedData.Carcass> nearby = new ArrayList<>();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            helper.fail("No Sable container");
        }
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 2, 5)));
        for (CarcassSavedData.Carcass carcass : data.all()) {
            UUID rootId = carcass.bones.get(carcass.rootBone);
            SubLevel root = rootId == null ? null : container.getSubLevel(rootId);
            if (root instanceof ServerSubLevel serverRoot && !serverRoot.isRemoved()
                    && serverRoot.logicalPose().position().distance(origin.x, origin.y, origin.z) < 4.0) {
                nearby.add(carcass);
            }
        }
        if (nearby.size() != 1) {
            StringBuilder sb = new StringBuilder();
            for (CarcassSavedData.Carcass carcass : data.all()) {
                UUID rootId = carcass.bones.get(carcass.rootBone);
                SubLevel root = rootId == null ? null : container.getSubLevel(rootId);
                sb.append(String.format(" [%s root=%s removed=%s dist=%.1f]", carcass.id.toString().substring(0, 8),
                        root == null ? "unloaded" : "loaded", root != null && root.isRemoved(),
                        root == null ? -1.0 : root.logicalPose().position().distance(origin.x, origin.y, origin.z)));
            }
            helper.fail("Expected exactly 1 carcass near this test at tick " + helper.getTick() + ", found " + nearby.size() + "; all:" + sb);
        }
        return nearby.get(0);
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
