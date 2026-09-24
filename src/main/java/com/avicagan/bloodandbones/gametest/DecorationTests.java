package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.decoration.BonePileBlock;
import com.avicagan.bloodandbones.decoration.GutChainHanging;
import com.avicagan.bloodandbones.decoration.HangingGutChainEntity;
import com.avicagan.bloodandbones.decoration.RibcageArchBlock;
import com.avicagan.bloodandbones.decoration.SteelRackBlock;
import com.avicagan.bloodandbones.decoration.SteelRackBlockEntity;
import com.avicagan.bloodandbones.decoration.SteelTableBlock;
import com.avicagan.bloodandbones.decoration.SteelTableBlockEntity;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The decoration from the brief: morgue furniture, ribs, bone piles, gut chain on conveyors, bloody cladding. */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class DecorationTests {
    /** A block's shape stands on the floor (it has legs) or starts higher up. */
    private static double shapeBottom(GameTestHelper helper, BlockPos rel) {
        BlockPos pos = helper.absolutePos(rel);
        return helper.getLevel().getBlockState(pos).getShape(helper.getLevel(), pos).bounds().minY;
    }

    /** The lowest and highest point, in blocks, that one of our block models draws, read from its file. */
    private static double[] modelHeights(String model) {
        try (var in = BloodAndBones.class.getResourceAsStream("/assets/bloodandbones/models/block/" + model + ".json")) {
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
            double[] heights = {16, 0};
            for (var element : json.getAsJsonArray("elements")) {
                heights[0] = Math.min(heights[0], element.getAsJsonObject().getAsJsonArray("from").get(1).getAsDouble());
                heights[1] = Math.max(heights[1], element.getAsJsonObject().getAsJsonArray("to").get(1).getAsDouble());
            }
            return new double[]{heights[0] / 16.0, heights[1] / 16.0};
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the model " + model, e);
        }
    }

    /**
     * Picks up what a test dropped, so nothing is left lying about for the tests beside it.
     */
    private static void clearDrops(GameTestHelper helper) {
        helper.killAllEntitiesOfClass(ItemEntity.class);
    }

    /** Right-click a block at a point on its face, as a player would. */
    private static ItemInteractionResult click(GameTestHelper helper, Player player, BlockPos rel, Vec3 local, Direction face) {
        BlockPos pos = helper.absolutePos(rel);
        BlockHitResult hit = new BlockHitResult(Vec3.atLowerCornerOf(pos).add(local), face, pos, false);
        BlockState state = helper.getLevel().getBlockState(pos);
        return state.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    /**
     * Tables side by side join into one run: an L of four has legs only at its three ends and on the outside
     * of the turn, none under the middle, and the joins follow a table taken out or the run turned.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void steelTablesJoinIntoARun(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 2, 2);
        BlockPos b = a.east();
        BlockPos c = b.east();
        BlockPos d = c.south();
        for (BlockPos pos : List.of(a, b, c, d)) {
            BlockPos abs = helper.absolutePos(pos);
            helper.getLevel().setBlock(abs, BBBlocks.STEEL_TABLE.get().getStateForPlacement(new net.minecraft.world.item.context.BlockPlaceContext(
                    helper.getLevel(), null, InteractionHand.MAIN_HAND, BBBlocks.STEEL_TABLE.asStack(),
                    new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false))), Block.UPDATE_ALL);
        }
        BlockState sa = helper.getBlockState(a);
        BlockState sb = helper.getBlockState(b);
        BlockState sc = helper.getBlockState(c);
        BlockState sd = helper.getBlockState(d);
        if (!sa.getValue(SteelTableBlock.EAST) || sa.getValue(SteelTableBlock.WEST) || !sb.getValue(SteelTableBlock.EAST) || !sb.getValue(SteelTableBlock.WEST)
                || !sc.getValue(SteelTableBlock.WEST) || !sc.getValue(SteelTableBlock.SOUTH) || sc.getValue(SteelTableBlock.EAST) || !sd.getValue(SteelTableBlock.NORTH)) {
            helper.fail("The tables did not join up: " + sa + " " + sb + " " + sc + " " + sd);
        }
        // legs: two at the west end, none in the middle, one on the outside of the turn, two at the south end
        if (!SteelTableBlock.leg(sa, Direction.NORTH, Direction.WEST) || !SteelTableBlock.leg(sa, Direction.SOUTH, Direction.WEST)
                || SteelTableBlock.leg(sa, Direction.NORTH, Direction.EAST)) {
            helper.fail("The end of a run should stand on two legs at its end");
        }
        if (shapeBottom(helper, b) < 0.7) {
            helper.fail("A table in the middle of a run should have no legs, its shape goes down to " + shapeBottom(helper, b));
        }
        if (!SteelTableBlock.leg(sc, Direction.NORTH, Direction.EAST) || SteelTableBlock.leg(sc, Direction.SOUTH, Direction.WEST)
                || SteelTableBlock.leg(sc, Direction.NORTH, Direction.WEST) || SteelTableBlock.leg(sc, Direction.SOUTH, Direction.EAST)) {
            helper.fail("The corner of a turn should have one leg, on its outside corner");
        }
        if (shapeBottom(helper, a) > 0.01 || shapeBottom(helper, d) > 0.01) {
            helper.fail("The ends of the run should reach the floor");
        }
        // the shape follows the model: the top as thick as it is drawn, and the legs reaching up to it
        double[] top = modelHeights("steel_table_top");
        double[] leg = modelHeights("steel_table_leg");
        if (Math.abs(shapeBottom(helper, b) - top[0]) > 1.0e-6 || Math.abs(leg[1] - top[0]) > 1.0e-6) {
            helper.fail("The table's shape should start where its drawn top does, at " + top[0] + ", not " + shapeBottom(helper, b));
        }
        VoxelShape withLegs = sa.getShape(helper.getLevel(), helper.absolutePos(a));
        for (double y = 0.02; y < top[1]; y += 0.05) {
            double at = y;
            if (withLegs.toAabbs().stream().noneMatch(box -> box.contains(2 / 16.0, at, 2 / 16.0))) {
                helper.fail("A leg should stand under the corner all the way up into the top, a gap at " + at);
                break;
            }
        }
        // turned a quarter clockwise on a contraption, the corner's joins turn with it
        BlockState turned = sc.rotate(Rotation.CLOCKWISE_90);
        if (!turned.getValue(SteelTableBlock.NORTH) || !turned.getValue(SteelTableBlock.WEST) || turned.getValue(SteelTableBlock.SOUTH) || turned.getValue(SteelTableBlock.EAST)) {
            helper.fail("A corner joined west and south, turned clockwise, should join north and west: " + turned);
        }
        // take the middle out: the ends stand on their own
        helper.getLevel().destroyBlock(helper.absolutePos(b), false);
        if (helper.getBlockState(a).getValue(SteelTableBlock.EAST) || helper.getBlockState(c).getValue(SteelTableBlock.WEST)) {
            helper.fail("Tables should stop joining a table that is taken away");
        }
        helper.succeed();
    }

    /**
     * A steel table holds one item, any item: put on from the top, not from a side (where a held block is
     * placed as usual), taken back with an empty hand, loaded by a funnel's handler, and dropped when broken.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void steelTableHoldsOneItem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, BBBlocks.STEEL_TABLE.getDefaultState());
        SteelTableBlockEntity table = (SteelTableBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 3));
        if (click(helper, player, pos, new Vec3(0.5, 0.5, 0.0), Direction.NORTH).consumesAction() || !table.specimen().isEmpty()) {
            helper.fail("Clicking a table's side should not lay things on it");
        }
        click(helper, player, pos, new Vec3(0.5, 15 / 16.0, 0.5), Direction.UP);
        click(helper, player, pos, new Vec3(0.5, 15 / 16.0, 0.5), Direction.UP);
        if (!table.specimen().is(Items.DIAMOND) || table.specimen().getCount() != 1 || player.getMainHandItem().getCount() != 2) {
            helper.fail("The table should take one diamond from the top and no second, holds " + table.specimen());
        }
        if (!table.inventory.insertItem(0, new ItemStack(Items.BONE), true).is(Items.BONE)) {
            helper.fail("A funnel should not fit a second thing on a full table");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        click(helper, player, pos, new Vec3(0.5, 0.2, 0.0), Direction.NORTH);
        if (!table.specimen().isEmpty() || !player.getInventory().contains(new ItemStack(Items.DIAMOND))) {
            helper.fail("An empty hand should take the diamond back");
        }
        if (!table.inventory.insertItem(0, new ItemStack(Items.BONE, 5), false).is(Items.BONE) || !table.specimen().is(Items.BONE)) {
            helper.fail("A funnel should lay one bone on the empty table and keep the rest");
        }
        helper.getLevel().destroyBlock(helper.absolutePos(pos), false);
        helper.assertItemEntityPresent(Items.BONE, pos, 2.0);
        clearDrops(helper);
        helper.succeed();
    }

    /**
     * The rack's four places: a click on the front goes to the place looked at, whichever way the rack faces
     * (not a click on its side); an empty hand takes that one back; a funnel fills from the lower left;
     * breaking it drops them all.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void steelRackPlacesWhatYouLookAt(GameTestHelper helper) {
        // facing east, seen from the east: the viewer's left is south; facing south, their right is east
        if (SteelRackBlock.slotAt(Direction.EAST, new Vec3(0.9, 0.2, 0.8)) != 0 || SteelRackBlock.slotAt(Direction.EAST, new Vec3(0.9, 0.8, 0.2)) != 3
                || SteelRackBlock.slotAt(Direction.SOUTH, new Vec3(0.8, 0.2, 0.9)) != 1) {
            helper.fail("The places are not where a player facing the rack sees them");
        }
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, BBBlocks.STEEL_RACK.getDefaultState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        SteelRackBlockEntity rack = (SteelRackBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // facing north, seen from the north: the viewer's left is east (high x)
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        click(helper, player, pos, new Vec3(0.75, 0.25, 0.0), Direction.NORTH);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE));
        click(helper, player, pos, new Vec3(0.25, 0.75, 0.0), Direction.NORTH);
        if (!rack.item(0).is(Items.DIAMOND) || !rack.item(3).is(Items.BONE) || !rack.item(1).isEmpty() || !rack.item(2).isEmpty()) {
            helper.fail("The diamond should be on the lower left and the bone on the upper right, got "
                    + List.of(rack.item(0), rack.item(1), rack.item(2), rack.item(3)));
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
        if (click(helper, player, pos, new Vec3(0.25, 0.75, 0.0), Direction.NORTH).consumesAction() || !rack.item(3).is(Items.BONE)) {
            helper.fail("A taken place should not take another item");
        }
        if (click(helper, player, pos, new Vec3(1.0, 0.75, 0.5), Direction.EAST).consumesAction() || !rack.item(2).isEmpty()) {
            helper.fail("Clicking a rack's side should place a held block as usual, not stock the shelf");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        click(helper, player, pos, new Vec3(0.75, 0.25, 0.0), Direction.NORTH);
        if (!rack.item(0).isEmpty() || !player.getInventory().contains(new ItemStack(Items.DIAMOND)) || !rack.item(3).is(Items.BONE)) {
            helper.fail("An empty hand should take back only the diamond it looked at");
        }
        ItemStack rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(rack.inventory, new ItemStack(Items.APPLE, 5), false);
        if (rest.getCount() != 2 || !rack.item(0).is(Items.APPLE) || !rack.item(1).is(Items.APPLE) || !rack.item(2).is(Items.APPLE)) {
            helper.fail("A funnel should fill the three free places, one apple each, and keep two");
        }
        helper.getLevel().destroyBlock(helper.absolutePos(pos), false);
        helper.runAfterDelay(1, () -> {
            AABB area = new AABB(helper.absolutePos(pos)).inflate(2.0);
            int apples = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(Items.APPLE)).stream().mapToInt(e -> e.getItem().getCount()).sum();
            if (apples != 3) {
                helper.fail("A broken rack should drop its three apples, dropped " + apples);
            }
            helper.assertItemEntityPresent(Items.BONE, pos, 2.0);
            clearDrops(helper);
            helper.succeed();
        });
    }

    /**
     * Ribs shape themselves: two stacks facing each other rise straight and bend in at the top, ribs hung
     * between them run level, a lone rib bends, and a stack's top bends again when the rib over it goes.
     * A rib placed against another takes its facing.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ribcageArchesShapeThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState east = BBBlocks.RIBCAGE_ARCH.getDefaultState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
        BlockState west = BBBlocks.RIBCAGE_ARCH.getDefaultState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST);
        List<BlockPos> placed = new java.util.ArrayList<>();
        for (int y = 2; y <= 4; y++) {
            placed.add(new BlockPos(2, y, 4));
            placed.add(new BlockPos(5, y, 4));
        }
        placed.add(new BlockPos(3, 4, 4));
        placed.add(new BlockPos(4, 4, 4));
        placed.add(new BlockPos(8, 2, 8));
        for (BlockPos rel : placed) {
            helper.setBlock(rel, rel.getX() >= 4 && rel.getX() <= 5 ? west : east);
        }
        for (BlockPos rel : placed) {
            BlockPos abs = helper.absolutePos(rel);
            level.setBlock(abs, Block.updateFromNeighbourShapes(level.getBlockState(abs), level, abs), Block.UPDATE_ALL);
        }
        java.util.function.BiConsumer<BlockPos, RibcageArchBlock.Shape> expect = (rel, shape) -> {
            RibcageArchBlock.Shape got = helper.getBlockState(rel).getValue(RibcageArchBlock.SHAPE);
            if (got != shape) {
                helper.fail("The rib at " + rel + " should be " + shape + ", is " + got);
            }
        };
        expect.accept(new BlockPos(2, 2, 4), RibcageArchBlock.Shape.STRAIGHT);
        expect.accept(new BlockPos(2, 3, 4), RibcageArchBlock.Shape.STRAIGHT);
        expect.accept(new BlockPos(2, 4, 4), RibcageArchBlock.Shape.CURVE);
        expect.accept(new BlockPos(3, 4, 4), RibcageArchBlock.Shape.TOP);
        expect.accept(new BlockPos(4, 4, 4), RibcageArchBlock.Shape.TOP);
        expect.accept(new BlockPos(5, 4, 4), RibcageArchBlock.Shape.CURVE);
        expect.accept(new BlockPos(8, 2, 8), RibcageArchBlock.Shape.CURVE);
        // the shapes are its collision too: a level rib is up at the crown, not on the floor
        BlockPos crown = helper.absolutePos(new BlockPos(3, 4, 4));
        if (level.getBlockState(crown).getCollisionShape(level, crown, CollisionContext.empty()).bounds().minY < 0.5) {
            helper.fail("A level rib's collision should be up at the crown");
        }
        // take the arch's left top away: the rib under it now tops its stack and bends in
        level.destroyBlock(helper.absolutePos(new BlockPos(2, 4, 4)), false);
        expect.accept(new BlockPos(2, 3, 4), RibcageArchBlock.Shape.CURVE);
        // placed on top of the right stack by a player facing north, it still faces west, as the stack does
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setYRot(180.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, BBBlocks.RIBCAGE_ARCH.asStack());
        BlockPos top = helper.absolutePos(new BlockPos(5, 4, 4));
        BBBlocks.RIBCAGE_ARCH.asItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(top).add(0, 0.5, 0), Direction.UP, top, false)));
        BlockState above = level.getBlockState(top.above());
        if (!above.is(BBBlocks.RIBCAGE_ARCH.get()) || above.getValue(HorizontalDirectionalBlock.FACING) != Direction.WEST
                || level.getBlockState(top).getValue(RibcageArchBlock.SHAPE) != RibcageArchBlock.Shape.STRAIGHT) {
            helper.fail("A rib stacked on another should take its facing, and the one under it rise straight: " + above);
        }
        helper.succeed();
    }

    /**
     * A bone pile builds up a layer a use, to a full block and then onto the next; drops two bones a layer;
     * a single layer can be walked through; it needs a solid floor, or a full pile, under it.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bonePilesLayerUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBBlocks.BONE_PILE.asItem(), 10));
        // first on the floor, then on its own top eight times: seven fill it, the eighth starts the next
        BBBlocks.BONE_PILE.asItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP, pos.below(), false)));
        BlockState one = level.getBlockState(pos);
        if (!one.is(BBBlocks.BONE_PILE.get()) || one.getValue(BonePileBlock.LAYERS) != 1 || !one.getCollisionShape(level, pos).isEmpty()) {
            helper.fail("A first layer should lie on the floor and be walked through: " + one);
        }
        for (int i = 0; i < 8; i++) {
            BlockPos at = level.getBlockState(pos.above()).is(BBBlocks.BONE_PILE.get()) ? pos.above() : pos;
            BBBlocks.BONE_PILE.asItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atLowerCornerOf(at).add(0.5, level.getBlockState(at).getShape(level, at).max(Direction.Axis.Y), 0.5), Direction.UP, at, false)));
        }
        BlockState full = level.getBlockState(pos);
        BlockState next = level.getBlockState(pos.above());
        if (full.getValue(BonePileBlock.LAYERS) != 8 || !next.is(BBBlocks.BONE_PILE.get()) || next.getValue(BonePileBlock.LAYERS) != 1) {
            helper.fail("Eight more uses should fill the pile and start another on top of it: " + full + " / " + next);
        }
        if (player.getMainHandItem().getCount() != 1) {
            helper.fail("Each layer should use one Bone Pile, " + player.getMainHandItem().getCount() + " left of 10 after 9");
        }
        if (Math.abs(full.getCollisionShape(level, pos).max(Direction.Axis.Y) - 14 / 16.0) > 1.0e-6) {
            helper.fail("A full pile's feet sink in two pixels, as snow's do");
        }
        List<ItemStack> drops = Block.getDrops(full, level, pos, null);
        int bones = drops.stream().filter(s -> s.is(Items.BONE)).mapToInt(ItemStack::getCount).sum();
        List<ItemStack> fewer = Block.getDrops(BBBlocks.BONE_PILE.getDefaultState().setValue(BonePileBlock.LAYERS, 3), level, pos, null);
        int three = fewer.stream().filter(s -> s.is(Items.BONE)).mapToInt(ItemStack::getCount).sum();
        if (bones != 16 || three != 6) {
            helper.fail("A pile should drop two bones a layer: 8 layers gave " + bones + ", 3 gave " + three);
        }
        // no floor, no pile; and nothing stacks on a pile that is not full
        BlockPos air = helper.absolutePos(new BlockPos(6, 5, 6));
        if (BBBlocks.BONE_PILE.getDefaultState().canSurvive(level, air)) {
            helper.fail("A bone pile should not float");
        }
        level.setBlock(pos, BBBlocks.BONE_PILE.getDefaultState().setValue(BonePileBlock.LAYERS, 4), Block.UPDATE_ALL);
        if (level.getBlockState(pos.above()).is(BBBlocks.BONE_PILE.get())) {
            helper.fail("A pile on a pile that is no longer full should fall");
        }
        clearDrops(helper);
        helper.succeed();
    }

    /**
     * Gut Chain on a chain conveyor: a player aiming at the chain hangs a link, uses more to lengthen it (to
     * eight at most), it rides the moving chain, its length reaches a client's copy through the synced data,
     * it saves, and hit, it comes down with all its links.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void gutChainRidesAChainConveyor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos aRel = new BlockPos(1, 6, 5);
        BlockPos bRel = new BlockPos(9, 6, 5);
        helper.setBlock(aRel, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        helper.setBlock(bRel, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        helper.setBlock(aRel.above(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.DOWN));
        BlockPos a = helper.absolutePos(aRel);
        BlockPos b = helper.absolutePos(bRel);
        HangingGutChainEntity[] chain = new HangingGutChainEntity[1];
        double[] range = {Double.MAX_VALUE, -Double.MAX_VALUE};
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.runAfterDelay(5, () -> {
            ChainConveyorBlockEntity aBe = (ChainConveyorBlockEntity) level.getBlockEntity(a);
            ChainConveyorBlockEntity bBe = (ChainConveyorBlockEntity) level.getBlockEntity(b);
            if (!bBe.addConnectionTo(a) || !aBe.addConnectionTo(b)) {
                helper.fail("Could not connect the chain conveyors");
            }
        });
        helper.runAfterDelay(10, () -> {
            // stand beside the middle of a strand, below it, and look at it
            ChainConveyorBlockEntity aBe = (ChainConveyorBlockEntity) level.getBlockEntity(a);
            aBe.prepareStats();
            var stats = aBe.connectionStats.get(b.subtract(a));
            Vec3 middle = stats.start().add(stats.end()).scale(0.5);
            player.moveTo(middle.x, middle.y - 2.5, middle.z - 2.0);
            player.lookAt(EntityAnchorArgument.Anchor.EYES, middle);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBBlocks.GUT_CHAIN.asItem(), 12));
            if (!GutChainHanging.tryHang(player, InteractionHand.MAIN_HAND, null)) {
                helper.fail("Gut Chain used on the chain should hang there");
                return;
            }
            List<HangingGutChainEntity> hung = level.getEntitiesOfClass(HangingGutChainEntity.class, new AABB(middle, middle).inflate(1.5));
            if (hung.size() != 1 || hung.get(0).links() != 1 || player.getMainHandItem().getCount() != 11) {
                helper.fail("One link should hang from the chain, using one Gut Chain: " + hung.size() + " strings, " + player.getMainHandItem().getCount() + " left");
                return;
            }
            chain[0] = hung.get(0);
            for (int i = 0; i < 10; i++) {
                chain[0].interact(player, InteractionHand.MAIN_HAND);
            }
            if (chain[0].links() != HangingGutChainEntity.MAX_LINKS || player.getMainHandItem().getCount() != 12 - HangingGutChainEntity.MAX_LINKS) {
                helper.fail("More Gut Chain should lengthen it to eight links and no more: " + chain[0].links() + " links, " + player.getMainHandItem().getCount() + " left");
            }
            if (chain[0].getBoundingBox().getYsize() < HangingGutChainEntity.MAX_LINKS) {
                helper.fail("Its box should hang the string's length");
            }
            if (java.util.Arrays.stream(chain[0].getParts()).filter(Entity::isPickable).count() != HangingGutChainEntity.MAX_LINKS
                    || java.util.Arrays.stream(chain[0].getParts()).mapToDouble(part -> part.getBoundingBox().minY).min().orElse(0)
                    > chain[0].getY() - HangingGutChainEntity.MAX_LINKS) {
                helper.fail("Each of its eight links should be a hit box, down its whole length, to be hit anywhere along it");
            }
            // what a client makes of the synced data: a copy with the same length and box
            HangingGutChainEntity copy = BBEntities.HANGING_GUT_CHAIN.get().create(level);
            var dirty = chain[0].getEntityData().getNonDefaultValues();
            if (dirty == null) {
                helper.fail("The length should be sent to clients");
                return;
            }
            copy.getEntityData().assignValues(dirty);
            if (copy.links() != chain[0].links() || copy.getBoundingBox().getYsize() < HangingGutChainEntity.MAX_LINKS) {
                helper.fail("A client's copy should get the length and the box from the synced data, got " + copy.links());
            }
            // and one saved and loaded keeps its place on the chain and its length
            CompoundTag saved = chain[0].saveWithoutId(new CompoundTag());
            HangingGutChainEntity loaded = BBEntities.HANGING_GUT_CHAIN.get().create(level);
            loaded.load(saved);
            if (loaded.links() != chain[0].links() || loaded.cursor() == null || !loaded.cursor().conveyor.equals(chain[0].cursor().conveyor)) {
                helper.fail("A saved string should come back with its length and its place on the chain");
            }
            ((CreativeMotorBlockEntity) level.getBlockEntity(a.above())).generatedSpeed.setValue(64);
        });
        for (int t = 40; t < 200; t++) {
            helper.runAfterDelay(t, () -> {
                if (chain[0] != null && !chain[0].isRemoved()) {
                    range[0] = Math.min(range[0], chain[0].getX());
                    range[1] = Math.max(range[1], chain[0].getX());
                }
            });
        }
        helper.runAfterDelay(200, () -> {
            if (chain[0] == null || chain[0].isRemoved()) {
                helper.fail("The string fell off the chain");
                return;
            }
            if (range[1] - range[0] < 3.0) {
                helper.fail("The string should ride the moving chain, it only went " + range[0] + ".." + range[1]);
            }
            Vec3 at = chain[0].position();
            chain[0].hurt(level.damageSources().playerAttack(player), 1.0F);
            if (!chain[0].isRemoved()) {
                helper.fail("Hitting the string should take it down");
            }
            int links = level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(1.0, 9.0, 1.0), e -> e.getItem().is(BBBlocks.GUT_CHAIN.asItem()))
                    .stream().mapToInt(e -> e.getItem().getCount()).sum();
            if (links != HangingGutChainEntity.MAX_LINKS) {
                helper.fail("Taken down, it should drop its eight links, dropped " + links);
            }
            clearDrops(helper);
            helper.succeed();
        });
    }

    /**
     * A string hung across a section line (the game files entities by 16-block sections) is found by a player
     * aiming at its lower end, below the line, as the game's own aim finds entities: more Gut Chain lengthens
     * it and a hit takes it down. The string's own box, filed in the section above, is not found from there.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void gutChainIsHitBelowASectionLine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(5, 0, 5));
        // the chain two and a half blocks above the next section line up, so seven links hang well below it
        int line = (Math.floorDiv(base.getY(), 16) + 1) * 16;
        Vec3 top = new Vec3(base.getX() + 0.5, line + 2.5, base.getZ() + 0.5);
        HangingGutChainEntity chain = BBEntities.HANGING_GUT_CHAIN.get().create(level);
        chain.setLinks(HangingGutChainEntity.MAX_LINKS - 1);
        chain.moveTo(top.x, top.y, top.z);
        level.addFreshEntity(chain);
        // a player below the line, two blocks off, looking level at the string's sixth link
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 aim = top.subtract(0, 5.5, 0);
        player.moveTo(aim.x, aim.y - player.getEyeHeight(), aim.z - 2.0);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, aim);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBBlocks.GUT_CHAIN.asItem()));
        double range = Math.max(player.blockInteractionRange(), player.entityInteractionRange());
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0F);
        AABB searched = player.getBoundingBox().expandTowards(view.scale(range)).inflate(1.0);
        String problem = null;
        if (level.getEntities((Entity) null, searched, e -> e == chain).size() != 0 || searched.maxY >= line) {
            problem = "The test should look for the string from below the section line it hangs across";
        } else {
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(view.scale(range)), searched,
                    e -> !e.isSpectator() && e.isPickable(), range * range);
            if (hit == null || !(hit.getEntity() instanceof HangingGutChainEntity.Link link) || link.getParent() != chain) {
                problem = "A player aiming at the string below a section line should find it, found " + (hit == null ? "nothing" : hit.getEntity());
            } else {
                player.interactOn(hit.getEntity(), InteractionHand.MAIN_HAND);
                if (chain.links() != HangingGutChainEntity.MAX_LINKS || !player.getMainHandItem().isEmpty()) {
                    problem = "Gut Chain used on its lower end should lengthen the string, it has " + chain.links() + " links";
                } else {
                    player.attack(hit.getEntity());
                    if (!chain.isRemoved()) {
                        problem = "Hitting its lower end should take the string down";
                    }
                }
            }
        }
        if (!chain.isRemoved()) {
            chain.discard();
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(top, top).inflate(2.0, 10.0, 2.0)).forEach(Entity::discard);
        if (problem != null) {
            helper.fail(problem);
            return;
        }
        helper.succeed();
    }

    /**
     * A client's copy of the string, fed the server's position once a tick as a client is, glides there a
     * step each tick rather than jumping; and swinging behind a fast chain, all of it stays inside the box
     * it is culled by, though it trails out of its straight-down box.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void gutChainGlidesOnClients(GameTestHelper helper) {
        HangingGutChainEntity copy = BBEntities.HANGING_GUT_CHAIN.get().create(helper.getLevel());
        copy.setLinks(HangingGutChainEntity.MAX_LINKS);
        Vec3 start = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 12, 5)));
        copy.moveTo(start.x, start.y, start.z);
        // blocks a tick: a chain conveyor at 256 RPM
        double speed = 0.7;
        for (int t = 1; t <= 60; t++) {
            // a move packet, then the client's tick
            copy.lerpTo(start.x + speed * t, start.y, start.z, 0.0F, 0.0F, 3);
            copy.setOldPosAndRot();
            copy.clientTick();
            double step = copy.getX() - copy.xo;
            if (t > 20 && Math.abs(step - speed) > 0.05) {
                helper.fail("A client's copy should move smoothly at the chain's speed, it moved " + step + " on tick " + t);
                return;
            }
            AABB culled = copy.cullingBox();
            for (float partialTick : new float[]{0.0F, 0.5F, 1.0F}) {
                for (Vec3 joint : copy.drawnJoints(partialTick)) {
                    if (!culled.contains(joint)) {
                        helper.fail("The string is drawn outside the box it is culled by, at " + joint + " on tick " + t);
                        return;
                    }
                }
            }
        }
        Vec3[] joints = copy.drawnJoints(1.0F);
        if (copy.getBoundingBox().inflate(1.0).contains(joints[joints.length - 1])) {
            helper.fail("Behind a fast chain the string should trail out of its straight-down box, its end is at " + joints[joints.length - 1]);
            return;
        }
        helper.succeed();
    }

    /**
     * On a Create contraption, the new blocks move and keep what they hold: a Mechanical Piston pushes a
     * table with an item on it, a rack with things on its shelves, a rib, both bloody casings, a bone pile
     * lying on one of them and a stone the pile pushes in front of it (with a single layer of bones on top,
     * which has no collision) two blocks along, and they are set down whole.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void decorationRidesAContraption(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int y = 3;
        int z = 5;
        for (int x = 0; x <= 1; x++) {
            helper.setBlock(new BlockPos(x, y, z), AllBlocks.PISTON_EXTENSION_POLE.getDefaultState().setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.EAST));
        }
        helper.setBlock(new BlockPos(2, y, z), AllBlocks.MECHANICAL_PISTON.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST).setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
        helper.setBlock(new BlockPos(2, y - 1, z), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        helper.setBlock(new BlockPos(3, y, z), BBBlocks.STEEL_TABLE.getDefaultState());
        helper.setBlock(new BlockPos(4, y, z), BBBlocks.STEEL_RACK.getDefaultState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(new BlockPos(5, y, z), BBBlocks.RIBCAGE_ARCH.getDefaultState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(new BlockPos(6, y, z), BBBlocks.BLOODY_BRASS_CASING.getDefaultState());
        helper.setBlock(new BlockPos(7, y, z), BBBlocks.BLOODY_COPPER_CASING.getDefaultState());
        // a pile the stone in front of it runs into unless the pile pushes it (Create would count a brittle
        // block as holding nothing up on any side), and a single layer on that stone
        helper.setBlock(new BlockPos(6, y + 1, z), BBBlocks.BONE_PILE.getDefaultState().setValue(BonePileBlock.LAYERS, 3));
        helper.setBlock(new BlockPos(7, y + 1, z), Blocks.STONE);
        helper.setBlock(new BlockPos(7, y + 2, z), BBBlocks.BONE_PILE.getDefaultState().setValue(BonePileBlock.LAYERS, 1));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(8, 2, 1));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        if (carcass == null) {
            helper.fail("Carcass assembly returned null");
            return;
        }
        ItemStack head = CarcassPieceItem.of(carcass, "head");
        ((SteelTableBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(3, y, z)))).put(head.copy());
        SteelRackBlockEntity rack = (SteelRackBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(4, y, z)));
        rack.put(0, new ItemStack(Items.DIAMOND));
        rack.put(3, new ItemStack(BBItems.OFFAL.get()));
        helper.runAfterDelay(2, () -> ((CreativeMotorBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(2, y - 1, z)))).generatedSpeed.setValue(-64));
        helper.runAfterDelay(60, () -> {
            // the other way round, in case this motor turns the piston back rather than out
            if (helper.getBlockState(new BlockPos(3, y, z)).is(BBBlocks.STEEL_TABLE.get())) {
                ((CreativeMotorBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(2, y - 1, z)))).generatedSpeed.setValue(64);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(BBBlocks.STEEL_TABLE.get(), new BlockPos(5, y, z));
            helper.assertBlockPresent(BBBlocks.STEEL_RACK.get(), new BlockPos(6, y, z));
            helper.assertBlockPresent(BBBlocks.RIBCAGE_ARCH.get(), new BlockPos(7, y, z));
            helper.assertBlockPresent(BBBlocks.BLOODY_BRASS_CASING.get(), new BlockPos(8, y, z));
            helper.assertBlockProperty(new BlockPos(8, y + 1, z), BonePileBlock.LAYERS, 3);
            helper.assertBlockPresent(BBBlocks.BLOODY_COPPER_CASING.get(), new BlockPos(9, y, z));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(9, y + 1, z));
            helper.assertBlockProperty(new BlockPos(9, y + 2, z), BonePileBlock.LAYERS, 1);
            helper.assertBlockNotPresent(BBBlocks.BONE_PILE.get(), new BlockPos(6, y + 1, z));
            helper.assertBlockNotPresent(BBBlocks.BONE_PILE.get(), new BlockPos(7, y + 2, z));
            SteelTableBlockEntity table = (SteelTableBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(5, y, z)));
            SteelRackBlockEntity moved = (SteelRackBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(6, y, z)));
            helper.assertTrue(table != null && ItemStack.isSameItemSameComponents(table.specimen(), head), "the table lost its piece on the way");
            helper.assertTrue(moved != null && moved.item(0).is(Items.DIAMOND) && moved.item(3).is(BBItems.OFFAL.get()), "the rack lost its things on the way");
            // nothing fell off and dropped on the way (looking in this test's own ground, not its neighbours')
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, helper.getBounds()).isEmpty(), "something dropped on the way");
        });
    }

    /** The brass and copper casings take blood from a Spout, as the andesite one does. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bloodyCladdingRecipes(GameTestHelper helper) {
        for (String metal : List.of("brass", "copper")) {
            var recipe = helper.getLevel().getRecipeManager().byKey(BloodAndBones.asResource("bloody_" + metal + "_casing_filling"));
            if (recipe.isEmpty() || !(recipe.get().value() instanceof com.simibubi.create.content.fluids.transfer.FillingRecipe filling)) {
                helper.fail("No spout filling recipe for the bloody " + metal + " casing");
                return;
            }
            ItemStack out = filling.getResultItem(helper.getLevel().registryAccess());
            if (!out.is(("brass".equals(metal) ? BBBlocks.BLOODY_BRASS_CASING : BBBlocks.BLOODY_COPPER_CASING).asItem())
                    || filling.getRequiredFluid().amount() != 250
                    || !filling.getIngredients().get(0).test(new ItemStack("brass".equals(metal) ? AllBlocks.BRASS_CASING.asItem() : AllBlocks.COPPER_CASING.asItem()))) {
                helper.fail("The bloody " + metal + " casing should come from its casing and 250 mB of blood");
            }
        }
        helper.succeed();
    }
}
