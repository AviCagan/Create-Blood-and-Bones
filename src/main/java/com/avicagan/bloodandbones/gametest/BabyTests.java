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
            Vector3f expected = shape.extension(bone.name()).mul(grown.scale()).add(bone.boxSize()).mul(f);
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

    /** The rabbit draws its baby by hand, not through AgeableListModel; its shape is relative to the grown rabbit's 0.6. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyRabbitCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.RABBIT);
    }

    /**
     * The baby rabbit's body and head start where the game draws them. RabbitModel draws a baby's body at
     * 0.4 * (p + (0, 36, 0)) and its head at 0.5667 * (p + (0, 22, 2)) model pixels under the renderer's
     * 1.501 block lift; the assembler puts a bone's pivot at feet + 1.501 * scale - offset.y / 16.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void babyRabbitSitsWhereTheGameDrawsIt(GameTestHelper helper) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.withDefaultNamespace("rabbit");
        Rig grown = RigManager.forEntity(id).orElseThrow();
        Rig baby = RigManager.forEntity(id, true).orElseThrow();
        String[] bones = {"body", "head"};
        float[] scales = {0.4F, 0.56666666F};
        float[] shifts = {36.0F, 22.0F};
        for (int i = 0; i < bones.length; i++) {
            // the grown rig keeps the model's pixels times the grown rabbit's 0.6
            double p = grown.bone(bones[i]).orElseThrow().offset().y / 0.6;
            double drawn = 1.501 - scales[i] * (p + shifts[i]) / 16.0;
            double built = 1.501 * baby.scale() - baby.bone(bones[i]).orElseThrow().offset().y / 16.0;
            if (Math.abs(drawn - built) > 0.02) {
                helper.fail("The baby rabbit's " + bones[i] + " starts " + built + " above its feet; the game draws it at " + drawn);
            }
        }
        helper.succeed();
    }

    /** Foals: the head and body shrink as usual, but the game draws them on their own long legs. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void foalCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.HORSE);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyDonkeyCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.DONKEY);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyMuleCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.MULE);
    }

    /**
     * A foal's legs are its baby legs: 22 px drawn at half size, the top 5.5 px hidden in the body, so what
     * shows (and the box) is three quarters of a grown horse's leg, though the body is half the size.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void foalStandsOnLongLegs(GameTestHelper helper) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.withDefaultNamespace("horse");
        Rig grown = RigManager.forEntity(id).orElseThrow();
        Rig foal = RigManager.forEntity(id, true).orElseThrow();
        Bone grownLeg = grown.bone("right_hind_leg").orElseThrow();
        Bone foalLeg = foal.bone("right_hind_leg").orElseThrow();
        if (!foalLeg.part().equals("right_hind_baby_leg")) {
            helper.fail("A foal's leg should be drawn with the baby leg part, got " + foalLeg.part());
        }
        float ratio = foalLeg.boxSize().y / grownLeg.boxSize().y;
        if (Math.abs(ratio - 0.75F) > 0.03F) {
            helper.fail("A foal's leg should be three quarters as long as a grown horse's, got " + ratio + " of it");
        }
        if (foal.bone("body").orElseThrow().boxSize().y > 0.6F * grown.bone("body").orElseThrow().boxSize().y) {
            helper.fail("A foal's body should be about half a grown horse's");
        }
        helper.succeed();
    }

    /** A kind with no baby shape (a llama cria, drawn squashed unevenly) still dies as it always did. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void criaDiesAsUsual(GameTestHelper helper) {
        Mob cria = helper.spawn(EntityType.LLAMA, new BlockPos(5, 2, 5));
        cria.setBaby(true);
        if (CarcassAssembler.assemble(cria, null) != null) {
            helper.fail("A cria has no baby rig yet and should not become a carcass");
        }
        cria.discard();
        helper.succeed();
    }
}
