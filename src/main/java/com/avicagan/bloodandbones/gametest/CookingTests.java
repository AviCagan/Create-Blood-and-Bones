package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.cooking.SpecimenJarBlockEntity;
import com.avicagan.bloodandbones.cooking.SpitRoastBlockEntity;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class CookingTests {
    /** A cow's body as a carried piece. */
    private static ItemStack cowBody(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(8, 2, 8));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        if (carcass == null) {
            helper.fail("Carcass assembly returned false");
        }
        return CarcassPieceItem.of(carcass, "body");
    }

    /** Over a lit campfire and turning, a piece cooks; done, it comes apart into cooked beef. Unturned, it does not cook. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void spitRoastCooksOverFire(GameTestHelper helper) {
        BlockPos fire = new BlockPos(3, 2, 3);
        BlockPos spit = fire.above();
        BlockPos motor = spit.west();
        helper.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState());
        helper.setBlock(spit, BBBlocks.SPIT_ROAST.getDefaultState().setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS, Direction.Axis.X));
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        // a second spit with no shaft: it must not cook
        BlockPos stillFire = new BlockPos(6, 2, 3);
        helper.setBlock(stillFire, Blocks.CAMPFIRE.defaultBlockState());
        helper.setBlock(stillFire.above(), BBBlocks.SPIT_ROAST.getDefaultState().setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS, Direction.Axis.X));
        if (helper.getLevel().getBlockEntity(helper.absolutePos(motor)) instanceof CreativeMotorBlockEntity creative) {
            creative.generatedSpeed.setValue(64);
        }
        ItemStack piece = cowBody(helper);
        SpitRoastBlockEntity roast = (SpitRoastBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(spit));
        SpitRoastBlockEntity still = (SpitRoastBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(stillFire.above()));
        if (!roast.skewer(piece.copy()) || !still.skewer(piece.copy())) {
            helper.fail("Could not skewer the piece");
        }
        helper.runAfterDelay(40, () -> {
            if (roast.progress <= 0) {
                helper.fail("The turning spit over a fire is not cooking (speed " + roast.getSpeed() + ", heat " + roast.heat() + ")");
            }
            if (still.progress > 0) {
                helper.fail("A spit that does not turn should not cook");
            }
            roast.progress = roast.cookTime();
            List<ItemStack> out = roast.cookedYields(helper.getLevel());
            int cooked = out.stream().filter(s -> s.is(Items.COOKED_BEEF)).mapToInt(ItemStack::getCount).sum();
            int raw = out.stream().filter(s -> s.is(Items.BEEF)).mapToInt(ItemStack::getCount).sum();
            if (cooked < 3 || raw > 0) {
                helper.fail("A cooked cow body should give cooked beef, got " + out);
            }
            roast.progress = 2 * roast.cookTime();
            out = roast.cookedYields(helper.getLevel());
            if (out.stream().noneMatch(s -> s.is(Items.CHARCOAL)) || out.stream().anyMatch(s -> s.is(Items.COOKED_BEEF))) {
                helper.fail("A burnt piece should give charcoal and no meat, got " + out);
            }
            helper.succeed();
        });
    }

    /** A piece goes into a jar, stays there, and falls out when the jar is broken. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void specimenJarKeepsAPiece(GameTestHelper helper) {
        BlockPos jarPos = new BlockPos(3, 2, 3);
        helper.setBlock(jarPos, BBBlocks.SPECIMEN_JAR.getDefaultState());
        ItemStack piece = cowBody(helper);
        SpecimenJarBlockEntity jar = (SpecimenJarBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(jarPos));
        if (!jar.put(piece) || jar.put(piece.copy())) {
            helper.fail("The jar should take one piece and only one");
        }
        helper.runAfterDelay(5, () -> {
            helper.getLevel().destroyBlock(helper.absolutePos(jarPos), false);
            helper.assertItemEntityPresent(com.avicagan.bloodandbones.registry.BBItems.CARCASS_PIECE.get(), jarPos, 2.0);
            helper.succeed();
        });
    }

    /**
     * A butcher's hook hangs on a wall and holds one piece; it cannot hang on thin air, and when its wall is
     * broken it falls, dropping both itself and the piece.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void butcherHookHoldsAPieceAndFallsWithItsWall(GameTestHelper helper) {
        BlockPos wall = new BlockPos(3, 2, 3);
        BlockPos hookPos = wall.east();
        helper.setBlock(wall, Blocks.STONE.defaultBlockState());
        net.minecraft.world.level.block.state.BlockState hookState = BBBlocks.BUTCHER_HOOK.getDefaultState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.EAST);
        if (!hookState.canSurvive(helper.getLevel(), helper.absolutePos(hookPos))) {
            helper.fail("The hook should hang on a stone wall");
        }
        if (hookState.canSurvive(helper.getLevel(), helper.absolutePos(new BlockPos(6, 3, 6)))) {
            helper.fail("The hook should not hang on thin air");
        }
        helper.setBlock(hookPos, hookState);
        ItemStack piece = cowBody(helper);
        SpecimenJarBlockEntity hook = (SpecimenJarBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(hookPos));
        if (!(hook instanceof com.avicagan.bloodandbones.cooking.ButcherHookBlockEntity) || !hook.put(piece) || hook.put(piece.copy())) {
            helper.fail("The hook should take one piece and only one");
        }
        helper.runAfterDelay(5, () -> {
            helper.destroyBlock(wall);
            helper.runAfterDelay(2, () -> {
                helper.assertBlockNotPresent(BBBlocks.BUTCHER_HOOK.get(), hookPos);
                helper.assertItemEntityPresent(com.avicagan.bloodandbones.registry.BBItems.CARCASS_PIECE.get(), hookPos, 2.0);
                helper.assertItemEntityPresent(BBBlocks.BUTCHER_HOOK.asItem(), hookPos, 2.0);
                helper.succeed();
            });
        });
    }

    /**
     * On a Create contraption both hooks go with the block they hang from and are brittle, and a wall
     * Shackle Hook turned with its wall stays on that wall.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hooksRideContraptions(GameTestHelper helper) {
        net.minecraft.world.level.block.state.BlockState butcher = BBBlocks.BUTCHER_HOOK.getDefaultState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.EAST);
        net.minecraft.world.level.block.state.BlockState shackle = BBBlocks.SHACKLE_HOOK.getDefaultState()
                .setValue(com.avicagan.bloodandbones.carcass.ShackleHookBlock.FACING, Direction.NORTH);
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        var level = helper.getLevel();
        if (!com.simibubi.create.api.contraption.BlockMovementChecks.isBlockAttachedTowards(butcher, level, pos, Direction.WEST)
                || com.simibubi.create.api.contraption.BlockMovementChecks.isBlockAttachedTowards(butcher, level, pos, Direction.EAST)) {
            helper.fail("A butcher's hook pointing east should be attached to the block west of it, and only that one");
        }
        if (!com.simibubi.create.api.contraption.BlockMovementChecks.isBlockAttachedTowards(shackle, level, pos, Direction.NORTH)
                || com.simibubi.create.api.contraption.BlockMovementChecks.isBlockAttachedTowards(shackle, level, pos, Direction.UP)) {
            helper.fail("A shackle hook on a north wall should be attached to that wall, and only that one");
        }
        if (!com.simibubi.create.api.contraption.BlockMovementChecks.isBrittle(butcher) || !com.simibubi.create.api.contraption.BlockMovementChecks.isBrittle(shackle)) {
            helper.fail("Both hooks should be brittle on a contraption");
        }
        if (shackle.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90).getValue(com.avicagan.bloodandbones.carcass.ShackleHookBlock.FACING) != Direction.EAST) {
            helper.fail("A shackle hook on a north wall, turned a quarter clockwise, should be on the east wall");
        }
        helper.succeed();
    }

    /**
     * Carcass pieces in Create's Attribute Filter: a fresh cow body offers "a piece of cow", "a carcass
     * body" and "fresh meat"; a pig filter or a head filter does not take it; rotted, it counts as rotting;
     * and each attribute survives being saved in a filter.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void carcassPiecesInAttributeFilters(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack body = cowBody(helper);
        List<com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute> offered =
                com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.getAllAttributes(body, level);
        var cow = new com.avicagan.bloodandbones.registry.BBItemAttributes.PieceOf(net.minecraft.resources.ResourceLocation.withDefaultNamespace("cow"));
        var pig = new com.avicagan.bloodandbones.registry.BBItemAttributes.PieceOf(net.minecraft.resources.ResourceLocation.withDefaultNamespace("pig"));
        var bodyPart = new com.avicagan.bloodandbones.registry.BBItemAttributes.PiecePart("body");
        var headPart = new com.avicagan.bloodandbones.registry.BBItemAttributes.PiecePart("head");
        var fresh = com.avicagan.bloodandbones.registry.BBItemAttributes.FRESH_PIECE.value().createAttribute();
        var rotting = com.avicagan.bloodandbones.registry.BBItemAttributes.ROTTING_PIECE.value().createAttribute();
        if (!offered.contains(cow) || !offered.contains(bodyPart) || !offered.contains(fresh) || offered.contains(rotting)) {
            helper.fail("A fresh cow body should offer piece of cow, carcass body and fresh meat, got " + offered);
        }
        if (!cow.appliesTo(body, level) || pig.appliesTo(body, level) || !bodyPart.appliesTo(body, level) || headPart.appliesTo(body, level)) {
            helper.fail("Mob and part filters should match only a cow body");
        }
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(body);
        ItemStack old = body.copy();
        old.set(com.avicagan.bloodandbones.registry.BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(piece.entity(), piece.bone(), piece.texture(),
                piece.coats(), 0.1F, piece.skinned(), piece.traits(), piece.blood(), piece.bloodMax(), piece.decay(), piece.baby()));
        if (!rotting.appliesTo(old, level) || fresh.appliesTo(old, level)) {
            helper.fail("A piece at 10% fresh should count as rotting and not fresh");
        }
        for (var attribute : List.of(cow, bodyPart, fresh)) {
            var saved = com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.saveStatic(attribute, level.registryAccess());
            var loaded = com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.loadStatic(saved, level.registryAccess());
            if (!attribute.equals(loaded)) {
                helper.fail("Attribute " + attribute + " came back from a filter as " + loaded);
            }
        }
        helper.succeed();
    }
}
