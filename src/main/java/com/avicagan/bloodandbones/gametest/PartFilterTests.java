package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.SurgeryTableBlock;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.body.SurgicalRig;
import com.avicagan.bloodandbones.body.TableAttachment;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBItemAttributes;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.UUID;

/**
 * Docs/BRIEF-AUDIT.md package 10: "a filtered machine pulls one specific part out of a mixed line and passes the rest
 * through untouched", and "every station that removes parts carries a filter". The filter is asked about each part a
 * station could take.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class PartFilterTests {
    /** Create's Attribute Filter set to one attribute, as a whitelist. */
    static ItemStack attributeFilter(ItemAttribute attribute) {
        ItemStack filter = new ItemStack(AllItems.ATTRIBUTE_FILTER.get());
        filter.set(AllDataComponents.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES, List.of(new ItemAttribute.ItemAttributeEntry(attribute, false)));
        return filter;
    }

    private static CarcassSavedData.Carcass carcass(GameTestHelper helper, EntityType<? extends Mob> type, BlockPos at) {
        Mob mob = helper.spawn(type, at);
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(mob, null);
        mob.discard();
        if (carcass == null) {
            helper.fail("Carcass assembly returned null");
        }
        return carcass;
    }

    /**
     * The machines' filter is asked about each part: set to "is a carcass hind leg" it takes a cow's hind legs and
     * nothing else of it, and a pig's and a rabbit's too (the slot comes from the rules that are data, not the mob); "is a
     * carcass head" only heads; a cow's egg every part of a cow; a list as a whitelist or a blacklist. The Attribute
     * Filter offers the finer attribute for limbs only.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void partFilterAsksAboutEachPart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(1, 2, 1), BBBlocks.GUILLOTINE.getDefaultState());
        CarcassMachineBlockEntity machine = (CarcassMachineBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(1, 2, 1)));
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, new BlockPos(4, 2, 4));
        CarcassSavedData.Carcass pig = carcass(helper, EntityType.PIG, new BlockPos(7, 2, 4));
        CarcassSavedData.Carcass rabbit = carcass(helper, EntityType.RABBIT, new BlockPos(4, 2, 8));
        if (cow == null || pig == null || rabbit == null) {
            return;
        }
        machine.filtering.setFilter(attributeFilter(new BBItemAttributes.PieceSlot("leg.hind")));
        helper.assertTrue(machine.accepts(cow, "left_hind_leg") && machine.accepts(cow, "right_hind_leg"), "a hind-leg filter should take a cow's hind legs");
        helper.assertTrue(!machine.accepts(cow, "left_front_leg") && !machine.accepts(cow, "head") && !machine.accepts(cow, "body"),
                "and none of the rest of it");
        helper.assertTrue(machine.accepts(pig, "left_hind_leg") && !machine.accepts(pig, "right_front_leg"), "a pig's hind legs too, not its front ones");
        String haunch = rabbit.joints.stream().map(j -> j.child()).filter(b -> b.contains("haunch")).findFirst().orElse("left_haunch");
        helper.assertTrue(machine.accepts(rabbit, haunch), "a rabbit's haunches are its hind legs: " + haunch);
        machine.filtering.setFilter(attributeFilter(new BBItemAttributes.PiecePart("head")));
        helper.assertTrue(machine.accepts(cow, "head") && !machine.accepts(cow, "left_hind_leg"), "a head filter should take only the head");
        machine.filtering.setFilter(new ItemStack(Items.COW_SPAWN_EGG));
        helper.assertTrue(machine.accepts(cow, "head") && machine.accepts(cow, "left_hind_leg") && !machine.accepts(pig, "head"),
                "a cow's egg should take any part of a cow and nothing of a pig");
        ItemStack list = new ItemStack(AllItems.FILTER.get());
        list.set(AllDataComponents.FILTER_ITEMS, ItemContainerContents.fromItems(List.of(new ItemStack(Items.PIG_SPAWN_EGG))));
        list.set(AllDataComponents.FILTER_ITEMS_BLACKLIST, true);
        machine.filtering.setFilter(list);
        helper.assertTrue(machine.accepts(cow, "head") && !machine.accepts(pig, "head"), "a blacklist of pigs should take the cow and pass the pig");
        // what the Attribute Filter offers: the limb's slot for a leg, nothing finer for a head
        List<ItemAttribute> leg = ItemAttribute.getAllAttributes(CarcassPieceItem.of(cow, "left_hind_leg"), level);
        List<ItemAttribute> head = ItemAttribute.getAllAttributes(CarcassPieceItem.of(cow, "head"), level);
        helper.assertTrue(leg.contains(new BBItemAttributes.PieceSlot("leg.hind")), "a hind leg should offer \"is a carcass hind leg\": " + leg);
        helper.assertTrue(head.stream().noneMatch(a -> a instanceof BBItemAttributes.PieceSlot), "a head should not offer a limb's slot: " + head);
        helper.succeed();
    }

    /**
     * A Guillotine set to "is a carcass hind leg" under a cow takes its two hind legs, one a drop, and then passes the
     * rest over however often its blade falls: the front legs and the head stay on.
     */
    @GameTest(template = "empty", timeoutTicks = 900)
    public static void guillotineTakesOnlyHindLegs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos motor = new BlockPos(5, 2, 5);
        BlockPos at = motor.above();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.setBlock(at.offset(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        helper.setBlock(at, BBBlocks.GUILLOTINE.getDefaultState());
        if (level.getBlockEntity(helper.absolutePos(motor)) instanceof CreativeMotorBlockEntity creative) {
            creative.generatedSpeed.setValue(64);
        }
        CarcassMachineBlockEntity guillotine = (CarcassMachineBlockEntity) level.getBlockEntity(helper.absolutePos(at));
        guillotine.filtering.setFilter(attributeFilter(new BBItemAttributes.PieceSlot("leg.hind")));
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, at.above());
        if (cow == null) {
            return;
        }
        UUID id = cow.id;
        MachineTests.clock(helper, at, 12);
        long[] hindOffAt = {-1};
        helper.succeedWhen(() -> {
            CarcassSavedData.Carcass left = CarcassSavedData.get(level).carcass(id);
            helper.assertTrue(left != null, "the cow's record is gone");
            boolean hind = left.joints.stream().anyMatch(j -> j.child().contains("hind"));
            helper.assertTrue(left.joints.stream().anyMatch(j -> j.child().equals("head")), "the head came off");
            helper.assertTrue(left.joints.stream().filter(j -> j.child().contains("front")).count() == 2, "a front leg came off");
            helper.assertTrue(!hind, "the hind legs are still on");
            if (hindOffAt[0] < 0) {
                hindOffAt[0] = helper.getTick();
            }
            // a few more drops with nothing it may take under the blade
            helper.assertTrue(helper.getTick() - hindOffAt[0] > 100 && guillotine.strokes == 2, "waiting for more drops to pass the rest over ("
                    + guillotine.strokes + " cuts)");
        });
    }

    /**
     * A mixed line past the stations: a Butcher's Table set to "is a carcass head" takes heads from a funnel and turns
     * the legs away (they go on down the line), and chops only a head lying on it; a Surgical Rig set to "is a carcass
     * head" passes a body laid on it over, and takes a head's eyes.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void mixedLineSortsAtTheTables(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, new BlockPos(8, 2, 8));
        CarcassSavedData.Carcass pig = carcass(helper, EntityType.PIG, new BlockPos(8, 2, 3));
        if (cow == null || pig == null) {
            return;
        }
        BlockPos tablePos = new BlockPos(2, 2, 2);
        helper.setBlock(tablePos, BBBlocks.BUTCHER_TABLE.getDefaultState());
        ButcherTableBlockEntity table = (ButcherTableBlockEntity) level.getBlockEntity(helper.absolutePos(tablePos));
        table.filtering.setFilter(attributeFilter(new BBItemAttributes.PiecePart("head")));
        IItemHandler funnel = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, helper.absolutePos(tablePos), Direction.UP);
        List<ItemStack> line = List.of(CarcassPieceItem.of(cow, "left_front_leg"), CarcassPieceItem.of(pig, "head"),
                CarcassPieceItem.of(pig, "right_hind_leg"), CarcassPieceItem.of(cow, "head"));
        int headsTaken = 0;
        int passed = 0;
        for (ItemStack piece : line) {
            ItemStack left = funnel.insertItem(0, piece.copy(), false);
            if (left.isEmpty()) {
                headsTaken++;
                helper.assertTrue(table.chop(level, new ItemStack(BBItems.CLEAVER.get()), FakePlayerFactory.getMinecraft(level)), "the head should be chopped");
            } else {
                passed++;
            }
        }
        helper.assertTrue(headsTaken == 2 && passed == 2, "the table should take the two heads and pass the two legs: " + headsTaken + " / " + passed);
        // the Surgical Rig: a body passed over, a head's eyes taken
        BlockPos rigPos = new BlockPos(2, 2, 6);
        helper.setBlock(rigPos, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.SURGICAL));
        SurgeryTableBlockEntity rig = (SurgeryTableBlockEntity) level.getBlockEntity(helper.absolutePos(rigPos));
        rig.filtering.setFilter(attributeFilter(new BBItemAttributes.PiecePart("head")));
        var surgeon = FakePlayerFactory.getMinecraft(level);
        ItemStack blade = new ItemStack(BBItems.CLEAVER.get());
        rig.put(CarcassPieceItem.of(cow, "body"));
        helper.assertTrue(!SurgicalRig.cut(level, surgeon, rig, blade), "a rig set for heads should pass a body over");
        rig.take();
        rig.put(CarcassPieceItem.of(cow, "head"));
        helper.assertTrue(SurgicalRig.cut(level, surgeon, rig, blade) && SurgicalRig.cut(level, surgeon, rig, blade), "a rig set for heads should take a head's eyes");
        helper.runAfterDelay(2, () -> {
            helper.assertItemEntityCountIs(BBItems.EYE.get(), rigPos.above(), 2.0, 2);
            helper.assertItemEntityCountIs(BBItems.HEART.get(), rigPos.above(), 2.0, 0);
            helper.succeed();
        });
    }
}
