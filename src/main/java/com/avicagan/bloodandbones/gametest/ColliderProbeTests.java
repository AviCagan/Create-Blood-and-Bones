package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassPartBlock;
import com.avicagan.bloodandbones.registry.BBBlocks;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;

/**
 * Drops a single carcass cell onto the floor and reports where it settles, to check that the physics box
 * sits where the logical block sits.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class ColliderProbeTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void probeFullCubeIdentity(GameTestHelper helper) {
        probe(helper, 16, new Quaterniond(), "full cube, identity");
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void probeSmallCubeFlipped(GameTestHelper helper) {
        probe(helper, 4, new Quaterniond().rotationX(Math.PI), "4px cube, 180 about X");
    }

    /** A small impulse along the body's local +x moves the cube along world +x; the API takes impulses in the body frame. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void probeImpulseIsLocalFrame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos staging = new BlockPos(floor.getX(), level.getMaxBuildHeight() - 6, floor.getZ());
        level.setBlock(staging, CarcassPartBlock.stateFor(BBBlocks.CARCASS_PART.get(), 8, 8, 8), Block.UPDATE_ALL);
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, staging, List.of(staging), new BoundingBox3i(staging, staging));
        if (subLevel == null || subLevel.isRemoved()) {
            helper.fail("assembly failed");
            return;
        }
        SubLevelPhysicsSystem physics = SubLevelContainer.getContainer(level).physicsSystem();
        Vector3d startCom = new Vector3d(floor.getX() + 0.5, floor.getY() + 0.25 + 0.01, floor.getZ() + 0.5);
        // body rotated 90 degrees about Y: local +x points along world -z
        subLevel.logicalPose().orientation().set(new Quaterniond().rotationY(Math.PI / 2.0));
        subLevel.logicalPose().position().set(startCom);
        physics.getPipeline().teleport(subLevel, subLevel.logicalPose().position(), subLevel.logicalPose().orientation());
        subLevel.updateLastPose();
        BlockPos c = subLevel.getPlot().getCenterBlock();
        Vector3d comPlot = new Vector3d(c.getX() + 0.25, c.getY() + 0.25, c.getZ() + 0.25);
        helper.runAfterDelay(5, () -> physics.getPhysicsHandle(subLevel).applyImpulseAtPoint(comPlot, new Vector3d(0.25, 0.0, 0.0)));
        helper.runAfterDelay(25, () -> {
            Vector3d pos = subLevel.logicalPose().position();
            double dx = pos.x - startCom.x;
            double dz = pos.z - startCom.z;
            BloodAndBones.LOGGER.info(String.format("[probe] impulse: moved x=%.3f z=%.3f", dx, dz));
            if (dz > -0.1 || Math.abs(dx) > 0.05) {
                helper.fail("Expected motion along world -z from a local +x impulse, got dx=" + dx + " dz=" + dz);
            }
            if (subLevel.logicalPose().position().distance(startCom) > 3.0) {
                helper.fail("Cube travelled too far for a 0.25 impulse");
            }
            helper.succeed();
        });
    }

    private static void probe(GameTestHelper helper, int size, Quaterniond orientation, String label) {
        ServerLevel level = helper.getLevel();
        // (2,1,2) is the stone floor itself; its top surface is at the y of the air block above it.
        BlockPos floor = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos staging = new BlockPos(floor.getX(), level.getMaxBuildHeight() - 6, floor.getZ());
        level.setBlock(staging, CarcassPartBlock.stateFor(BBBlocks.CARCASS_PART.get(), size, size, size), Block.UPDATE_ALL);
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, staging, List.of(staging), new BoundingBox3i(staging, staging));
        if (subLevel == null || subLevel.isRemoved()) {
            helper.fail("assembly failed");
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevelPhysicsSystem physics = container.physicsSystem();
        // logical box: [center block corner, + size/16]; COM at half size. Put the box bottom 1.0 above the floor.
        double half = size / 32.0;
        Vector3d targetCom = new Vector3d(floor.getX() + 0.5, floor.getY() + 1.0 + half, floor.getZ() + 0.5);
        subLevel.logicalPose().orientation().set(orientation);
        subLevel.logicalPose().position().set(targetCom);
        physics.getPipeline().teleport(subLevel, subLevel.logicalPose().position(), subLevel.logicalPose().orientation());
        subLevel.updateLastPose();
        double floorTop = floor.getY();
        BloodAndBones.LOGGER.info(String.format("[probe] %s: start com y above floor = %.3f (box bottom %.3f)", label, targetCom.y - floorTop, targetCom.y - half - floorTop));
        helper.runAfterDelay(60, () -> {
            Vector3d pos = subLevel.logicalPose().position();
            double bottom = pos.y - half - floorTop;
            BloodAndBones.LOGGER.info(String.format("[probe] %s: settled with box bottom %.3f above the floor", label, bottom));
            if (Math.abs(bottom) > 0.05) {
                helper.fail(label + ": physics box does not rest on the floor, bottom is at " + bottom);
            }
            helper.succeed();
        });
    }
}
