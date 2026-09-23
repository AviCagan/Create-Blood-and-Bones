package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity;
import com.avicagan.bloodandbones.bleeding.FanAirflow;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class BleedingRackTests {

    /** Tank fills only through collect(), pipes see it from the sides and bottom but not the top, and cannot fill it. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rackTankAndCapabilitySides(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(5, 2, 5);
        helper.setBlock(rel, BBBlocks.BLEEDING_RACK.getDefaultState());
        BlockPos abs = helper.absolutePos(rel);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(abs) instanceof BleedingRackBlockEntity rack)) {
                helper.fail("No rack block entity");
                return;
            }
            int in = rack.collect(new FluidStack(Fluids.WATER, 5000), FluidAction.EXECUTE);
            if (in != BleedingRackBlockEntity.CAPACITY) {
                helper.fail("collect accepted " + in + " of 5000, expected " + BleedingRackBlockEntity.CAPACITY);
            }
            if (level.getCapability(Capabilities.FluidHandler.BLOCK, abs, Direction.UP) != null) {
                helper.fail("Top face must not expose the tank");
            }
            for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
                if (level.getCapability(Capabilities.FluidHandler.BLOCK, abs, side) == null) {
                    helper.fail("No fluid handler on " + side);
                }
            }
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, abs, Direction.NORTH);
            rack.getTank().getPrimaryHandler().drain(1000, FluidAction.EXECUTE);
            if (handler.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) != 0) {
                helper.fail("Pipes must not be able to fill the rack");
            }
            FluidStack out = handler.drain(500, FluidAction.EXECUTE);
            if (out.getAmount() != 500 || rack.getFluid().getAmount() != 2500) {
                helper.fail("Drain gave " + out.getAmount() + ", left " + rack.getFluid().getAmount());
            }
            int signal = level.getBlockState(abs).getAnalogOutputSignal(level, abs);
            if (signal <= 0) {
                helper.fail("Comparator signal " + signal);
            }
            // a pipe beside the rack connects, a pipe above does not
            BlockState rackState = level.getBlockState(abs);
            if (!FluidPipeBlock.canConnectTo(level, abs, rackState, Direction.SOUTH)) {
                helper.fail("Pipe to the north does not connect");
            }
            if (FluidPipeBlock.canConnectTo(level, abs, rackState, Direction.DOWN)) {
                helper.fail("Pipe above connects but should not");
            }
            helper.succeed();
        });
    }

    /** A mechanical pump pulls the rack's fluid into a Create fluid tank. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void pumpPullsFromRack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rackRel = new BlockPos(3, 2, 5);
        BlockPos pumpRel = rackRel.east();
        BlockPos tankRel = pumpRel.east();
        BlockPos cogRel = pumpRel.north();
        BlockPos motorRel = cogRel.west();
        helper.setBlock(rackRel, BBBlocks.BLEEDING_RACK.getDefaultState());
        helper.setBlock(tankRel, AllBlocks.FLUID_TANK.getDefaultState());
        helper.setBlock(pumpRel, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(cogRel, AllBlocks.COGWHEEL.getDefaultState().setValue(CogWheelBlock.AXIS, Direction.Axis.X));
        helper.setBlock(motorRel, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        BlockPos rackAbs = helper.absolutePos(rackRel);
        BlockPos tankAbs = helper.absolutePos(tankRel);
        helper.runAfterDelay(2, () -> {
            if (level.getBlockEntity(helper.absolutePos(motorRel)) instanceof CreativeMotorBlockEntity motor) {
                motor.generatedSpeed.setValue(128);
            }
            ((BleedingRackBlockEntity) level.getBlockEntity(rackAbs)).collect(new FluidStack(Fluids.WATER, 4000), FluidAction.EXECUTE);
        });
        helper.runAfterDelay(300, () -> {
            IFluidHandler tank = level.getCapability(Capabilities.FluidHandler.BLOCK, tankAbs, null);
            int moved = tank == null ? -1 : tank.getFluidInTank(0).getAmount();
            int left = ((BleedingRackBlockEntity) level.getBlockEntity(rackAbs)).getFluid().getAmount();
            BloodAndBones.LOGGER.info("[rack test] pump moved {} mB, rack has {} mB", moved, left);
            if (moved <= 0 || left >= 4000) {
                helper.fail("Pump moved " + moved + " mB, rack still has " + left);
            }
            helper.succeed();
        });
    }

    /** fanSpeedAt: in front of a spinning fan yes, beside or behind no, past a solid block no, over the rack yes. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fanSpeedAtFollowsTheAirCurrent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos motorRel = new BlockPos(1, 2, 5);
        BlockPos fanRel = motorRel.east();
        helper.setBlock(motorRel, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(fanRel, AllBlocks.ENCASED_FAN.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(fanRel.east(2), BBBlocks.BLEEDING_RACK.getDefaultState()); // air passes over the 8 px tray
        helper.setBlock(fanRel.east(6), Blocks.STONE.defaultBlockState());
        helper.runAfterDelay(2, () -> {
            if (level.getBlockEntity(helper.absolutePos(motorRel)) instanceof CreativeMotorBlockEntity motor) {
                motor.generatedSpeed.setValue(64);
            }
        });
        helper.runAfterDelay(60, () -> {
            float front = FanAirflow.fanSpeedAt(level, helper.absolutePos(fanRel.east(1)));
            float rack = FanAirflow.fanSpeedAt(level, helper.absolutePos(fanRel.east(2)));
            float pastRack = FanAirflow.fanSpeedAt(level, helper.absolutePos(fanRel.east(4)));
            float beforeStone = FanAirflow.fanSpeedAt(level, helper.absolutePos(fanRel.east(5)));
            float stone = FanAirflow.fanSpeedAt(level, helper.absolutePos(fanRel.east(6)));
            float pastStone = FanAirflow.fanSpeedAt(level, helper.absolutePos(fanRel.east(7)));
            float beside = FanAirflow.fanSpeedAt(level, helper.absolutePos(fanRel.east(2).north()));
            float behind = FanAirflow.fanSpeedAt(level, helper.absolutePos(motorRel.west()));
            BloodAndBones.LOGGER.info("[fan test] front={} rack={} pastRack={} beforeStone={} stone={} pastStone={} beside={} behind={}",
                    front, rack, pastRack, beforeStone, stone, pastStone, beside, behind);
            if (front != 64 || rack != 64 || pastRack != 64 || beforeStone != 64) {
                helper.fail("Expected 64 RPM in the current: front=" + front + " rack=" + rack + " pastRack=" + pastRack + " beforeStone=" + beforeStone);
            }
            if (stone != 0 || pastStone != 0 || beside != 0 || behind != 0) {
                helper.fail("Expected no air: stone=" + stone + " pastStone=" + pastStone + " beside=" + beside + " behind=" + behind);
            }
            helper.succeed();
        });
    }
}
