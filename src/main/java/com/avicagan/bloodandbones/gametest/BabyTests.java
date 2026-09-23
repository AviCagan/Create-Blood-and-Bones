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
            Vector3f f = BabyShape.alongPart(shape.scaleOf(bone.name()), bone.rotation());
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

    /**
     * The smallest slime and magma cube leave a carcass a quarter the size of a big one's, starting where the
     * game draws them (the cube's middle a quarter of a block up); a middle-sized one still splits as usual.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void smallestSlimesLeaveCarcasses(GameTestHelper helper) {
        for (EntityType<? extends net.minecraft.world.entity.monster.Slime> type : java.util.List.of(EntityType.SLIME, EntityType.MAGMA_CUBE)) {
            net.minecraft.world.entity.monster.Slime slime = helper.spawn(type, new BlockPos(3, 2, 3));
            slime.setSize(1, true);
            Vec3 pos = slime.position();
            CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(slime, null);
            slime.discard();
            if (carcass == null || !carcass.baby) {
                helper.fail("The smallest " + type + " should leave a small carcass");
                return;
            }
            Rig grown = RigManager.forEntity(carcass.entity).orElseThrow();
            Rig small = RigManager.forCarcass(carcass).orElseThrow();
            if (small.weight() > grown.weight() / 8.0F) {
                helper.fail("A size 1 " + type + " should weigh far less than a size 4 one: " + small.weight() + " vs " + grown.weight());
                return;
            }
            UUID torso = carcass.bones.get(small.root().name());
            if (!(SubLevelContainer.getContainer(helper.getLevel()).getSubLevel(torso) instanceof ServerSubLevel body)) {
                helper.fail("The small " + type + " carcass has no body");
                return;
            }
            double up = body.logicalPose().position().y - pos.y;
            if (Math.abs(up - 0.25) > 0.1) {
                helper.fail("The small " + type + " starts " + up + " blocks above its feet, expected about 0.25");
                return;
            }
            net.minecraft.world.entity.monster.Slime middle = helper.spawn(type, new BlockPos(7, 2, 7));
            middle.setSize(2, true);
            if (CarcassAssembler.assemble(middle, null) != null) {
                helper.fail("A size 2 " + type + " should split and die as usual");
                return;
            }
            middle.discard();
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
     * A foal stands on long legs: the game draws baby legs 22 px long at half size with the top 5.5 px
     * hidden in the body, so what shows is three quarters of a grown horse's leg and half as wide, though
     * the body is half the size.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void foalStandsOnLongLegs(GameTestHelper helper) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.withDefaultNamespace("horse");
        Rig grown = RigManager.forEntity(id).orElseThrow();
        Rig foal = RigManager.forEntity(id, true).orElseThrow();
        Bone grownLeg = grown.bone("right_hind_leg").orElseThrow();
        Bone foalLeg = foal.bone("right_hind_leg").orElseThrow();
        if (Math.abs(foalLeg.boxSize().x / grownLeg.boxSize().x - 0.5F) > 0.02F) {
            helper.fail("A foal's leg should be half as wide as a grown horse's");
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

    /** Kinds drawn with another kind's model take its baby shape: a baby zombified piglin, a zombie foal. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyZombifiedPiglinCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.ZOMBIFIED_PIGLIN);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void zombieFoalCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.ZOMBIE_HORSE);
    }

    /** A baby llama's rig keeps its squashed sizes through saving and reading back; an even one stays one number. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void babyRigSurvivesSaving(GameTestHelper helper) {
        Rig baby = RigManager.forEntity(net.minecraft.resources.ResourceLocation.withDefaultNamespace("llama"), true).orElseThrow();
        com.google.gson.JsonElement json = Rig.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, baby).getOrThrow();
        Rig back = Rig.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        for (Bone bone : baby.bones()) {
            if (back.bone(bone.name()).orElseThrow().scale().distance(bone.scale()) > 1.0E-4F) {
                helper.fail("Bone " + bone.name() + " came back at " + back.bone(bone.name()).orElseThrow().scale() + ", was " + bone.scale());
            }
        }
        Rig grown = RigManager.forEntity(net.minecraft.resources.ResourceLocation.withDefaultNamespace("cow")).orElseThrow();
        com.google.gson.JsonElement cow = Rig.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, grown).getOrThrow();
        for (com.google.gson.JsonElement bone : cow.getAsJsonObject().getAsJsonArray("bones")) {
            com.google.gson.JsonElement scale = bone.getAsJsonObject().get("scale");
            if (scale != null && !scale.isJsonPrimitive()) {
                helper.fail("An even scale should be written as one number, got " + scale);
            }
        }
        helper.succeed();
    }

    /** A baby llama: the game squashes its head, body and legs each by its own amount along each axis. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyLlamaCarcass(GameTestHelper helper) {
        babyTest(helper, EntityType.LLAMA);
    }

    /**
     * The baby llama's parts, against LlamaModel's own numbers: the head 8x18x10 at (0.714, 0.649, 0.794);
     * the body, turned a quarter about x so its own y runs along the model's z, at (0.625, 0.455, 0.455).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void babyLlamaSquashedAsDrawn(GameTestHelper helper) {
        Rig baby = RigManager.forEntity(net.minecraft.resources.ResourceLocation.withDefaultNamespace("llama"), true).orElseThrow();
        Vector3f head = baby.bone("head").orElseThrow().boxSize();
        Vector3f leg = baby.bone("right_front_leg").orElseThrow().boxSize();
        if (head.distance(8 * 0.71428573F, 18 * 0.64935064F, 10 * 0.7936508F) > 0.01F) {
            helper.fail("The baby llama's head box is " + head);
        }
        if (leg.distance(4 * 0.45454544F, 14 * 0.41322312F, 4 * 0.45454544F) > 0.01F) {
            helper.fail("The baby llama's leg box is " + leg);
        }
        Vector3f bodyScale = baby.bone("body").orElseThrow().scale();
        if (bodyScale.distance(0.625F, 0.45454544F, 0.45454544F) > 0.01F) {
            helper.fail("The baby llama's body should be drawn at (0.625, 0.455, 0.455) along its own axes, got " + bodyScale);
        }
        helper.succeed();
    }
}
