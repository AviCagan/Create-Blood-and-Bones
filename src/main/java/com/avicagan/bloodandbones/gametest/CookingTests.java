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
}
