package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
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
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Chasing the cut leg that fell through the ground into the void (ARCHITECTURE 15.18, item 4). Off unless
 * {@code -Dbloodandbones.debug.void=true}: these build high above their own ground, where the suite's other tests do not
 * look. {@code -Dbloodandbones.debug.void_runs=N} plays each N times.
 * <p>
 * The ground a body can hit is built by Sable only in the chunk sections near a body (PhysicsChunkTicketManager: its box
 * and one block round it), and dropped 20 ticks after no body is near. A cow hung four blocks over ground whose top is on a
 * section's border has nothing near that section, so it goes; the leg cut off it falls back into it.
 * <p>
 * Played 32 times over four ways (hung over a section's border, over a chunk's edge, and flung off at 6 and at 16 blocks
 * a second across chunks), every leg stopped on the ground: the fall was not made to happen again (ARCHITECTURE 15.30).
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class VoidLegTests {
    private static final String TEMPLATE = BloodAndBones.MOD_ID + ":empty";

    @GameTestGenerator
    public static Collection<TestFunction> voidLegs() {
        if (!Boolean.getBoolean("bloodandbones.debug.void")) {
            return List.of();
        }
        int runs = Math.max(1, Integer.getInteger("bloodandbones.debug.void_runs", 1));
        List<TestFunction> out = new ArrayList<>();
        for (int i = 0; i < runs; i++) {
            out.add(new TestFunction("void_legs", "voidleg_hung_on_border_" + i, TEMPLATE, 500, 0L, true, helper -> hungOverBorder(helper, 0.0, false)));
            out.add(new TestFunction("void_legs", "voidleg_hung_on_chunk_edge_" + i, TEMPLATE, 500, 0L, true, helper -> hungOverBorder(helper, 0.0, true)));
            out.add(new TestFunction("void_legs", "voidleg_flung_on_border_" + i, TEMPLATE, 500, 0L, true, helper -> hungOverBorder(helper, 6.0, false)));
            out.add(new TestFunction("void_legs", "voidleg_flung_far_" + i, TEMPLATE, 500, 0L, true, helper -> hungOverBorder(helper, 16.0, true)));
        }
        return out;
    }

    /**
     * A platform whose top is a section border, high over the test; a cow lies on it, is hung four blocks over it for five
     * seconds, and has a front leg cut off (flung sideways at {@code fling} blocks a second). The leg must stop on the
     * platform, or on the ground below it if flung off, never fall through.
     */
    private static void hungOverBorder(GameTestHelper helper, double fling, boolean chunkEdge) {
        ServerLevel level = helper.getLevel();
        int floor = helper.absolutePos(new BlockPos(0, 1, 0)).getY();
        // the lowest block over the floor whose top is a section border
        int top = Math.floorDiv(floor + 2 + 15, 16) * 16 - 1;
        int rel = top - helper.absolutePos(BlockPos.ZERO).getY();
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) {
                helper.setBlock(new BlockPos(x, rel, z), Blocks.GRASS_BLOCK);
            }
        }
        // over a chunk's edge: the hook where the edge runs through the platform
        int hx = 5;
        if (chunkEdge) {
            for (int x = 2; x <= 8; x++) {
                if (Math.floorMod(helper.absolutePos(new BlockPos(x, 0, 0)).getX(), 16) == 0) {
                    hx = x;
                }
            }
        }
        BlockPos hookAt = new BlockPos(hx, rel + 5, 5);
        helper.setBlock(hookAt.above(), Blocks.STONE);
        helper.setBlock(hookAt, BBBlocks.SHACKLE_HOOK.getDefaultState().setValue(ShackleHookBlock.FACING, Direction.UP));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(hx, rel + 1, 5));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        helper.assertTrue(carcass != null, "Carcass assembly returned null");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, rel + 1, 7))));
        player.setOldPosAndRot();
        helper.runAfterDelay(20, () -> {
            ServerSubLevel leg = (ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(carcass.bones.get("right_hind_leg"));
            helper.assertTrue(CarcassDrag.start(level, player, leg.getPlot().getCenterBlock(), null), "could not start dragging");
            ((ShackleHookBlockEntity) helper.getBlockEntity(hookAt)).toggle(level, player);
        });
        CarcassSavedData.Carcass[] cut = {null};
        double[] lowest = {Double.MAX_VALUE};
        helper.runAfterDelay(220, () -> {
            Vector3d at = CarcassAssembler.boneWorldPosition(level, carcass, "left_front_leg");
            helper.assertTrue(at != null, "no front leg to cut");
            cut[0] = CarcassButchery.sever(level, carcass, "left_front_leg", at);
            helper.assertTrue(cut[0] != null, "the leg did not come off");
            if (fling > 0.0) {
                ServerSubLevel leg = (ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(cut[0].bones.get(cut[0].rootBone));
                dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(leg).addLinearAndAngularVelocity(new Vector3d(fling, 2.0, fling * 0.3), new Vector3d(3, 0, 5));
            }
        });
        helper.onEachTick(() -> {
            if (cut[0] != null) {
                Vector3d at = CarcassAssembler.boneWorldPosition(level, cut[0], cut[0].rootBone);
                if (at != null) {
                    lowest[0] = Math.min(lowest[0], at.y);
                }
            }
        });
        helper.runAfterDelay(480, () -> {
            Vector3d at = CarcassAssembler.boneWorldPosition(level, cut[0], cut[0].rootBone);
            helper.assertTrue(at != null, "the cut leg is gone");
            // the ground under where it ended: the platform, the test's floor or the world's (it may be flung out of the test)
            double ground = Double.NaN;
            for (int y = (int) Math.floor(at.y); y > level.getMinBuildHeight(); y--) {
                BlockPos below = BlockPos.containing(at.x, y, at.z);
                if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                    ground = y + 1;
                    break;
                }
            }
            BloodAndBones.LOGGER.info("[voidleg] platform top {} fling {} edge {}: the leg went as low as {} and lies at {} over ground at {}",
                    top + 1, fling, chunkEdge, lowest[0], at, ground);
            helper.assertTrue(!Double.isNaN(ground) && at.y > ground - 1.0, "the cut leg fell through the ground: it lies at " + at.y + ", the ground's top is at " + ground);
            helper.succeed();
        });
    }
}
