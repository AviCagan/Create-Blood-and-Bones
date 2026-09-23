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

    /**
     * Meat hitting the ground: a cow carcass dropped from high up thuds when it lands (and a hard landing
     * splats); one built lying on the ground stays quiet.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void droppedCarcassThuds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // the test space is seven blocks high: from its top, a fall of about four
        Cow high = helper.spawn(EntityType.COW, new BlockPos(3, 6, 3));
        CarcassSavedData.Carcass falling = CarcassAssembler.assemble(high, null);
        high.discard();
        Cow low = helper.spawn(EntityType.COW, new BlockPos(7, 2, 7));
        CarcassSavedData.Carcass lying = CarcassAssembler.assemble(low, null);
        low.discard();
        if (falling == null || lying == null) {
            helper.fail("Carcass assembly returned null");
            return;
        }
        helper.runAfterDelay(120, () -> {
            if (falling.thuds == 0) {
                helper.fail("A carcass dropped four blocks should thud when it lands");
            }
            if (lying.thuds > 0) {
                helper.fail("A carcass built on the ground should not thud, it did " + lying.thuds + " times");
            }
            helper.succeed();
        });
    }

    /**
     * A carcass landing on a ship's deck thuds too. The deck is a sub-level on four corner posts, so there is
     * nothing but air in the world under the middle, where the cow comes down.
     */
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void carcassThudsOnADeck(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        java.util.List<BlockPos> deck = new java.util.ArrayList<>();
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                BlockPos at = helper.absolutePos(new BlockPos(x, 3, z));
                level.setBlock(at, Blocks.STONE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                deck.add(at);
            }
        }
        for (int[] corner : new int[][]{{2, 2}, {2, 6}, {6, 2}, {6, 6}}) {
            helper.setBlock(new BlockPos(corner[0], 2, corner[1]), Blocks.STONE);
        }
        ServerSubLevel ship = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level, deck.get(12), deck,
                new dev.ryanhcode.sable.companion.math.BoundingBox3i(deck.get(0), deck.get(deck.size() - 1)));
        if (ship == null || ship.isRemoved()) {
            helper.fail("The deck did not become a sub-level");
            return;
        }
        if (!helper.getBlockState(new BlockPos(4, 3, 4)).isAir()) {
            helper.fail("The deck's blocks should have moved into the sub-level");
            return;
        }
        // as the assembler does for a body: give it its colliders now, or it can drop through its posts
        CarcassAssembler.bindColliders(level, ship);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(4, 6, 4));
        CarcassSavedData.Carcass falling = CarcassAssembler.assemble(cow, null);
        cow.discard();
        if (falling == null) {
            helper.fail("Carcass assembly returned null");
            return;
        }
        helper.runAfterDelay(100, () -> {
            if (falling.thuds == 0) {
                helper.fail("A carcass landing on a ship's deck should thud");
            }
            helper.succeed();
        });
    }

    /** A nether mob's wound stains the ground with Soul Blood, a cow's with blood. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void hoglinStainsSoulBlood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.monster.hoglin.Hoglin hoglin = helper.spawn(EntityType.HOGLIN, new BlockPos(3, 2, 3));
        hoglin.setImmuneToZombification(true);
        CarcassSavedData.Carcass nether = CarcassAssembler.assemble(hoglin, null);
        hoglin.discard();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(7, 2, 7));
        CarcassSavedData.Carcass plain = CarcassAssembler.assemble(cow, null);
        cow.discard();
        if (nether == null || plain == null) {
            helper.fail("Carcass assembly returned null");
            return;
        }
        BlockPos soulAt = new BlockPos(1, 2, 8);
        BlockPos bloodAt = new BlockPos(8, 2, 1);
        com.avicagan.bloodandbones.carcass.Blood.wound(level, nether, new org.joml.Vector3d(
                helper.absolutePos(soulAt).getX() + 0.5, helper.absolutePos(soulAt).getY() + 0.5, helper.absolutePos(soulAt).getZ() + 0.5), 4, 1);
        com.avicagan.bloodandbones.carcass.Blood.wound(level, plain, new org.joml.Vector3d(
                helper.absolutePos(bloodAt).getX() + 0.5, helper.absolutePos(bloodAt).getY() + 0.5, helper.absolutePos(bloodAt).getZ() + 0.5), 4, 1);
        var soulStain = helper.getBlockState(soulAt);
        var bloodStain = helper.getBlockState(bloodAt);
        if (!soulStain.is(com.avicagan.bloodandbones.registry.BBBlocks.BLOOD_STAIN.get()) || !soulStain.getValue(com.avicagan.bloodandbones.bleeding.BloodStainBlock.SOUL)) {
            helper.fail("A hoglin's wound should leave a Soul Blood stain, found " + soulStain);
        }
        if (!bloodStain.is(com.avicagan.bloodandbones.registry.BBBlocks.BLOOD_STAIN.get()) || bloodStain.getValue(com.avicagan.bloodandbones.bleeding.BloodStainBlock.SOUL)) {
            helper.fail("A cow's wound should leave a blood stain, found " + bloodStain);
        }
        helper.succeed();
    }

    /** Bloodless mode's rewording: whole words only, capitals kept, this mod's keys only. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bloodlessWordsRewordText(GameTestHelper helper) {
        String[][] cases = {
                {"Bucket of Blood", "Bucket of Essence"},
                {"Bleeding Rack", "Draining Rack"},
                {"Bloody Casing", "Stained Casing"},
                {"Soul Blood", "Soul Essence"},
                {"_Bloodless_ mode keeps its name", "_Bloodless_ mode keeps its name"},
                {"Create: Blood & Bones", "Create: Blood & Bones"},
                {"A _bloody_ blade: blood-soaked meat bleeds, and bled out", "A _stained_ blade: essence-soaked meat drains, and drained out"},
        };
        for (String[] c : cases) {
            String out = com.avicagan.bloodandbones.config.BloodlessWords.soften(c[0]);
            if (!out.equals(c[1])) {
                helper.fail("\"" + c[0] + "\" should read \"" + c[1] + "\" in bloodless mode, got \"" + out + "\"");
            }
        }
        if (!com.avicagan.bloodandbones.config.BloodlessWords.reworded("block.bloodandbones.bleeding_rack")
                || com.avicagan.bloodandbones.config.BloodlessWords.reworded("itemGroup.bloodandbones.title")
                || com.avicagan.bloodandbones.config.BloodlessWords.reworded("bloodandbones.configuration.bloodless_mode.tooltip")
                || com.avicagan.bloodandbones.config.BloodlessWords.reworded("gamerule.bloodandbonesBloodless.description")
                || com.avicagan.bloodandbones.config.BloodlessWords.reworded("block.minecraft.stone")) {
            helper.fail("Only this mod's text, and not its name, should be reworded");
        }
        helper.succeed();
    }

    /**
     * A cleaver that cuts a cow comes away bloody, even from the off hand; one that cuts a skeleton does not,
     * and neither does one that strikes an armour stand.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void bladesGetBloody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 2, 3));
        CarcassSavedData.Carcass meat = CarcassAssembler.assemble(cow, null);
        cow.discard();
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(7, 2, 7));
        CarcassSavedData.Carcass bones = CarcassAssembler.assemble(skeleton, null);
        skeleton.discard();
        if (meat == null || bones == null) {
            helper.fail("Carcass assembly returned null");
            return;
        }
        Player butcher = helper.makeMockPlayer(GameType.SURVIVAL);
        Player other = helper.makeMockPlayer(GameType.SURVIVAL);
        Player lefty = helper.makeMockPlayer(GameType.SURVIVAL);
        butcher.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.CLEAVER.get()));
        other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.CLEAVER.get()));
        lefty.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.BREAD));
        lefty.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(BBItems.CLEAVER.get()));
        ItemStack standBlade = new ItemStack(BBItems.CLEAVER.get());
        net.minecraft.world.entity.decoration.ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 2, 7));
        helper.runAfterDelay(5, () -> {
            com.avicagan.bloodandbones.carcass.CarcassButchery.cut(level, butcher, meat, "right_front_leg", null);
            com.avicagan.bloodandbones.carcass.CarcassButchery.cut(level, other, bones, "right_arm", null);
            com.avicagan.bloodandbones.carcass.CarcassButchery.cut(level, lefty, meat, "left_front_leg", null);
            standBlade.getItem().hurtEnemy(standBlade, stand, other);
            if (butcher.getMainHandItem().get(com.avicagan.bloodandbones.registry.BBDataComponents.BLOODIED_AT.get()) == null) {
                helper.fail("A cleaver that cut a cow should be bloody");
            }
            if (lefty.getOffhandItem().get(com.avicagan.bloodandbones.registry.BBDataComponents.BLOODIED_AT.get()) == null) {
                helper.fail("A cleaver in the off hand that cut a cow should be bloody");
            }
            if (standBlade.get(com.avicagan.bloodandbones.registry.BBDataComponents.BLOODIED_AT.get()) != null) {
                helper.fail("A cleaver that struck an armour stand should stay clean");
            }
            if (other.getMainHandItem().get(com.avicagan.bloodandbones.registry.BBDataComponents.BLOODIED_AT.get()) != null) {
                helper.fail("A cleaver that cut a skeleton should stay clean");
            }
            helper.succeed();
        });
    }
}
