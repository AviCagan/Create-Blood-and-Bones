package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.bleeding.FanAirflow;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassRot;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import plus.dragons.createdragonsplus.common.processing.freeze.BlockFreezer;
import plus.dragons.createdragonsplus.common.registry.CDPFanProcessingTypes;

/**
 * Docs/BRIEF-AUDIT.md package 16: "cold air keeps carcasses fresh; this should play nicely with other addons' bulk
 * freezing". Create: Dragons Plus is the addon here: its freezers (and any addon's, through its freezer registry) and its
 * freezing fans.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class ColdAirTests {
    /** A block no test sets near a carcass, registered as another addon would register a freezer strong enough to freeze. */
    private static boolean testFreezerRegistered;

    private static CarcassSavedData.Carcass cow(GameTestHelper helper, BlockPos at) {
        Mob mob = helper.spawn(EntityType.COW, at);
        mob.setNoAi(true);
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(mob, null);
        mob.discard();
        if (carcass == null) {
            helper.fail("Carcass assembly returned null");
        }
        return carcass;
    }

    private static BlockPos torsoBlock(GameTestHelper helper, CarcassSavedData.Carcass carcass) {
        Vector3d at = CarcassAssembler.boneWorldPosition(helper.getLevel(), carcass, carcass.rootBone);
        return BlockPos.containing(at.x, at.y, at.z);
    }

    /**
     * An encased fan blowing through powder snow is a Dragons Plus freezing fan: its current past the snow freezes. A cow
     * lying in it, four blocks on (out of reach of the snow itself), does not rot at all; a cow in a plain fan's current
     * beside it rots as it would anywhere.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void freezingFanKeepsACarcass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cold = new BlockPos(1, 2, 2);
        BlockPos plain = new BlockPos(1, 2, 8);
        for (BlockPos motor : new BlockPos[]{cold, plain}) {
            helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
            helper.setBlock(motor.east(), AllBlocks.ENCASED_FAN.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        }
        // the freezing catalyst in front of the first fan
        helper.setBlock(cold.east(2), Blocks.POWDER_SNOW);
        CarcassSavedData.Carcass frozen = cow(helper, cold.east(7));
        CarcassSavedData.Carcass warm = cow(helper, plain.east(7));
        if (frozen == null || warm == null) {
            return;
        }
        helper.runAfterDelay(2, () -> {
            for (BlockPos motor : new BlockPos[]{cold, plain}) {
                ((CreativeMotorBlockEntity) level.getBlockEntity(helper.absolutePos(motor))).generatedSpeed.setValue(64);
            }
        });
        helper.runAfterDelay(80, () -> {
            BlockPos frozenAt = torsoBlock(helper, frozen);
            BlockPos warmAt = torsoBlock(helper, warm);
            // a cow lying on the floor: the air along the floor passes through the block under its middle
            var frozenAir = FanAirflow.processingAt(level, frozenAt.getY() > helper.absolutePos(cold).getY() ? frozenAt.below() : frozenAt);
            var warmAir = FanAirflow.processingAt(level, warmAt.getY() > helper.absolutePos(plain).getY() ? warmAt.below() : warmAt);
            BloodAndBones.LOGGER.info("[cold] frozen cow at {} in {}, warm cow at {} in {}", frozenAt, frozenAir, warmAt, warmAir);
            helper.assertTrue(frozenAir.contains(CDPFanProcessingTypes.FREEZING.get()), "the first cow should lie in a freezing current: " + frozenAir);
            helper.assertTrue(warmAir.isEmpty(), "the second cow should lie in a plain current: " + warmAir);
            helper.assertTrue(CarcassRot.rateAround(level, frozenAt) == 0.0F, "a freezing fan's current should keep a carcass from rotting");
            helper.assertTrue(CarcassRot.rateAround(level, warmAt) > 0.0F, "a plain fan's current should not");
            helper.assertTrue(frozen.rotRate == 0.0F && warm.rotRate > 0.0F, "and the carcasses' own rot should follow: " + frozen.rotRate + " / " + warm.rotRate);
            helper.succeed();
        });
    }

    /**
     * Dragons Plus's freezers: its passive freezers (ice, snow) chill a carcass beside them, and any block another addon
     * registers with Dragons Plus as a freezer strong enough to freeze stops rot outright, though none of our own tags
     * name it.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void dragonsPlusFreezersKeepACarcass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (!testFreezerRegistered) {
            testFreezerRegistered = true;
            BlockFreezer.REGISTRY.register(Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA, (world, pos, state) -> 1.0F);
        }
        CarcassSavedData.Carcass frozen = cow(helper, new BlockPos(3, 2, 3));
        CarcassSavedData.Carcass chilled = cow(helper, new BlockPos(3, 2, 8));
        CarcassSavedData.Carcass plain = cow(helper, new BlockPos(8, 2, 5));
        if (frozen == null || chilled == null || plain == null) {
            return;
        }
        helper.runAfterDelay(20, () -> {
            BlockPos frozenAt = torsoBlock(helper, frozen);
            BlockPos chilledAt = torsoBlock(helper, chilled);
            helper.getLevel().setBlockAndUpdate(frozenAt.north(2), Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(chilledAt.south(2), Blocks.ICE.defaultBlockState());
            helper.assertTrue(BlockFreezer.findFreeze(level, chilledAt.south(2), Blocks.ICE.defaultBlockState()) == BlockFreezer.PASSIVE_FREEZE,
                    "Dragons Plus should rate ice a passive freezer");
            float free = CarcassRot.rateAround(level, torsoBlock(helper, plain));
            float cool = CarcassRot.rateAround(level, chilledAt);
            float stopped = CarcassRot.rateAround(level, frozenAt);
            helper.assertTrue(free > 0.0F && Math.abs(cool - free * CarcassRot.CHILLED_RATE) < 1.0E-4F,
                    "a passive freezer beside it should quarter its rot: " + cool + " / " + free);
            helper.assertTrue(stopped == 0.0F, "an addon's freezing block beside it should stop its rot: " + stopped);
            helper.succeed();
        });
    }
}
