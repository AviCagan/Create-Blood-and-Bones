package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.bleeding.BloodStainBlock;
import com.avicagan.bloodandbones.carcass.Blood;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.ShackleHookBlock;
import com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBItems;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class BloodStainTests {

    /**
     * Blood falls to the first solid top below and stains it; more makes the stain bigger; grass takes none;
     * left alone a stain dries darker, shrinks and goes.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void bloodStainsPoolAndDry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 high = helper.absoluteVec(new Vec3(2.5, 5.5, 2.5));
        Blood.stain(level, new Vector3d(high.x, high.y, high.z), 1);
        BlockPos floor = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState stain = level.getBlockState(floor);
        if (!stain.is(BBBlocks.BLOOD_STAIN.get())) {
            helper.fail("No stain on the floor below, found " + stain);
            return;
        }
        Blood.stain(level, new Vector3d(high.x, high.y, high.z), 3);
        if (level.getBlockState(floor).getValue(BloodStainBlock.SIZE) <= stain.getValue(BloodStainBlock.SIZE)) {
            helper.fail("More blood should make the stain bigger");
        }
        helper.setBlock(new BlockPos(5, 1, 2), Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(5, 2, 2), Blocks.SHORT_GRASS);
        Vec3 overGrass = helper.absoluteVec(new Vec3(5.5, 5.5, 2.5));
        Blood.stain(level, new Vector3d(overGrass.x, overGrass.y, overGrass.z), 2);
        if (!level.getBlockState(helper.absolutePos(new BlockPos(5, 2, 2))).is(Blocks.SHORT_GRASS)) {
            helper.fail("Blood should not replace grass");
        }
        // age it by hand: random ticks are too slow to wait for
        RandomSource random = RandomSource.create(1);
        boolean dried = false;
        for (int i = 0; i < 400 && level.getBlockState(floor).is(BBBlocks.BLOOD_STAIN.get()); i++) {
            BlockState now = level.getBlockState(floor);
            dried |= now.getValue(BloodStainBlock.AGE) == 2;
            now.randomTick(level, floor, random);
        }
        if (level.getBlockState(floor).is(BBBlocks.BLOOD_STAIN.get())) {
            helper.fail("A stain left alone should go in the end");
        }
        if (!dried && !level.isRainingAt(floor.above())) {
            helper.fail("A stain should dry before it goes");
        }
        helper.succeed();
    }

    /** A cow hanging with nothing under it bleeds onto the floor; a hanging skeleton leaves nothing. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void hangingCarcassStainsTheFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(2, 2, 8));
        CarcassSavedData.Carcass bones = CarcassAssembler.assemble(skeleton, null);
        skeleton.discard();
        if (carcass == null || bones == null) {
            helper.fail("Carcass assembly returned null");
            return;
        }
        if (Blood.bloody(bones) || !Blood.bloody(carcass)) {
            helper.fail("A skeleton has no blood and a cow does");
        }
        helper.setBlock(new BlockPos(5, 7, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 6, 5), BBBlocks.SHACKLE_HOOK.get().defaultBlockState().setValue(ShackleHookBlock.FACING, Direction.UP));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))));
        player.setOldPosAndRot();
        helper.runAfterDelay(10, () -> {
            if (!(SubLevelContainer.getContainer(level).getSubLevel(carcass.bones.get("right_hind_leg")) instanceof ServerSubLevel leg)) {
                helper.fail("No leg");
                return;
            }
            if (!CarcassDrag.start(level, player, leg.getPlot().getCenterBlock(), null)) {
                helper.fail("Could not start dragging");
            }
            ((ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 6, 5)))).toggle(level, player);
        });
        helper.runAfterDelay(200, () -> {
            int stains = 0;
            for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(10, 2, 10)))) {
                if (level.getBlockState(pos).is(BBBlocks.BLOOD_STAIN.get())) {
                    stains++;
                }
            }
            if (stains == 0) {
                helper.fail("A cow bleeding with no rack under it left no blood on the floor (body has " + carcass.blood + " of " + carcass.bloodMax + ")");
            }
            helper.succeed();
        });
    }

    /**
     * A fresh cut over Bleeding Racks pours into them instead of onto the floor; a skeleton's cut arm shows
     * no raw wound.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void freshCutPoursIntoARack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 2; x <= 8; x++) {
            for (int z = 2; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), BBBlocks.BLEEDING_RACK.getDefaultState());
            }
        }
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(9, 2, 1));
        CarcassSavedData.Carcass bones = CarcassAssembler.assemble(skeleton, null);
        skeleton.discard();
        if (carcass == null || bones == null) {
            helper.fail("Carcass assembly returned null");
            return;
        }
        helper.runAfterDelay(10, () -> {
            com.avicagan.bloodandbones.carcass.CarcassButchery.sever(level, carcass, "right_front_leg", null);
            com.avicagan.bloodandbones.carcass.CarcassButchery.sever(level, bones, "right_arm", null);
            if (!com.avicagan.bloodandbones.carcass.CarcassRot.cuts(bones).isEmpty()) {
                helper.fail("A skeleton's cut ends should be dry, got " + com.avicagan.bloodandbones.carcass.CarcassRot.cuts(bones));
            }
        });
        helper.runAfterDelay(160, () -> {
            int caught = 0;
            for (int x = 2; x <= 8; x++) {
                for (int z = 2; z <= 8; z++) {
                    if (level.getBlockEntity(helper.absolutePos(new BlockPos(x, 1, z))) instanceof com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity rack) {
                        caught += rack.getFluid().getAmount();
                    }
                }
            }
            if (caught <= 0) {
                helper.fail("A fresh cut over racks should pour into them");
            }
            helper.succeed();
        });
    }
}
