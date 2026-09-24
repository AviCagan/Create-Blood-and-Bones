package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.Surgery;
import com.avicagan.bloodandbones.body.SurgeryTableBlock;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.body.TableAttachment;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.MinionStats;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * The brief's amputation ritual: a player's flesh comes off only with a surgeon minion (a villager's or a pillager's
 * head, and a hand) awake at the table; its cut leaves a ragged stump that costs a bucket of blood to fit; and
 * nothing about fitting ever needs a surgeon, so the safety floor is always in reach.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class SurgeonTests {
    private static PieceRef ref(String entity, String bone) {
        return new PieceRef(ResourceLocation.withDefaultNamespace(entity), bone, ResourceLocation.withDefaultNamespace("textures/entity/" + entity + "/" + entity + ".png"),
                List.of(), 1.0F, false, Map.of(), false);
    }

    private static SurgeryTableBlockEntity table(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.SURGICAL));
        return (SurgeryTableBlockEntity) helper.getBlockEntity(at);
    }

    private static int count(Player player, net.minecraft.world.item.Item item) {
        return player.getInventory().countItem(item);
    }

    /** A villager's or a pillager's head, with an arm, can be a surgeon; a cow's cannot; a villager's head with no arm cannot either. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void villagerAndPillagerHeadsOfferSurgeon(GameTestHelper helper) {
        ResourceLocation surgeon = BloodAndBones.asResource("surgeon");
        MinionBuild armed = MinionBuild.of(ref("zombie", "body")).with("right_arm", ref("zombie", "right_arm"));
        MinionStats villager = MinionStats.of(PartsData.SERVER, armed.with("head", ref("villager", "head")));
        MinionStats pillager = MinionStats.of(PartsData.SERVER, armed.with("head", ref("pillager", "head")));
        MinionStats cow = MinionStats.of(PartsData.SERVER, armed.with("head", ref("cow", "head")));
        MinionStats armless = MinionStats.of(PartsData.SERVER, MinionBuild.of(ref("cow", "body")).with("head", ref("villager", "head")));
        if (!villager.jobs().get(0).equals(surgeon) || !pillager.jobs().contains(surgeon) || cow.jobs().contains(surgeon)) {
            helper.fail("Villager and pillager heads should offer surgeon, a cow's not: " + villager.jobs() + " " + pillager.jobs() + " " + cow.jobs());
            return;
        }
        if (armless.jobs().contains(surgeon) || armless.jobs().contains(BloodAndBones.asResource("farmer"))) {
            helper.fail("With no hand to work with, a villager's head should not offer surgeon or farmer: " + armless.jobs());
            return;
        }
        helper.succeed();
    }

    /**
     * A Cleaver on the table does nothing to a player's arm with no surgeon there, nor with one out of blood, nor
     * with one across the room; nor does a prosthetic swapped straight in for it. With a surgeon awake beside the
     * table, the arm comes off and leaves a ragged stump.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void amputationNeedsSurgeon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        table.put(new ItemStack(BBItems.HOOK_HAND.get()));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.NONE || BodyEffects.body(player).state(BodyPart.RIGHT_ARM) != Body.State.NATURAL
                || !table.item().is(BBItems.HOOK_HAND.get())) {
            helper.fail("With no surgeon a Hook Hand should not be swapped in for the arm: that cuts flesh too");
            return;
        }
        table.take();
        table.put(new ItemStack(BBItems.CLEAVER.get()));
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.NONE || BodyEffects.body(player).state(BodyPart.LEFT_ARM) != Body.State.NATURAL) {
            helper.fail("With no surgeon the arm should stay");
            return;
        }
        MinionEntity surgeon = MinionTests.surgeon(helper, new BlockPos(4, 2, 4));
        surgeon.powerDown();
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.NONE) {
            helper.fail("A surgeon out of blood cannot operate");
            return;
        }
        surgeon.feed(500.0F);
        BlockPos far = helper.absolutePos(new BlockPos(10, 2, 10));
        surgeon.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.NONE) {
            helper.fail("A surgeon across the room cannot operate");
            return;
        }
        BlockPos near = helper.absolutePos(new BlockPos(4, 2, 4));
        surgeon.moveTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        Body body;
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.TAKE_OFF
                || (body = BodyEffects.body(player)).state(BodyPart.LEFT_ARM) != Body.State.MISSING || !body.ragged(BodyPart.LEFT_ARM)
                || count(player, BBItems.SEVERED_ARM.get()) != 1) {
            helper.fail("With a surgeon by the table the arm should come off, leaving a ragged stump");
            return;
        }
        helper.succeed();
    }

    /**
     * Fitting anything but a crude prosthetic into a ragged stump takes a bucket of blood as well: without one nothing
     * happens; with one it goes on and the bucket comes back empty; the stump is dressed, so taking the implant out and
     * fitting again is free. Putting the limb itself back into a ragged stump costs the same.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void raggedStumpCostsBlood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BodyEffects.body(player).lose(BodyPart.RIGHT_ARM, true);
        table.put(new ItemStack(BBItems.FLESH_ARM.get()));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.NONE || !table.item().is(BBItems.FLESH_ARM.get())) {
            helper.fail("With no blood a Flesh Arm should not go into a ragged stump");
            return;
        }
        player.getInventory().add(new ItemStack(BBFluids.BLOOD.getBucket().get()));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.FIT || count(player, BBFluids.BLOOD.getBucket().get()) != 0
                || count(player, Items.BUCKET) != 1) {
            helper.fail("With a bucket of blood it should fit, the bucket coming back empty");
            return;
        }
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.UNCLIP || BodyEffects.body(player).ragged(BodyPart.RIGHT_ARM)) {
            helper.fail("Taken out again, the stump should be dressed, not ragged");
            return;
        }
        ItemStack arm = player.getInventory().items.stream().filter(s -> s.is(BBItems.FLESH_ARM.get())).findFirst().orElseThrow();
        table.put(arm.split(1));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.FIT) {
            helper.fail("A dressed stump takes the Flesh Arm with no blood");
            return;
        }
        BodyEffects.body(player).lose(BodyPart.LEFT_ARM, true);
        table.put(BBItems.partItem(BodyPart.Kind.ARM).of(player));
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.NONE || BodyEffects.body(player).state(BodyPart.LEFT_ARM) != Body.State.MISSING) {
            helper.fail("With no blood the arm should not go back into a ragged stump");
            return;
        }
        player.getInventory().add(new ItemStack(BBFluids.BLOOD.getBucket().get()));
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.REATTACH || count(player, BBFluids.BLOOD.getBucket().get()) != 0
                || BodyEffects.body(player).state(BodyPart.LEFT_ARM) != Body.State.NATURAL) {
            helper.fail("With a bucket of blood the arm should go back on, the bucket used");
            return;
        }
        helper.succeed();
    }

    /** The blood for a ragged stump can come from a worn Fluid Backtank too. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void raggedStumpPaidFromBacktank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack tank = new ItemStack(BBItems.backtank(com.avicagan.bloodandbones.backtank.BacktankTier.COPPER));
        com.avicagan.bloodandbones.backtank.FluidBacktankItem.setFluid(tank, new net.neoforged.neoforge.fluids.FluidStack(BBFluids.blood(), 1500));
        player.setItemSlot(EquipmentSlot.CHEST, tank);
        BodyEffects.body(player).lose(BodyPart.LEFT_LEG, true);
        table.put(new ItemStack(BBItems.SINEW_LEG.get()));
        if (Surgery.operate(level, player, table, BodyPart.LEFT_LEG) != Surgery.Action.FIT) {
            helper.fail("The backtank's blood should pay for the ragged stump");
            return;
        }
        int left = com.avicagan.bloodandbones.backtank.FluidBacktankItem.fluid(player.getItemBySlot(EquipmentSlot.CHEST)).getAmount();
        if (left != 500) {
            helper.fail("A bucket's worth should come out of the backtank, leaving 500 mB: " + left);
            return;
        }
        helper.succeed();
    }

    /**
     * The safety floor: a crude prosthetic goes into a clean empty slot with no surgeon and no blood, and into a
     * surgeon's ragged stump with no blood too (dressing it); and swapping an implant straight in for flesh (with the
     * surgeon) leaves no stump at all.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void safetyFloorNeverNeedsSurgeon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BodyEffects.body(player).lose(BodyPart.LEFT_LEG);
        table.put(new ItemStack(BBItems.PEG_LEG.get()));
        if (Surgery.operate(level, player, table, BodyPart.LEFT_LEG) != Surgery.Action.FIT) {
            helper.fail("A Peg Leg should always go into a clean empty slot, surgeon or not");
            return;
        }
        BodyEffects.body(player).lose(BodyPart.LEFT_ARM, true);
        table.put(new ItemStack(BBItems.HOOK_HAND.get()));
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.FIT || BodyEffects.body(player).ragged(BodyPart.LEFT_ARM)) {
            helper.fail("A Hook Hand should go into a ragged stump with no blood, dressing it");
            return;
        }
        MinionTests.surgeon(helper, new BlockPos(4, 2, 4));
        table.put(new ItemStack(BBItems.HOOK_HAND.get()));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.REPLACE || BodyEffects.body(player).ragged(BodyPart.RIGHT_ARM)
                || BodyEffects.body(player).state(BodyPart.RIGHT_ARM) != Body.State.IMPLANT) {
            helper.fail("A Hook Hand swapped straight in for the arm should leave no ragged stump");
            return;
        }
        helper.succeed();
    }

    /** A surgeon set down away from its table walks back to it and keeps there. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void surgeonKeepsToItsTable(GameTestHelper helper) {
        BlockPos tableAt = new BlockPos(2, 2, 2);
        table(helper, tableAt);
        MinionEntity surgeon = MinionTests.surgeon(helper, new BlockPos(9, 2, 9));
        surgeon.setNoAi(false);
        surgeon.setHome(helper.absolutePos(tableAt));
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(tableAt));
        helper.succeedWhen(() -> helper.assertTrue(surgeon.distanceToSqr(centre) < Surgery.SURGEON_REACH * Surgery.SURGEON_REACH
                && Surgery.surgeonAt(helper.getLevel(), helper.absolutePos(tableAt)) == surgeon, "the surgeon has not got back to its table yet"));
    }

    /**
     * A surgeon folded up at one table and set down beside another, far off, takes the table beside it: it works from
     * where it is set down, and its old table's far-off chunk is never looked at.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void unfoldedSurgeonTakesTheTableBesideIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos first = new BlockPos(1, 2, 1);
        BlockPos second = new BlockPos(9, 2, 9);
        table(helper, first);
        table(helper, second);
        MinionEntity surgeon = MinionTests.surgeon(helper, new BlockPos(2, 2, 2));
        surgeon.setNoAi(false);
        surgeon.setHome(helper.absolutePos(first));
        surgeon.powerDown();
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        com.avicagan.bloodandbones.minion.DormantMinionItem.fold(surgeon, maker);
        ItemStack folded = maker.getInventory().items.stream().filter(st -> st.is(BBItems.DORMANT_MINION.get())).findFirst().orElseThrow();
        BlockPos floor = helper.absolutePos(new BlockPos(8, 1, 7));
        folded.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(level, maker, net.minecraft.world.InteractionHand.MAIN_HAND, folded,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(floor), net.minecraft.core.Direction.UP, floor, false)));
        MinionEntity back = level.getEntitiesOfClass(MinionEntity.class, new net.minecraft.world.phys.AABB(floor).inflate(2)).stream().findFirst().orElse(null);
        if (back == null) {
            helper.fail("It should unfold");
            return;
        }
        back.feed(500.0F);
        helper.succeedWhen(() -> helper.assertTrue(back.home().equals(helper.absolutePos(second)),
                "the surgeon has not taken the table beside it yet (home " + back.home() + ")"));
    }
}
