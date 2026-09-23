package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.BBAttachments;
import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.Surgery;
import com.avicagan.bloodandbones.body.SurgerySeatEntity;
import com.avicagan.bloodandbones.body.SurgeryTableBlock;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The body: limbs off and on at the Surgery Table, what that does, and that it is kept. */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class BodyTests {
    private static SurgeryTableBlockEntity table(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, BBBlocks.SURGERY_TABLE.getDefaultState());
        return (SurgeryTableBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at));
    }

    private static int count(Player player, net.minecraft.world.item.Item item) {
        return player.getInventory().countItem(item);
    }

    /** A body with parts gone and fitted is written and read back the same, on its own and on a player. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bodySurvivesSaving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Body body = new Body();
        body.lose(BodyPart.LEFT_ARM);
        body.fit(BodyPart.RIGHT_LEG, new ItemStack(BBItems.PEG_LEG.get()));
        Tag tag = Body.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), body).getOrThrow();
        Body back = Body.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
        if (!back.equals(body) || back.state(BodyPart.RIGHT_LEG) != Body.State.IMPLANT || back.state(BodyPart.LEFT_ARM) != Body.State.MISSING
                || back.state(BodyPart.LEFT_LEG) != Body.State.NATURAL) {
            helper.fail("Body came back as " + back + ", was " + body);
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(BBAttachments.BODY, body);
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        Player loaded = helper.makeMockPlayer(GameType.SURVIVAL);
        loaded.load(saved);
        if (!BodyEffects.body(loaded).equals(body)) {
            helper.fail("A player's body was not saved with them: " + BodyEffects.body(loaded));
            return;
        }
        helper.succeed();
    }

    /** Lie on the table: a Cleaver takes an arm off, a Hook Hand goes on, comes off, and the arm goes back. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void surgeryTakesOffAndFits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(3, 2, 3);
        SurgeryTableBlockEntity table = table(helper, at);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.NONE) {
            helper.fail("An empty table should do nothing to a whole arm");
            return;
        }
        table.put(new ItemStack(BBItems.CLEAVER.get()));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.TAKE_OFF
                || BodyEffects.body(player).state(BodyPart.RIGHT_ARM) != Body.State.MISSING || count(player, BBItems.SEVERED_ARM.get()) != 1
                || !table.item().is(BBItems.CLEAVER.get())) {
            helper.fail("A Cleaver on the table should take the arm off and hand it over, and stay on the table");
            return;
        }
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.NONE) {
            helper.fail("A Cleaver cannot do anything to an arm that is gone");
            return;
        }
        table.take();
        table.put(new ItemStack(BBItems.PEG_LEG.get()));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.NONE) {
            helper.fail("A Peg Leg does not fit an arm");
            return;
        }
        table.take();
        table.put(new ItemStack(BBItems.HOOK_HAND.get()));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.FIT
                || BodyEffects.body(player).state(BodyPart.RIGHT_ARM) != Body.State.IMPLANT || !table.item().isEmpty()) {
            helper.fail("The Hook Hand should be fitted, and leave the table");
            return;
        }
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.UNCLIP
                || BodyEffects.body(player).state(BodyPart.RIGHT_ARM) != Body.State.MISSING || count(player, BBItems.HOOK_HAND.get()) != 1) {
            helper.fail("The Hook Hand should unclip into the inventory, leaving the arm missing");
            return;
        }
        ItemStack arm = ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(BBItems.SEVERED_ARM.get())) {
                arm = player.getInventory().getItem(slot);
            }
        }
        table.put(arm.split(1));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.REATTACH || !BodyEffects.body(player).whole()) {
            helper.fail("The severed arm should go back on as flesh");
            return;
        }
        helper.succeed();
    }

    /** A missing leg slows you and weakens the jump; a peg leg nearly makes up for it. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void legsSetTheWalk(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double walk = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double jump = player.getAttributeValue(Attributes.JUMP_STRENGTH);
        Body body = BodyEffects.body(player);
        body.lose(BodyPart.LEFT_LEG);
        BodyEffects.changed(player);
        double oneLeg = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (Math.abs(oneLeg - walk * 0.6) > 1.0E-4 || player.getAttributeValue(Attributes.JUMP_STRENGTH) >= jump) {
            helper.fail("One leg should walk at 60% and jump lower: " + oneLeg + " of " + walk);
            return;
        }
        body.fit(BodyPart.LEFT_LEG, new ItemStack(BBItems.PEG_LEG.get()));
        BodyEffects.changed(player);
        if (Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - walk * 0.95) > 1.0E-4) {
            helper.fail("A peg leg should walk at 90%, so 95% on the two: " + player.getAttributeValue(Attributes.MOVEMENT_SPEED));
            return;
        }
        body.restore(BodyPart.LEFT_LEG);
        BodyEffects.changed(player);
        if (Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - walk) > 1.0E-6 || Math.abs(player.getAttributeValue(Attributes.JUMP_STRENGTH) - jump) > 1.0E-6) {
            helper.fail("Whole again, the walk and jump should be as they were");
            return;
        }
        helper.succeed();
    }

    /** A hand with no arm uses nothing; a Hook Hand works, breaking blocks slower. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void armsSetTheHands(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BodyPart main = BodyEffects.armFor(player, InteractionHand.MAIN_HAND);
        Body body = BodyEffects.body(player);
        body.lose(main);
        if (BodyEffects.handWorks(player, InteractionHand.MAIN_HAND) || !BodyEffects.handWorks(player, InteractionHand.OFF_HAND)
                || BodyEffects.work(player) != BodyEffects.MISSING_WORK) {
            helper.fail("With the main arm gone, the main hand should not work, the other should, and blocks break slowly");
            return;
        }
        body.fit(main, new ItemStack(BBItems.HOOK_HAND.get()));
        if (!BodyEffects.handWorks(player, InteractionHand.MAIN_HAND) || Math.abs(BodyEffects.work(player) - 0.7F) > 1.0E-6F) {
            helper.fail("A Hook Hand should work the hand and break blocks at 70%");
            return;
        }
        helper.succeed();
    }

    /** Lying down puts you on a seat on the table; nobody else fits; the seat goes when you get up. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void lyingOnTheTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(3, 2, 3);
        table(helper, at);
        BlockPos abs = helper.absolutePos(at);
        net.minecraft.world.entity.animal.Pig patient = helper.spawn(net.minecraft.world.entity.EntityType.PIG, new BlockPos(5, 2, 5));
        if (!SurgeryTableBlock.lieDown(level, abs, patient) || !(patient.getVehicle() instanceof SurgerySeatEntity seat) || !seat.blockPosition().equals(abs)) {
            helper.fail("The patient should be lying on the table");
            return;
        }
        net.minecraft.world.entity.animal.Pig other = helper.spawn(net.minecraft.world.entity.EntityType.PIG, new BlockPos(6, 2, 6));
        if (SurgeryTableBlock.lieDown(level, abs, other)) {
            helper.fail("A second patient should not fit on the table");
            return;
        }
        patient.stopRiding();
        helper.runAfterDelay(5, () -> {
            if (!level.getEntitiesOfClass(SurgerySeatEntity.class, new net.minecraft.world.phys.AABB(abs)).isEmpty()) {
                helper.fail("The seat should go once nobody is on it");
                return;
            }
            helper.succeed();
        });
    }

    /** The screen's buttons only work for someone lying on that table, not beside it or on another. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void surgeryOnlyFromTheTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        SurgeryTableBlockEntity other = table(helper, new BlockPos(6, 2, 6));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        if (Surgery.lyingOn(player, table.getBlockPos())) {
            helper.fail("Someone standing by is not on the table");
            return;
        }
        SurgeryTableBlock.lieDown(level, other.getBlockPos(), player);
        if (Surgery.lyingOn(player, table.getBlockPos()) || !Surgery.lyingOn(player, other.getBlockPos())) {
            helper.fail("Someone on one table is not on another");
            return;
        }
        player.stopRiding();
        helper.succeed();
    }
}
