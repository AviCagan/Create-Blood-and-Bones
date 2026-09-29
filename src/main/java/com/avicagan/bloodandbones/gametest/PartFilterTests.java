package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.SurgeryTableBlock;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.body.SurgicalRig;
import com.avicagan.bloodandbones.body.TableAttachment;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
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

    /**
     * A Create filter set in a table's slot is the player's own item: breaking a Butcher's Table or a Surgery Table drops
     * it, as Create's own blocks do, and taking the Surgical Rig off (swapping in the Assembly Frame) hands it back, so it is
     * never left in a slot that is no longer there.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tableFiltersAreNeverLost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos butcherPos = new BlockPos(2, 2, 2);
        BlockPos rigPos = new BlockPos(6, 2, 2);
        BlockPos swapPos = new BlockPos(10, 2, 2);
        helper.setBlock(butcherPos, BBBlocks.BUTCHER_TABLE.getDefaultState());
        ((ButcherTableBlockEntity) level.getBlockEntity(helper.absolutePos(butcherPos))).filtering.setFilter(attributeFilter(new BBItemAttributes.PiecePart("head")));
        for (BlockPos at : List.of(rigPos, swapPos)) {
            helper.setBlock(at, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.SURGICAL));
            ((SurgeryTableBlockEntity) level.getBlockEntity(helper.absolutePos(at))).filtering.setFilter(attributeFilter(new BBItemAttributes.PieceSlot("leg.hind")));
        }
        level.destroyBlock(helper.absolutePos(butcherPos), true);
        level.destroyBlock(helper.absolutePos(rigPos), true);
        // the Assembly Frame fitted in the rig's place: the rig and its filter come back to the player
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack frame = new ItemStack(BBItems.ASSEMBLY_FRAME.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, frame);
        BlockPos swap = helper.absolutePos(swapPos);
        level.getBlockState(swap).useItemOn(frame, level, player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(swap), Direction.UP, swap, false));
        SurgeryTableBlockEntity swapped = (SurgeryTableBlockEntity) level.getBlockEntity(swap);
        helper.assertTrue(level.getBlockState(swap).getValue(SurgeryTableBlock.ATTACHMENT) == TableAttachment.ASSEMBLY, "the Assembly Frame should be fitted");
        helper.assertTrue(player.getInventory().items.stream().anyMatch(stack -> stack.is(AllItems.ATTRIBUTE_FILTER.get())), "the rig's filter should come back to the player");
        helper.assertTrue(swapped.filtering.getFilter().isEmpty(), "and leave the slot empty");
        helper.runAfterDelay(1, () -> {
            helper.assertItemEntityCountIs(AllItems.ATTRIBUTE_FILTER.get(), butcherPos, 1.5, 1);
            helper.assertItemEntityCountIs(AllItems.ATTRIBUTE_FILTER.get(), rigPos, 1.5, 1);
            helper.assertItemEntityCountIs(BBItems.SURGICAL_RIG.get(), rigPos, 1.5, 1);
            helper.succeed();
        });
    }

    /**
     * A Mangler set to grind only bodies, under a cow whose head and legs it will not take: it cannot grind the body while
     * they hang off it, so it leaves the cow be. It does not unfold the cow's resting body stroke after stroke for nothing
     * (before, it did, about every three seconds, for as long as the cow lay there).
     */
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void manglerLeavesABodyItCannotGrind(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(5, 3, 5);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.setBlock(at.offset(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(at.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        helper.setBlock(at, BBBlocks.MANGLER.getDefaultState());
        if (level.getBlockEntity(helper.absolutePos(at.below())) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(64);
        }
        CarcassMachineBlockEntity mangler = (CarcassMachineBlockEntity) level.getBlockEntity(helper.absolutePos(at));
        mangler.filtering.setFilter(attributeFilter(new BBItemAttributes.PiecePart("body")));
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, at.above());
        if (cow == null) {
            return;
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(cow.resting, "the cow should come to rest on the Mangler"))
                .thenExecuteFor(200, () -> {
                    helper.assertTrue(cow.resting && CarcassSavedData.get(level).carcass(cow.id) == cow, "the Mangler should leave the resting cow folded");
                    helper.assertTrue(mangler.strokes == 0, "and take nothing of it");
                })
                .thenSucceed();
    }

    /**
     * The limb attribute takes any slot key the slot rules give, not only the mod's own: a datapack's rule making a
     * guardian's tail segments fins ("arm.fin") makes them "is a carcass fin arm", worded from the key where the lang
     * file has no words for it. With the shipped rules they are tails, which are PiecePart's.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pieceSlotTakesAnySlotTheDataGives(GameTestHelper helper) {
        com.avicagan.bloodandbones.parts.PartsData.Store store = new com.avicagan.bloodandbones.parts.PartsData.Store();
        java.util.Map<net.minecraft.resources.ResourceLocation, String> rules = new java.util.HashMap<>(
                com.avicagan.bloodandbones.parts.PartsData.SERVER.raw(com.avicagan.bloodandbones.parts.PartsData.Kind.BONE_SLOT_RULES));
        helper.assertTrue(!rules.isEmpty(), "the shipped slot rules should be loaded");
        rules.put(BloodAndBones.asResource("a_fins"), "{\"rules\": [{\"match\": \"^tail\\\\d*$\", \"slot\": \"arm\", \"form\": \"fin\"}]}");
        store.load(com.avicagan.bloodandbones.parts.PartsData.Kind.BONE_SLOT_RULES, rules, com.mojang.serialization.JsonOps.INSTANCE);
        ItemStack tail = new ItemStack(BBItems.CARCASS_PIECE.get());
        tail.set(com.avicagan.bloodandbones.registry.BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(net.minecraft.resources.ResourceLocation.withDefaultNamespace("guardian"),
                "head/tail0", net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/guardian.png"), List.of(), 1.0F, false,
                java.util.Map.of(), 0.0F, 0.0F, 0.0F, false));
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(tail);
        helper.assertTrue("arm.fin".equals(BBItemAttributes.PieceSlot.slotOf(store, piece)), "the datapack's fin should be a slot the filter can name: "
                + BBItemAttributes.PieceSlot.slotOf(store, piece));
        helper.assertTrue(BBItemAttributes.PieceSlot.slotOf(com.avicagan.bloodandbones.parts.PartsData.SERVER, piece) == null,
                "with the shipped rules a guardian's tail is a tail, which the limb attribute leaves to PiecePart");
        Object words = new BBItemAttributes.PieceSlot("arm.fin").getTranslationParameters()[0];
        helper.assertTrue(words instanceof net.minecraft.network.chat.Component c && c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && "fin arm".equals(t.getFallback()), "a key with no words of its own should read from its parts: " + words);
        helper.succeed();
    }

    /**
     * A Cleaver in the main hand and a piece in the other at an empty Butcher's Table: the Cleaver lets the other hand lay
     * the piece first, then chops it, even with a loose piece lying on the table top that it could chop (the client
     * cannot see that one, so both sides decide by what both can see). The Meat Hook at an empty Spit Roast, dragging
     * nothing, lets the other hand skewer its piece, even with a cow lying over the spit. (The client's half is logged by
     * the showcase, which makes the same clicks on the client.)
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theOtherHandHasItsTurn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tablePos = new BlockPos(2, 2, 2);
        BlockPos spitPos = new BlockPos(7, 2, 2);
        helper.setBlock(tablePos, BBBlocks.BUTCHER_TABLE.getDefaultState());
        // the spit flush in a stone floor, so a cow lies across it
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.setBlock(spitPos.offset(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(spitPos, BBBlocks.SPIT_ROAST.getDefaultState());
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, new BlockPos(2, 2, 8));
        // a cow lying over the spit
        CarcassSavedData.Carcass lyingCow = carcass(helper, EntityType.COW, spitPos.above());
        if (cow == null || lyingCow == null) {
            return;
        }
        // a leg set down lying on the table top
        BlockPos table = helper.absolutePos(tablePos);
        var hit = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(table.getX() + 0.5, table.getY() + 1.0, table.getZ() + 0.5),
                Direction.UP, table, false);
        net.minecraft.world.entity.player.Player setter = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack loose = CarcassPieceItem.of(cow, "left_front_leg");
        setter.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, loose);
        loose.useOn(new net.minecraft.world.item.context.UseOnContext(setter, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        helper.runAfterDelay(20, () -> {
            ButcherTableBlockEntity butcher = (ButcherTableBlockEntity) level.getBlockEntity(table);
            CarcassButchery.Lying lying = butcher.lying(level);
            helper.assertTrue(lying != null, "a leg should lie on the table top");
            ItemStack cleaver = new ItemStack(BBItems.CLEAVER.get());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, cleaver);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, CarcassPieceItem.of(cow, "left_hind_leg"));
            helper.assertTrue(level.getBlockState(table).useItemOn(cleaver, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit)
                    == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, "the Cleaver should let the other hand go first");
            helper.assertTrue(CarcassSavedData.get(level).carcass(lying.carcass().id) != null, "and not chop the leg lying there on the same click");
            level.getBlockState(table).useItemOn(player.getOffhandItem(), level, player, net.minecraft.world.InteractionHand.OFF_HAND, hit);
            helper.assertTrue(!butcher.specimen().isEmpty() && player.getOffhandItem().isEmpty(), "the other hand should lay its piece on the table");
            helper.assertTrue(level.getBlockState(table).useItemOn(cleaver, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit).consumesAction()
                    && butcher.specimen().isEmpty(), "then the Cleaver should chop it");
            // the spit
            BlockPos spit = helper.absolutePos(spitPos);
            helper.assertTrue(CarcassButchery.lyingOn(level, spit, 0.5).stream().anyMatch(l -> l.carcass() == lyingCow), "a cow should lie over the spit");
            ItemStack hook = new ItemStack(BBItems.MEAT_HOOK.get());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, hook);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, CarcassPieceItem.of(cow, "right_hind_leg"));
            var spitHit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(spit), Direction.UP, spit, false);
            helper.assertTrue(level.getBlockState(spit).useItemOn(hook, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, spitHit)
                    == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, "the hook dragging nothing should let the other hand go first");
            helper.assertTrue(CarcassSavedData.get(level).carcass(lyingCow.id) != null, "and leave the cow lying there where it is");
            level.getBlockState(spit).useItemOn(player.getOffhandItem(), level, player, net.minecraft.world.InteractionHand.OFF_HAND, spitHit);
            var roast = (com.avicagan.bloodandbones.cooking.SpitRoastBlockEntity) level.getBlockEntity(spit);
            helper.assertTrue(roast.pieces().size() == 1 && !roast.whole(), "the other hand should skewer its piece");
            helper.succeed();
        });
    }
}
