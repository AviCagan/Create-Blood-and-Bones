package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.avicagan.bloodandbones.carcass.trolley.ChainCursor;
import com.avicagan.bloodandbones.carcass.trolley.ShackleTrolleyEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.UUID;

@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class TrolleyTests {

    /** A carcass hung on a trolley follows a powered chain: along the strand, round the far wheel and back. */
    @GameTest(template = "empty", timeoutTicks = 900)
    public static void trolleyCarriesCarcassAlongChain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos aRel = new BlockPos(1, 6, 5);
        BlockPos bRel = new BlockPos(9, 6, 5);
        helper.setBlock(aRel, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        helper.setBlock(bRel, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        helper.setBlock(aRel.above(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.DOWN));
        BlockPos a = helper.absolutePos(aRel);
        BlockPos b = helper.absolutePos(bRel);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(4, 2, 5));
        CarcassSavedData.Carcass mine = CarcassAssembler.assemble(cow, null);
        if (mine == null) {
            helper.fail("Carcass assembly returned null");
            return;
        }
        java.util.UUID mineId = mine.id;
        cow.discard();

        ShackleTrolleyEntity[] trolley = new ShackleTrolleyEntity[1];
        double[] minX = {Double.MAX_VALUE};
        double[] maxX = {-Double.MAX_VALUE};
        double[] worstGap = {0};
        boolean[] looped = {false};
        boolean[] cameBack = {false};
        double[] worstJump = {0};
        Vec3[] lastAnchor = {null};
        int[] flipTick = {-1};

        helper.runAfterDelay(5, () -> {
            ChainConveyorBlockEntity aBe = (ChainConveyorBlockEntity) level.getBlockEntity(a);
            ChainConveyorBlockEntity bBe = (ChainConveyorBlockEntity) level.getBlockEntity(b);
            if (!bBe.addConnectionTo(a) || !aBe.addConnectionTo(b)) {
                helper.fail("Could not connect the chain conveyors");
            }
            CreativeMotorBlockEntity motor = (CreativeMotorBlockEntity) level.getBlockEntity(a.above());
            motor.generatedSpeed.setValue(64);
        });

        helper.runAfterDelay(30, () -> {
            ChainConveyorBlockEntity aBe = (ChainConveyorBlockEntity) level.getBlockEntity(a);
            ChainConveyorBlockEntity bBe = (ChainConveyorBlockEntity) level.getBlockEntity(b);
            BloodAndBones.LOGGER.info("[trolley] speeds a={} b={} reversed={} connections={}", aBe.getSpeed(), bBe.getSpeed(), aBe.reversed, aBe.connections);
            if (aBe.getSpeed() == 0 || bBe.getSpeed() == 0) {
                helper.fail("Chain conveyors are not turning: " + aBe.getSpeed() + " / " + bBe.getSpeed());
            }
            // other tests run alongside this one: take this test's own cow
            CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(mineId);
            if (carcass == null) {
                helper.fail("No carcass");
                return;
            }
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            SubLevel torso = container.getSubLevel(carcass.bones.get(carcass.rootBone));
            if (!(torso instanceof ServerSubLevel serverTorso)) {
                helper.fail("No torso");
                return;
            }
            aBe.prepareStats();
            ChainCursor cursor = new ChainCursor(a, b.subtract(a), 0.5f, aBe.reversed);
            trolley[0] = ShackleTrolleyEntity.create(BBEntities.SHACKLE_TROLLEY.get(), level, cursor, carcass, serverTorso);
            level.addFreshEntity(trolley[0]);
            BloodAndBones.LOGGER.info("[trolley] start at {}", trolley[0].position());
        });

        for (int t = 31; t < 700; t++) {
            int tick = t;
            helper.runAfterDelay(t, () -> {
                ShackleTrolleyEntity trolleyEntity = trolley[0];
                if (trolleyEntity == null || trolleyEntity.isRemoved()) {
                    helper.fail("Trolley gone at tick " + tick);
                    return;
                }
                ChainCursor cursor = trolleyEntity.cursor();
                Vec3 anchor = trolleyEntity.anchor();
                if (cursor == null || anchor == null) {
                    return;
                }
                if (lastAnchor[0] != null) {
                    double jump = anchor.distanceTo(lastAnchor[0]);
                    if (jump > worstJump[0]) {
                        worstJump[0] = jump;
                        BloodAndBones.LOGGER.info("[trolley] new worst per-tick anchor step {} at t={} conn={} pos={}", jump, tick, cursor.connection, cursor.position);
                    }
                }
                lastAnchor[0] = anchor;
                // reverse the network while the trolley is part-way along a strand
                if (flipTick[0] < 0 && tick > 250 && cursor.connection != null && cursor.position > 2.0f) {
                    flipTick[0] = tick;
                    CreativeMotorBlockEntity motor = (CreativeMotorBlockEntity) level.getBlockEntity(a.above());
                    motor.generatedSpeed.setValue(-48);
                    BloodAndBones.LOGGER.info("[trolley] reversing the motor at t={} conveyor={} conn={} pos={}", tick,
                            cursor.conveyor.subtract(helper.absolutePos(BlockPos.ZERO)), cursor.connection, cursor.position);
                }
                if (flipTick[0] > 0 && tick <= flipTick[0] + 6) {
                    ChainConveyorBlockEntity cur = (ChainConveyorBlockEntity) level.getBlockEntity(cursor.conveyor);
                    BloodAndBones.LOGGER.info("[trolley] after flip t={} speed={} reversed={} conveyor={} conn={} pos={} flipped={} anchor={}", tick,
                            cur.getSpeed(), cur.reversed, cursor.conveyor.subtract(helper.absolutePos(BlockPos.ZERO)), cursor.connection, cursor.position, cursor.flipped, anchor);
                }
                minX[0] = Math.min(minX[0], trolleyEntity.getX());
                maxX[0] = Math.max(maxX[0], trolleyEntity.getX());
                if (cursor.connection == null && cursor.conveyor.equals(b)) {
                    looped[0] = true;
                }
                if (looped[0] && cursor.connection != null && cursor.conveyor.equals(b)) {
                    cameBack[0] = true;
                }
                CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(mineId);
                if (carcass == null) {
                    return;
                }
                UUID torsoId = carcass.bones.get(carcass.rootBone);
                SubLevel torso = SubLevelContainer.getContainer(level).getSubLevel(torsoId);
                if (!(torso instanceof ServerSubLevel body)) {
                    return;
                }
                Vector3d neck = body.logicalPose().transformPosition(trolleyEntity.anchorPlot(), new Vector3d());
                double gap = neck.distance(anchor.x, anchor.y, anchor.z);
                if (tick > 90) {
                    worstGap[0] = Math.max(worstGap[0], gap);
                }
                if (tick % 20 == 0) {
                    BloodAndBones.LOGGER.info("[trolley] t={} conveyor={} conn={} pos={} anchor={} neck=({}, {}, {}) gap={} bodyY={}",
                            tick, cursor.conveyor.subtract(helper.absolutePos(BlockPos.ZERO)), cursor.connection, cursor.position,
                            anchor, neck.x, neck.y, neck.z, gap, body.logicalPose().position().y);
                }
            });
        }

        helper.runAfterDelay(720, () -> {
            BloodAndBones.LOGGER.info("[trolley] minX={} maxX={} worstGap={} worstJump={} flipTick={} looped={} cameBack={}", minX[0], maxX[0], worstGap[0], worstJump[0], flipTick[0], looped[0], cameBack[0]);
            if (flipTick[0] < 0) {
                helper.fail("Never reversed the network");
            }
            ChainConveyorBlockEntity aBe = (ChainConveyorBlockEntity) level.getBlockEntity(a);
            if (aBe.getSpeed() <= 0) {
                helper.fail("Network did not reverse: speed " + aBe.getSpeed());
            }
            if (worstJump[0] > 0.4) {
                helper.fail("The joint target jumped " + worstJump[0] + " blocks in one tick");
            }
            if (maxX[0] - minX[0] < 4.0) {
                helper.fail("Trolley barely moved: x range " + minX[0] + ".." + maxX[0]);
            }
            if (!looped[0] || !cameBack[0]) {
                helper.fail("Trolley did not go round the far wheel and back (looped=" + looped[0] + ", back=" + cameBack[0] + ")");
            }
            if (worstGap[0] > 0.05) {
                helper.fail("Carcass neck lagged " + worstGap[0] + " blocks behind the trolley");
            }
            trolley[0].dropCarcass(level);
            helper.succeed();
        });
    }

    /** Two trolleys put on the chain almost together: the newer waits, then follows a carcass-length behind. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void trolleysQueueOnAChain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos aRel = new BlockPos(1, 6, 5);
        BlockPos bRel = new BlockPos(9, 6, 5);
        helper.setBlock(aRel, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        helper.setBlock(bRel, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        helper.setBlock(aRel.above(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.DOWN));
        BlockPos a = helper.absolutePos(aRel);
        BlockPos b = helper.absolutePos(bRel);
        UUID[] ids = new UUID[2];
        for (int i = 0; i < 2; i++) {
            Cow cow = helper.spawn(EntityType.COW, new BlockPos(3 + 3 * i, 2, 5));
            CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
            cow.discard();
            if (carcass == null) {
                helper.fail("Carcass assembly returned null");
                return;
            }
            ids[i] = carcass.id;
        }
        ShackleTrolleyEntity[] trolleys = new ShackleTrolleyEntity[2];
        double[] closest = {Double.MAX_VALUE};
        double[][] range = {{Double.MAX_VALUE, -Double.MAX_VALUE}, {Double.MAX_VALUE, -Double.MAX_VALUE}};

        helper.runAfterDelay(5, () -> {
            ChainConveyorBlockEntity aBe = (ChainConveyorBlockEntity) level.getBlockEntity(a);
            ChainConveyorBlockEntity bBe = (ChainConveyorBlockEntity) level.getBlockEntity(b);
            if (!bBe.addConnectionTo(a) || !aBe.addConnectionTo(b)) {
                helper.fail("Could not connect the chain conveyors");
            }
            ((CreativeMotorBlockEntity) level.getBlockEntity(a.above())).generatedSpeed.setValue(64);
        });
        helper.runAfterDelay(30, () -> {
            ChainConveyorBlockEntity aBe = (ChainConveyorBlockEntity) level.getBlockEntity(a);
            aBe.prepareStats();
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            for (int i = 0; i < 2; i++) {
                CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(ids[i]);
                if (carcass == null || !(container.getSubLevel(carcass.bones.get(carcass.rootBone)) instanceof ServerSubLevel torso)) {
                    helper.fail("No carcass " + i);
                    return;
                }
                // the second goes on a hair behind the first
                ChainCursor cursor = new ChainCursor(a, b.subtract(a), 1.3f - 0.3f * i, aBe.reversed);
                trolleys[i] = ShackleTrolleyEntity.create(BBEntities.SHACKLE_TROLLEY.get(), level, cursor, carcass, torso);
                level.addFreshEntity(trolleys[i]);
            }
        });
        for (int t = 31; t < 500; t++) {
            int tick = t;
            helper.runAfterDelay(t, () -> {
                if (trolleys[0] == null || trolleys[0].isRemoved() || trolleys[1].isRemoved()) {
                    helper.fail("A trolley is gone at tick " + tick);
                    return;
                }
                for (int i = 0; i < 2; i++) {
                    range[i][0] = Math.min(range[i][0], trolleys[i].getX());
                    range[i][1] = Math.max(range[i][1], trolleys[i].getX());
                }
                double gap = trolleys[0].position().distanceTo(trolleys[1].position());
                if (tick > 60) {
                    closest[0] = Math.min(closest[0], gap);
                }
                if (tick % 40 == 0) {
                    BloodAndBones.LOGGER.info("[queue] t={} gap={} first={} second={}", tick, gap, trolleys[0].position(), trolleys[1].position());
                }
            });
        }
        helper.runAfterDelay(510, () -> {
            BloodAndBones.LOGGER.info("[queue] closest={} ranges={}..{} / {}..{}", closest[0], range[0][0], range[0][1], range[1][0], range[1][1]);
            if (closest[0] < 1.2) {
                helper.fail("The trolleys came within " + closest[0] + " blocks of each other");
            }
            for (int i = 0; i < 2; i++) {
                if (range[i][1] - range[i][0] < 4.0) {
                    helper.fail("Trolley " + i + " barely moved: " + range[i][0] + ".." + range[i][1]);
                }
                trolleys[i].dropCarcass(level);
            }
            helper.succeed();
        });
    }
}
