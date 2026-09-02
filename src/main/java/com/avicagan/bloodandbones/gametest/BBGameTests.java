package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
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
    public static void register(RegisterGameTestsEvent event) {
        event.register(BBGameTests.class);
    }

    /** Direct assembly: six limbs, five joints, everything stays put. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cowCarcassAssembles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
        Vec3 cowPos = cow.position();

        if (!CarcassAssembler.assemble(cow, null)) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();

        helper.runAfterDelay(40, () -> {
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
                if (pos.y < cowPos.y - 2.0) {
                    helper.fail("Bone " + bone.getKey() + " fell through the floor to " + pos);
                }
            }
            helper.succeed();
        });
    }

    /** Joints are rebuilt from saved data when their handles are gone, as after a world reload. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cowCarcassJointsRestore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
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
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        cow.hurt(level.damageSources().playerAttack(player), 1000.0F);

        helper.runAfterDelay(20, () -> {
            if (cow.isAlive() || !cow.isRemoved()) {
                helper.fail("Cow should be gone after a Meat Hook kill");
            }
            CarcassSavedData.Carcass carcass = onlyCarcass(helper, level);
            requireLiveJoints(helper, carcass, 5);
            AABB area = AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(5, 5, 5)));
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area);
            if (!drops.isEmpty()) {
                helper.fail("A Meat Hook kill should drop nothing, found " + drops.size() + " item entities");
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
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 2)));
        for (CarcassSavedData.Carcass carcass : data.all()) {
            UUID rootId = carcass.bones.get(carcass.rootBone);
            SubLevel root = rootId == null ? null : container.getSubLevel(rootId);
            if (root instanceof ServerSubLevel serverRoot && !serverRoot.isRemoved()
                    && serverRoot.logicalPose().position().distance(origin.x, origin.y, origin.z) < 4.0) {
                nearby.add(carcass);
            }
        }
        if (nearby.size() != 1) {
            helper.fail("Expected exactly 1 carcass near this test, found " + nearby.size());
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
