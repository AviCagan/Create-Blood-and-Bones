package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.rig.BabyShape;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;

/** Babies become carcasses shaped as the game draws them: a calf's head is its own size on a half-size body. */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class BabyTests {
    private static final int SETTLE_TICKS = 120;

    private static void babyTest(GameTestHelper helper, EntityType<? extends Mob> type) {
        ServerLevel level = helper.getLevel();
        Mob mob = helper.spawn(type, new BlockPos(5, 2, 5));
        mob.setBaby(true);
        if (!mob.isBaby()) {
            helper.fail(type + " would not be a baby");
            return;
        }
        Vec3 pos = mob.position();
        double ceiling = Math.max(1.6, mob.getBbHeight() + 0.6);
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(mob, null);
        mob.discard();
        if (carcass == null) {
            helper.fail("No carcass for a baby " + type);
            return;
        }
        Rig grown = RigManager.forEntity(carcass.entity).orElseThrow();
        Rig small = RigManager.forCarcass(carcass).orElseThrow();
        BabyShape shape = grown.baby().orElseThrow();
        if (!carcass.baby || small == grown || small.bones().size() != grown.bones().size() || small.weight() >= grown.weight()) {
            helper.fail("A baby " + type + " should be built from a smaller baby rig");
            return;
        }
        for (Bone bone : grown.bones()) {
            Bone baby = small.bone(bone.name()).orElseThrow();
            float f = shape.isHead(bone.name()) ? shape.headScale() : shape.bodyScale();
            Vector3f expected = bone.boxSize().mul(f);
            if (baby.boxSize().distance(expected) > 1.0E-3F) {
                helper.fail("Baby " + type + " bone " + bone.name() + " is " + baby.boxSize() + ", expected " + expected);
                return;
            }
        }
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            for (Map.Entry<String, UUID> bone : carcass.bones.entrySet()) {
                if (!(SubLevelContainer.getContainer(level).getSubLevel(bone.getValue()) instanceof ServerSubLevel body)) {
                    if (carcass.resting) {
                        continue;
                    }
                    helper.fail("Bone " + bone.getKey() + " of the baby " + type + " has no body");
                    return;
                }
                Vector3d p = body.logicalPose().position();
                if (p.distance(pos.x, pos.y, pos.z) > 3.0) {
                    helper.fail("Bone " + bone.getKey() + " of the baby " + type + " ended up " + p.distance(pos.x, pos.y, pos.z) + " blocks away");
                }
                if (p.y < pos.y - 0.2) {
                    helper.fail("Bone " + bone.getKey() + " of the baby " + type + " sank into the floor to " + p);
                }
                if (p.y > pos.y + ceiling) {
                    helper.fail("Bone " + bone.getKey() + " of the baby " + type + " is floating at " + p);
                }
                if (bone.getKey().equals(carcass.rootBone)
                        && (!(level.getBlockEntity(body.getPlot().getCenterBlock()) instanceof CarcassPartBlockEntity cell) || !cell.baby())) {
                    helper.fail("The baby " + type + "'s cells do not know it is a baby");
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyCowCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.COW);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyPigCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.PIG);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyChickenCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.CHICKEN);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyWolfCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.WOLF);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyPandaCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.PANDA);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyZombieCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.ZOMBIE);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyVillagerCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.VILLAGER);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyCamelCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.CAMEL);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyTurtleCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.TURTLE);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babySnifferCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.SNIFFER);
    }

    /** A kind with no baby shape (a horse foal) still dies as it always did. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void foalDiesAsUsual(GameTestHelper helper) {
        Mob foal = helper.spawn(EntityType.HORSE, new BlockPos(5, 2, 5));
        foal.setBaby(true);
        if (CarcassAssembler.assemble(foal, null) != null) {
            helper.fail("A foal has no baby rig yet and should not become a carcass");
        }
        foal.discard();
        helper.succeed();
    }
}
