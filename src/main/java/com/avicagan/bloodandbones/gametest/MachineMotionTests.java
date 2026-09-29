package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.trolley.ChainCursor;
import com.avicagan.bloodandbones.carcass.trolley.ShackleTrolleyEntity;
import com.avicagan.bloodandbones.cooking.SpitRoastBlockEntity;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Docs/BRIEF-AUDIT.md package 15: the machines as the brief describes them. The Guillotine "winds up under rotation,
 * drops on a redstone edge"; the Beheader is "inline, continuous"; the Spit Roast "cooks whole carcasses and limbs ...
 * much faster on a shaft".
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class MachineMotionTests {
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
     * The Guillotine winds its blade up while it turns (at 32 RPM in 40 ticks) and holds it: a pulse before then does
     * nothing, a signal held while it winds does nothing, and only a rising edge once it is armed drops it, taking one
     * limb; then it winds up again from the bottom.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void guillotineWindsUpAndDropsOnARisingEdge(GameTestHelper helper) {
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
            creative.generatedSpeed.setValue(32);
        }
        CarcassMachineBlockEntity guillotine = (CarcassMachineBlockEntity) level.getBlockEntity(helper.absolutePos(at));
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, at.above());
        if (cow == null) {
            return;
        }
        UUID id = cow.id;
        BlockPos beside = at.east();
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(guillotine.wind > 0.0F && guillotine.wind < 1.0F, "it should be winding up: " + guillotine.wind);
                    // a rising edge while it winds: nothing, and the signal stays on
                    helper.setBlock(beside, Blocks.REDSTONE_BLOCK);
                    helper.assertTrue(guillotine.falling == 0, "a pulse before it is wound up should not drop the blade");
                })
                .thenWaitUntil(() -> helper.assertTrue(guillotine.wind >= 1.0F, "not wound up yet: " + guillotine.wind))
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(guillotine.falling == 0 && guillotine.wind >= 1.0F, "a signal held on is not an edge: it should stay armed");
                    helper.assertTrue(CarcassSavedData.get(level).carcass(id).joints.size() == 5, "nothing should have been cut yet");
                    helper.assertTrue(guillotine.bladeDrop(0.0F) == 0.0F, "armed, the blade is drawn at the top of its frame");
                    helper.setBlock(beside, Blocks.STONE);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(guillotine.falling == 0, "a falling edge should not drop it");
                    helper.setBlock(beside, Blocks.REDSTONE_BLOCK);
                    helper.assertTrue(guillotine.falling == CarcassMachineBlockEntity.DROP_TICKS && guillotine.wind == 0.0F,
                            "a rising edge on an armed blade should drop it");
                })
                .thenIdle(CarcassMachineBlockEntity.DROP_TICKS + 1)
                .thenExecute(() -> {
                    CarcassSavedData.Carcass left = CarcassSavedData.get(level).carcass(id);
                    helper.assertTrue(left != null && left.joints.size() == 4, "the blade should have taken one limb");
                    helper.assertTrue(left.joints.stream().anyMatch(j -> j.child().equals("head")), "and never the head");
                    helper.assertTrue(guillotine.strokes == 1, "one cut: " + guillotine.strokes);
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(guillotine.wind > 0.0F && guillotine.wind < 1.0F && guillotine.bladeDrop(0.0F) > 0.0F,
                        "it should be winding the blade back up: " + guillotine.wind))
                .thenSucceed();
    }

    /**
     * The Beheader inline: a cow hung on a Shackle Trolley goes along a turning chain over a Beheader set under it, and its
     * head comes off as it passes, while the trolley carries the rest on.
     */
    @GameTest(template = "empty", timeoutTicks = 700)
    public static void beheaderTakesHeadsOffAPassingTrolley(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos aRel = new BlockPos(1, 6, 5);
        BlockPos bRel = new BlockPos(9, 6, 5);
        helper.setBlock(aRel, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        helper.setBlock(bRel, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        helper.setBlock(aRel.above(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.DOWN));
        // the Beheader on a motor under the middle of the chain
        BlockPos beheaderRel = new BlockPos(5, 3, 5);
        helper.setBlock(beheaderRel.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        helper.setBlock(beheaderRel, BBBlocks.BEHEADER.getDefaultState());
        BlockPos a = helper.absolutePos(aRel);
        BlockPos b = helper.absolutePos(bRel);
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, new BlockPos(2, 2, 9));
        if (cow == null) {
            return;
        }
        UUID id = cow.id;
        ShackleTrolleyEntity[] trolley = new ShackleTrolleyEntity[1];
        helper.runAfterDelay(5, () -> {
            ChainConveyorBlockEntity aBe = (ChainConveyorBlockEntity) level.getBlockEntity(a);
            ChainConveyorBlockEntity bBe = (ChainConveyorBlockEntity) level.getBlockEntity(b);
            helper.assertTrue(bBe.addConnectionTo(a) && aBe.addConnectionTo(b), "could not connect the chain conveyors");
            ((CreativeMotorBlockEntity) level.getBlockEntity(a.above())).generatedSpeed.setValue(48);
            ((CreativeMotorBlockEntity) level.getBlockEntity(helper.absolutePos(beheaderRel.below()))).generatedSpeed.setValue(64);
        });
        helper.runAfterDelay(30, () -> {
            ChainConveyorBlockEntity aBe = (ChainConveyorBlockEntity) level.getBlockEntity(a);
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(id);
            if (!(SubLevelContainer.getContainer(level).getSubLevel(carcass.bones.get(carcass.rootBone)) instanceof ServerSubLevel torso)) {
                helper.fail("No torso");
                return;
            }
            aBe.prepareStats();
            trolley[0] = ShackleTrolleyEntity.create(BBEntities.SHACKLE_TROLLEY.get(), level, new ChainCursor(a, b.subtract(a), 0.5F, aBe.reversed), carcass, torso);
            level.addFreshEntity(trolley[0]);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(trolley[0] != null && !trolley[0].isRemoved(), "no trolley yet");
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(id);
            helper.assertTrue(carcass != null, "the cow's record is gone");
            helper.assertTrue(carcass.joints.stream().noneMatch(j -> j.child().equals("head")), "the head is still on");
            helper.assertTrue(carcass.joints.size() == 4, "only the head should come off: " + carcass.joints.size() + " joints left");
            helper.assertTrue(ShackleTrolleyEntity.isHanging(level, id), "the trolley should still carry the rest");
        });
    }

    /**
     * A whole cow goes on the Spit Roast with the Meat Hook that drags it: every piece of it, its bodies gone from the
     * world, drawn whole on the spit. It takes longer than a leg, and comes off as all of its meat cooked. A whole carcass
     * taken off raw is set down again, piece by piece.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void spitRoastTakesAWholeCarcass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fire = new BlockPos(3, 2, 3);
        BlockPos spit = fire.above();
        helper.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState());
        helper.setBlock(spit, BBBlocks.SPIT_ROAST.getDefaultState().setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS, Direction.Axis.X));
        helper.setBlock(spit.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, new BlockPos(5, 2, 3));
        if (cow == null) {
            return;
        }
        UUID id = cow.id;
        helper.runAfterDelay(20, () -> {
            SpitRoastBlockEntity roast = (SpitRoastBlockEntity) level.getBlockEntity(helper.absolutePos(spit));
            // a leg alone, for how long a piece takes
            roast.skewer(CarcassPieceItem.of(cow, "left_front_leg"));
            int legTime = roast.cookTime();
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            roast.takeOff(player);
            // the cow, dragged by the hook, goes on whole
            player.moveTo(helper.absolutePos(new BlockPos(5, 2, 1)).getCenter());
            ItemStack hook = new ItemStack(BBItems.MEAT_HOOK.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, hook);
            ServerSubLevel torso = (ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(cow.bones.get(cow.rootBone));
            helper.assertTrue(CarcassDrag.start(level, player, torso.getPlot().getCenterBlock(), null), "the hook should take hold of the cow");
            BlockPos spitAt = helper.absolutePos(spit);
            level.getBlockState(spitAt).useItemOn(hook, level, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(spitAt), Direction.UP, spitAt, false));
            helper.assertTrue(CarcassSavedData.get(level).carcass(id) == null, "the cow's record should be off the world");
            helper.assertTrue(roast.whole() && roast.pieces().size() == 6, "the spit should hold all six pieces of the cow: " + roast.pieces().size());
            helper.assertTrue(CarcassDrag.current(player) == null, "the hook should let go of it");
            helper.assertTrue(roast.cookTime() > legTime * 2, "a whole cow should take far longer than a leg: " + roast.cookTime() + " / " + legTime);
            roast.progress = roast.cookTime();
            List<ItemStack> out = roast.cookedYields(level);
            int cooked = out.stream().filter(s -> s.is(Items.COOKED_BEEF)).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(cooked >= 4 && out.stream().noneMatch(s -> s.is(Items.BEEF)), "a whole cow roasted should give all its beef cooked: " + out);
            // raw, it comes off in its pieces, set down beside the spit
            int before = CarcassSavedData.get(level).all().size();
            roast.progress = 0;
            roast.takeOff(player);
            int after = CarcassSavedData.get(level).all().size();
            helper.assertTrue(roast.pieces().isEmpty() && after - before == 6, "a raw whole cow should come off as its six pieces: " + (after - before));
            helper.succeed();
        });
    }

    /**
     * The same leg on two spits over the same fire: one turned by a Hand Crank (32 RPM, kept turning), one on a shaft at
     * 256 RPM. The shaft's cooks about eight times as fast.
     */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void shaftCooksFarFasterThanACrank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos crankSpit = new BlockPos(3, 3, 3);
        BlockPos shaftSpit = new BlockPos(3, 3, 7);
        for (BlockPos spit : List.of(crankSpit, shaftSpit)) {
            helper.setBlock(spit.below(), Blocks.CAMPFIRE.defaultBlockState());
            helper.setBlock(spit, BBBlocks.SPIT_ROAST.getDefaultState().setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS, Direction.Axis.X));
        }
        helper.setBlock(crankSpit.west(), AllBlocks.HAND_CRANK.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));
        helper.setBlock(shaftSpit.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, new BlockPos(8, 2, 5));
        if (cow == null) {
            return;
        }
        SpitRoastBlockEntity cranked = (SpitRoastBlockEntity) level.getBlockEntity(helper.absolutePos(crankSpit));
        SpitRoastBlockEntity shafted = (SpitRoastBlockEntity) level.getBlockEntity(helper.absolutePos(shaftSpit));
        ((CreativeMotorBlockEntity) level.getBlockEntity(helper.absolutePos(shaftSpit.west()))).generatedSpeed.setValue(256);
        HandCrankBlockEntity crank = (HandCrankBlockEntity) level.getBlockEntity(helper.absolutePos(crankSpit.west()));
        // someone keeps turning the crank
        helper.onEachTick(() -> {
            if (helper.getTick() % 5 == 0) {
                crank.turn(false);
            }
        });
        helper.runAfterDelay(10, () -> {
            cranked.skewer(CarcassPieceItem.of(cow, "left_front_leg"));
            shafted.skewer(CarcassPieceItem.of(cow, "right_front_leg"));
        });
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(Math.abs(cranked.getSpeed()) == 32, "the crank should turn its spit at 32 RPM: " + cranked.getSpeed());
            helper.assertTrue(cranked.progress > 0, "the cranked spit should cook");
            float ratio = shafted.progress / cranked.progress;
            helper.assertTrue(ratio > 6.0F, "a shaft at 256 RPM should cook far faster than a crank: " + ratio + "x");
            helper.succeed();
        });
    }
}
