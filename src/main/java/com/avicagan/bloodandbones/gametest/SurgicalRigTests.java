package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.Surgery;
import com.avicagan.bloodandbones.body.SurgeryTableBlock;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.body.SurgicalRig;
import com.avicagan.bloodandbones.body.TableAttachment;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * The Surgical Rig as a butchery path (docs/BRIEF-AUDIT.md packages 5 and 10): each organ comes out once, whichever way
 * the part reached the table; the hide comes off as one of its cuts; a body hanging over it is left alone.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class SurgicalRigTests {
    private static final ResourceLocation COW = ResourceLocation.withDefaultNamespace("cow");

    /** A Surgery Table with its Surgical Rig, sunk so its top is flush with a stone floor round it. */
    private static SurgeryTableBlockEntity rig(GameTestHelper helper, BlockPos at) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.setBlock(at.offset(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(at, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.SURGICAL));
        return (SurgeryTableBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at));
    }

    /** A fresh piece of a cow, with its hide on, as a carried piece is. */
    private static ItemStack piece(String bone) {
        ItemStack stack = new ItemStack(BBItems.CARCASS_PIECE.get());
        stack.set(BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(COW, bone, ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"),
                List.of(), 1.0F, false, Map.of(), 0.0F, 0.0F, 0.0F, false));
        return stack;
    }

    private static CarcassSavedData.Carcass cow(GameTestHelper helper, BlockPos at) {
        Mob mob = helper.spawn(EntityType.COW, at);
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(mob, null);
        mob.discard();
        if (carcass == null) {
            helper.fail("Carcass assembly returned null");
        }
        return carcass;
    }

    /** Every item lying in a box round a spot, counted by item and cleared away. */
    private static void sweep(GameTestHelper helper, BlockPos around, Map<Item, Integer> into) {
        AABB box = new AABB(helper.absolutePos(around)).inflate(3.0);
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            into.merge(item.getItem().getItem(), item.getItem().getCount(), Integer::sum);
            item.discard();
        }
    }

    private static int count(Player player, Map<Item, Integer> swept, Item item) {
        int n = swept.getOrDefault(item, 0);
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** Every cow record with a body over a spot. */
    private static List<CarcassSavedData.Carcass> cowsOver(GameTestHelper helper, BlockPos at) {
        return CarcassButchery.lyingOn(helper.getLevel(), helper.absolutePos(at), SurgicalRig.TOP).stream()
                .map(CarcassButchery.Lying::carcass).filter(c -> c.entity.equals(COW)).distinct().toList();
    }

    /**
     * Each organ comes out of a part once, whichever way the part reached the table. A cow's head laid on the rig gives
     * its two eyes; taken back and set down lying on the table top, it gives none again. A cow lying on the rig gives its
     * six organs (its body's heart, lungs, stomach and rumen, its head's two eyes, as the organ data has them); its head,
     * cut off, picked up and laid on the rig, gives none again. Both ways the count is the one key Surgery#harvest counts
     * under ("organs_taken:head"), and a piece carried counts its own under the plain key.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void rigTakesEachOrganOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pieceRig = new BlockPos(3, 2, 3);
        BlockPos cowRig = new BlockPos(3, 2, 9);
        SurgeryTableBlockEntity first = rig(helper, pieceRig);
        SurgeryTableBlockEntity second = rig(helper, cowRig);
        Player surgeon = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack blade = new ItemStack(BBItems.CLEAVER.get());
        Map<Item, Integer> swept = new java.util.HashMap<>();
        // piece, then world, then rig
        first.put(piece("head"));
        helper.assertTrue(SurgicalRig.cut(level, surgeon, first, blade) && SurgicalRig.cut(level, surgeon, first, blade),
                "a cow's head on the rig should give two eyes");
        helper.assertTrue(count(surgeon, swept, BBItems.EYE.get()) == 2, "two eyes by now");
        ItemStack head = first.take();
        helper.assertTrue(Surgery.organsTaken(CarcassPieceItem.piece(head)) == 2, "the head carried should count its two eyes out");
        BlockPos top = helper.absolutePos(pieceRig);
        BlockHitResult hit = new BlockHitResult(new Vec3(top.getX() + 0.5, top.getY() + SurgicalRig.TOP, top.getZ() + 0.5), Direction.UP, top, false);
        // set down by another hand (the surgeon's holds the eyes)
        Player setter = helper.makeMockPlayer(GameType.SURVIVAL);
        setter.setItemInHand(InteractionHand.MAIN_HAND, head);
        head.useOn(new UseOnContext(setter, InteractionHand.MAIN_HAND, hit));
        CarcassSavedData.Carcass[] laid = new CarcassSavedData.Carcass[1];
        CarcassSavedData.Carcass cow = cow(helper, cowRig.above());
        if (cow == null) {
            return;
        }
        helper.startSequence()
                .thenWaitUntil(() -> {
                    List<CarcassSavedData.Carcass> here = cowsOver(helper, pieceRig);
                    helper.assertTrue(here.size() == 1, "the head set down should lie on the table top: " + here.size());
                    laid[0] = here.getFirst();
                })
                .thenExecute(() -> {
                    helper.assertTrue(Surgery.organsTaken(laid[0].traits, "head", true) == 2 && !laid[0].traits.containsKey(Surgery.ORGANS_TAKEN),
                            "set down, its count is its own bone's: " + laid[0].traits);
                    // the rig's next cut is its hide, not an eye; then the piece comes apart
                    helper.assertTrue(SurgicalRig.cut(level, surgeon, first, blade) && laid[0].skinned, "the next cut should take the head's hide");
                    for (int guard = 0; guard < 4 && !cowsOver(helper, pieceRig).isEmpty(); guard++) {
                        SurgicalRig.cut(level, surgeon, first, blade);
                    }
                    helper.assertTrue(cowsOver(helper, pieceRig).isEmpty(), "the rig should have taken the head apart");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    sweep(helper, pieceRig, swept);
                    helper.assertTrue(count(surgeon, swept, BBItems.EYE.get()) == 2, "the head set down should give no more eyes: "
                            + count(surgeon, swept, BBItems.EYE.get()));
                })
                // world, then piece, then rig
                .thenWaitUntil(() -> helper.assertTrue(!cowsOver(helper, cowRig).isEmpty(), "the cow should lie over the second rig"))
                .thenExecute(() -> {
                    FakePlayer deployer = FakePlayerFactory.getMinecraft(level);
                    for (int cut = 0; cut < 6; cut++) {
                        helper.assertTrue(SurgicalRig.cut(level, deployer, second, blade), "cut " + (cut + 1) + " should take an organ out of the cow");
                    }
                    helper.assertTrue(!cow.skinned, "the organs come out before the hide");
                    helper.assertTrue("2".equals(cow.traits.get(Surgery.ORGANS_TAKEN + ":head")) && "4".equals(cow.traits.get(Surgery.ORGANS_TAKEN + ":body")),
                            "the cow should count its body's four and its head's two: " + cow.traits);
                    // the head comes off, is picked up and laid on the rig
                    CarcassSavedData.Carcass cut = CarcassButchery.sever(level, cow, "head", null);
                    helper.assertTrue(cut != null, "the head should come off");
                    Player carrier = helper.makeMockPlayer(GameType.SURVIVAL);
                    helper.assertTrue(CarcassButchery.pickUp(level, carrier, cut, "head"), "the head should be light enough to pick up");
                    ItemStack carried = carrier.getInventory().items.stream().filter(s -> s.is(BBItems.CARCASS_PIECE.get())).findFirst().orElseThrow();
                    helper.assertTrue(Surgery.organsTaken(CarcassPieceItem.piece(carried)) == 2, "the head picked up should still count its two eyes out");
                    second.put(carried.copy());
                    int before = count(carrier, swept, BBItems.EYE.get());
                    SurgicalRig.cut(level, carrier, second, blade);
                    helper.assertTrue(count(carrier, swept, BBItems.EYE.get()) == before && CarcassPieceItem.piece(second.item()) != null
                            && CarcassPieceItem.piece(second.item()).skinned(), "the carried head should give its hide next, not an eye");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    sweep(helper, cowRig, swept);
                    helper.assertTrue(swept.getOrDefault(BBItems.EYE.get(), 0) == 2 && swept.getOrDefault(BBItems.HEART.get(), 0) == 1
                                    && swept.getOrDefault(BBItems.GLAND.get(), 0) == 1,
                            "the cow should give its two eyes, one heart and its rumen, once each: " + swept);
                })
                .thenSucceed();
    }

    /**
     * The rig takes the hide as one of its cuts, once the organs are out: a cow's unskinned leg laid on it is flayed on the
     * first cut and taken apart on the next; a Deployer's cuts on a cow lying on it flay the whole cow
     * after its six organs. A cow hanging from a Shackle Hook over the rig is not the rig's: a Cleaver clicked there is laid
     * down on the table, as with nothing over it.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void rigTakesTheHideAndLeavesAHungBodyAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos legRig = new BlockPos(3, 2, 3);
        BlockPos cowRig = new BlockPos(9, 2, 3);
        BlockPos hungRig = new BlockPos(6, 2, 9);
        SurgeryTableBlockEntity legTable = rig(helper, legRig);
        SurgeryTableBlockEntity cowTable = rig(helper, cowRig);
        SurgeryTableBlockEntity hungTable = rig(helper, hungRig);
        FakePlayer deployer = FakePlayerFactory.getMinecraft(level);
        ItemStack blade = new ItemStack(BBItems.CLEAVER.get());
        Map<Item, Integer> whole = new java.util.HashMap<>();
        legTable.put(piece("left_hind_leg"));
        helper.assertTrue(SurgicalRig.cut(level, deployer, legTable, blade), "the first cut on a leg should take its hide");
        helper.assertTrue(CarcassPieceItem.piece(legTable.item()) != null && CarcassPieceItem.piece(legTable.item()).skinned(),
                "the leg should be skinned now, and still on the table");
        CarcassSavedData.Carcass cow = cow(helper, cowRig.above());
        // a cow hung by its neck from a Shackle Hook under a ceiling three blocks over the third rig
        BlockPos hookAt = hungRig.above(3);
        helper.setBlock(hookAt.above(), Blocks.STONE);
        helper.setBlock(hookAt, BBBlocks.SHACKLE_HOOK.getDefaultState().setValue(com.avicagan.bloodandbones.carcass.ShackleHookBlock.FACING, Direction.UP));
        CarcassSavedData.Carcass hung = cow(helper, hungRig.above());
        if (cow == null || hung == null) {
            return;
        }
        Player hauler = helper.makeMockPlayer(GameType.SURVIVAL);
        hauler.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        hauler.setPos(Vec3.atBottomCenterOf(helper.absolutePos(hungRig.east())));
        hauler.setOldPosAndRot();
        AABB over = new AABB(helper.absolutePos(hungRig)).setMinY(helper.absolutePos(hungRig).getY() + SurgicalRig.TOP - 0.3)
                .setMaxY(helper.absolutePos(hungRig).getY() + SurgicalRig.TOP + 1.5).inflate(0.25, 0.0, 0.25);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(SurgicalRig.cut(level, deployer, legTable, blade) && legTable.item().isEmpty(), "the next cut takes the leg apart");
                    var torso = (dev.ryanhcode.sable.sublevel.ServerSubLevel) dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level)
                            .getSubLevel(hung.bones.get(hung.rootBone));
                    helper.assertTrue(com.avicagan.bloodandbones.carcass.CarcassDrag.start(level, hauler, torso.getPlot().getCenterBlock(), null), "the hook should take hold of the cow");
                    ShackleHookBlockEntity hook = (ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(hookAt));
                    helper.assertTrue(hook.hang(level, hauler), "the Shackle Hook should take the cow");
                })
                .thenWaitUntil(() -> helper.assertTrue(!cowsOver(helper, cowRig).isEmpty(), "the cow should lie over the rig"))
                .thenExecute(() -> {
                    for (int cut = 0; cut < 6; cut++) {
                        SurgicalRig.cut(level, deployer, cowTable, blade);
                    }
                    helper.assertTrue(!cow.skinned, "the organs come out before the hide");
                    helper.assertTrue(SurgicalRig.cut(level, deployer, cowTable, blade) && cow.skinned, "the seventh cut should flay the cow");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    sweep(helper, cowRig, whole);
                    // a Deployer works the rig as a station: the whole hide, 2.43 a cow
                    helper.assertTrue(whole.getOrDefault(BBItems.RAW_HIDE.get(), 0) >= 2, "the rig should give the cow's whole hide: " + whole);
                })
                .thenWaitUntil(() -> helper.assertTrue(com.avicagan.bloodandbones.carcass.CarcassRest.isHeld(level, hung) && hung.bones.keySet().stream().anyMatch(bone -> {
                    org.joml.Vector3d at = CarcassAssembler.boneWorldPosition(level, hung, bone);
                    return at != null && over.contains(at.x, at.y, at.z);
                }), "the cow should hang with a part over the table top"))
                .thenExecute(() -> {
                    helper.assertTrue(CarcassButchery.lyingOn(level, helper.absolutePos(hungRig), SurgicalRig.TOP).stream().noneMatch(l -> l.carcass() == hung),
                            "a hung body only sways over the table: it does not lie on it");
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    ItemStack cleaver = new ItemStack(BBItems.CLEAVER.get());
                    player.setItemInHand(InteractionHand.MAIN_HAND, cleaver);
                    BlockPos top = helper.absolutePos(hungRig);
                    level.getBlockState(top).useItemOn(cleaver, level, player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(top), Direction.UP, top, false));
                    helper.assertTrue(hungTable.item().is(BBItems.CLEAVER.get()), "with only a hung body over it, the Cleaver should be laid on the table");
                    helper.assertTrue(hung.traits.keySet().stream().noneMatch(k -> k.startsWith(Surgery.ORGANS_TAKEN)), "and the hung cow left whole");
                })
                .thenSucceed();
    }

    private static ResourceLocation bb(String path) {
        return BloodAndBones.asResource(path);
    }

    /**
     * A filter for single organs (docs/BRIEF-AUDIT.md package 10: "every station that removes parts carries a filter"). An
     * organ goes in the rig's slot, and the Attribute Filter offers "is the organ Heart" for one. Set to a heart, a cow's body
     * laid on the rig gives its heart and then nothing, the lungs after it left in; "is the organ Stomach" takes the stomach
     * past the lungs, the count kept as the places taken ("0,2"); a Filter holding the rumen's Gland takes the rumen; with
     * no filter the lungs come out last, and then the hide. A whole cow lying on a rig set to eyes gives its head's two eyes
     * and nothing of its body, and is not flayed, cut up or unfolded for it. A Butcher's Table set to a heart takes no piece
     * (organs never come out there: a Cleaver there cuts a piece into its butchery table).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void rigFilterPicksSingleOrgans(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pieceRig = new BlockPos(3, 2, 3);
        BlockPos cowRig = new BlockPos(3, 2, 10);
        SurgeryTableBlockEntity first = rig(helper, pieceRig);
        SurgeryTableBlockEntity second = rig(helper, cowRig);
        FakePlayer deployer = FakePlayerFactory.getMinecraft(level);
        ItemStack blade = new ItemStack(BBItems.CLEAVER.get());
        helper.assertTrue(com.avicagan.bloodandbones.machine.PartFilter.allowed(new ItemStack(BBItems.HEART.get()))
                && !com.avicagan.bloodandbones.machine.PartFilter.allowed(new ItemStack(net.minecraft.world.item.Items.STICK)), "an organ, not a stick, should go in the slot");
        helper.assertTrue(com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.getAllAttributes(new ItemStack(BBItems.HEART.get()), level)
                .contains(new com.avicagan.bloodandbones.registry.BBItemAttributes.OrganIs(bb("heart"))), "the Attribute Filter should offer \"is the organ Heart\"");
        // a heart in the slot: the heart and nothing after it
        first.filtering.setFilter(new ItemStack(BBItems.HEART.get()));
        first.put(piece("body"));
        helper.assertTrue(SurgicalRig.cut(level, deployer, first, blade), "a rig set to a heart should take a cow's heart");
        helper.assertTrue(!SurgicalRig.cut(level, deployer, first, blade), "and then nothing: the lungs are next, and not a heart");
        helper.assertTrue(Surgery.organsTaken(CarcassPieceItem.piece(first.item())) == 1 && !CarcassPieceItem.piece(first.item()).skinned(),
                "one organ out, the piece not flayed with organs still in it");
        // the stomach, past the lungs
        first.filtering.setFilter(PartFilterTests.attributeFilter(new com.avicagan.bloodandbones.registry.BBItemAttributes.OrganIs(bb("stomach"))));
        helper.assertTrue(SurgicalRig.cut(level, deployer, first, blade) && !SurgicalRig.cut(level, deployer, first, blade),
                "\"is the organ Stomach\" should take the stomach and then nothing");
        helper.assertTrue("0,2".equals(CarcassPieceItem.piece(first.item()).traits().get(Surgery.ORGANS_TAKEN)),
                "the heart and the stomach out, the lungs still in: " + CarcassPieceItem.piece(first.item()).traits());
        // a Filter holding the rumen's Gland
        ItemStack list = new ItemStack(com.simibubi.create.AllItems.FILTER.get());
        list.set(com.simibubi.create.AllDataComponents.FILTER_ITEMS, net.minecraft.world.item.component.ItemContainerContents.fromItems(
                List.of(com.avicagan.bloodandbones.parts.Organs.stack(com.avicagan.bloodandbones.parts.PartsData.SERVER, bb("rumen"), COW, false))));
        first.filtering.setFilter(list);
        helper.assertTrue(SurgicalRig.cut(level, deployer, first, blade) && !SurgicalRig.cut(level, deployer, first, blade),
                "a Filter holding the rumen should take the rumen and then nothing");
        // no filter: the lungs, then the hide
        first.filtering.setFilter(ItemStack.EMPTY);
        helper.assertTrue(SurgicalRig.cut(level, deployer, first, blade) && Surgery.organsTaken(CarcassPieceItem.piece(first.item())) == 4,
                "with no filter the lungs should come out last");
        helper.assertTrue(SurgicalRig.cut(level, deployer, first, blade) && CarcassPieceItem.piece(first.item()).skinned(), "and then the hide");
        // a Butcher's Table set to a heart takes no piece
        BlockPos butcherAt = new BlockPos(8, 2, 3);
        helper.setBlock(butcherAt, BBBlocks.BUTCHER_TABLE.getDefaultState());
        var butcher = (com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity) level.getBlockEntity(helper.absolutePos(butcherAt));
        butcher.filtering.setFilter(new ItemStack(BBItems.HEART.get()));
        helper.assertTrue(!butcher.inventory.insertItem(0, piece("body"), false).isEmpty() && butcher.specimen().isEmpty(),
                "a Butcher's Table set to a heart should take no piece");
        // a whole cow lying on a rig set to eyes
        second.filtering.setFilter(new ItemStack(BBItems.EYE.get()));
        CarcassSavedData.Carcass cow = cow(helper, cowRig.above());
        if (cow == null) {
            return;
        }
        int bones = cow.bones.size();
        Map<Item, Integer> swept = new java.util.HashMap<>();
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> sweep(helper, pieceRig, swept))
                .thenExecute(() -> {
                    helper.assertTrue(swept.getOrDefault(BBItems.HEART.get(), 0) == 1 && swept.getOrDefault(BBItems.STOMACH.get(), 0) == 1
                            && swept.getOrDefault(BBItems.GLAND.get(), 0) == 1 && swept.getOrDefault(BBItems.LUNGS.get(), 0) == 1,
                            "the body should give one of each, each by its filter: " + swept);
                    swept.clear();
                })
                .thenWaitUntil(() -> helper.assertTrue(!cowsOver(helper, cowRig).isEmpty(), "the cow should lie over the second rig"))
                .thenExecute(() -> {
                    helper.assertTrue(SurgicalRig.cut(level, deployer, second, blade) && SurgicalRig.cut(level, deployer, second, blade),
                            "a rig set to eyes should take the cow's two eyes");
                    helper.assertTrue(!SurgicalRig.cut(level, deployer, second, blade), "and then nothing");
                    helper.assertTrue("2".equals(cow.traits.get(Surgery.ORGANS_TAKEN + ":head")) && !cow.traits.containsKey(Surgery.ORGANS_TAKEN + ":body"),
                            "the head's two out and none of the body's: " + cow.traits);
                    helper.assertTrue(!cow.skinned && cow.bones.size() + cow.restPoses.size() >= bones && CarcassSavedData.get(level).carcass(cow.id) == cow,
                            "the cow should be left whole and unflayed");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    sweep(helper, cowRig, swept);
                    helper.assertTrue(swept.getOrDefault(BBItems.EYE.get(), 0) == 2 && swept.getOrDefault(BBItems.HEART.get(), 0) == 0,
                            "two eyes and no heart from the cow: " + swept);
                })
                .thenSucceed();
    }
}
