package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.MinionFrame;
import com.avicagan.bloodandbones.minion.MinionJob;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minions: built on the table, woken with soul blood, and doing the job their parts give them. */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class MinionTests {
    private static ItemStack piece(String entity, String bone) {
        ItemStack stack = new ItemStack(BBItems.CARCASS_PIECE.get());
        stack.set(BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(ResourceLocation.withDefaultNamespace(entity), bone,
                ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"), List.of(), 1.0F, false, Map.of(), 0.0F, 0.0F, 0.0F, false));
        return stack;
    }

    private static String root(String entity) {
        return com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(ResourceLocation.withDefaultNamespace(entity)).orElseThrow().root().name();
    }

    /** A minion with the given body, made at home by a mock player. */
    private static MinionEntity minion(GameTestHelper helper, BlockPos home, Body body) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = BBEntities.MINION.get().create(level);
        BlockPos at = helper.absolutePos(home);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(helper.makeMockPlayer(GameType.SURVIVAL), at, new MinionFrame.Frame(body, Optional.of(ResourceLocation.withDefaultNamespace("zombie"))));
        level.addFreshEntity(minion);
        BodyEffects.changed(minion);
        return minion;
    }

    /** A cow's body with a head, two arms and two legs on it, woken with soul blood, gets up a farmer. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void buildAndWake(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(3, 2, 3), BBBlocks.SURGERY_TABLE.getDefaultState());
        SurgeryTableBlockEntity table = (SurgeryTableBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(3, 2, 3)));
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        table.put(piece("cow", root("cow")));
        if (MinionFrame.wake(level, maker, table, new ItemStack(BBFluids.SOUL_BLOOD.getBucket().get())) != null) {
            helper.fail("A frame with no head should not wake");
            return;
        }
        for (ItemStack part : List.of(piece("zombie", "head"), new ItemStack(BBItems.SEVERED_ARM.get()), new ItemStack(BBItems.SEVERED_ARM.get()),
                new ItemStack(BBItems.SEVERED_LEG.get()), new ItemStack(BBItems.PEG_LEG.get()))) {
            if (!MinionFrame.fit(level, maker, table, part) || !part.isEmpty()) {
                helper.fail("The frame should take " + part + " and use it up");
                return;
            }
        }
        if (MinionFrame.fit(level, maker, table, new ItemStack(BBItems.SEVERED_ARM.get()))) {
            helper.fail("A third arm has nowhere to go");
            return;
        }
        ItemStack bucket = new ItemStack(BBFluids.SOUL_BLOOD.getBucket().get());
        maker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bucket);
        MinionEntity minion = MinionFrame.wake(level, maker, table, bucket);
        if (minion == null || !table.item().isEmpty() || !bucket.isEmpty() || maker.getInventory().countItem(Items.BUCKET) != 1) {
            helper.fail("Ready, the frame should wake, leave the table, and use the soul blood up");
            return;
        }
        Body body = BodyEffects.body(minion);
        if (minion.job() != MinionJob.FARMER || body.state(BodyPart.RIGHT_LEG) == Body.State.MISSING || body.state(BodyPart.LEFT_LEG) == Body.State.MISSING
                || body.state(BodyPart.HEART) != Body.State.NATURAL || !minion.head().map(h -> h.getPath().equals("zombie")).orElse(false)) {
            helper.fail("It should be a farmer with its legs, a heart and a zombie's head; it is a " + minion.job() + ", " + body);
            return;
        }
        helper.succeed();
    }

    /** Its parts decide its job. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void jobsFromParts(GameTestHelper helper) {
        Body fighter = new Body();
        fighter.fit(BodyPart.RIGHT_ARM, new ItemStack(BBItems.HOOK_HAND.get()));
        Body courier = new Body();
        courier.lose(BodyPart.LEFT_EYE);
        courier.lose(BodyPart.RIGHT_EYE);
        Body companion = new Body();
        companion.lose(BodyPart.LEFT_ARM);
        if (MinionJob.of(fighter, null) != MinionJob.FIGHTER || MinionJob.of(new Body(), null) != MinionJob.FARMER
                || MinionJob.of(courier, null) != MinionJob.COURIER || MinionJob.of(companion, null) != MinionJob.COMPANION) {
            helper.fail("Hook Hand: fighter; whole: farmer; no eyes: courier; one arm: companion");
            return;
        }
        helper.succeed();
    }

    /** A fighter with a Hook Hand goes for a zombie. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fighterFights(GameTestHelper helper) {
        Body body = new Body();
        body.fit(BodyPart.RIGHT_ARM, new ItemStack(BBItems.HOOK_HAND.get()));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), body);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 6));
        zombie.setNoAi(true);
        float health = zombie.getHealth();
        helper.succeedWhen(() -> helper.assertTrue(minion.job() == MinionJob.FIGHTER && (!zombie.isAlive() || zombie.getHealth() < health),
                "the fighter has not hurt the zombie yet"));
    }

    /** A courier (two arms, no eyes) carries something dropped near home into the chest there. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void courierCarries(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.CHEST);
        Body body = new Body();
        body.lose(BodyPart.LEFT_EYE);
        body.lose(BodyPart.RIGHT_EYE);
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), body);
        BlockPos drop = helper.absolutePos(new BlockPos(7, 2, 7));
        helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(), drop.getX() + 0.5, drop.getY() + 0.2, drop.getZ() + 0.5, new ItemStack(Items.BONE, 3)));
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 1));
            helper.assertTrue(minion.job() == MinionJob.COURIER && chest.countItem(Items.BONE) == 3, "the courier has not put the bones in the chest yet");
        });
    }

    /** A farmer reaps ripe wheat by home, plants it again, and puts the wheat in the chest. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void farmerReaps(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.CHEST);
        BlockPos field = new BlockPos(6, 1, 6);
        helper.setBlock(field, Blocks.FARMLAND);
        helper.setBlock(field.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), new Body());
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 1));
            var crop = helper.getBlockState(field.above());
            helper.assertTrue(minion.job() == MinionJob.FARMER && chest.countItem(Items.WHEAT) >= 1
                    && crop.is(Blocks.WHEAT) && crop.getValue(CropBlock.AGE) < 7, "the farmer has not reaped, replanted and stored the wheat yet");
        });
    }

    /** Saved and loaded, a minion keeps its maker, home, what it carries, its head and its body. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void minionSaves(GameTestHelper helper) {
        Body body = new Body();
        body.fit(BodyPart.LEFT_LEG, new ItemStack(BBItems.PEG_LEG.get()));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), body);
        minion.inventory.addItem(new ItemStack(Items.WHEAT, 5));
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        minion.saveWithoutId(tag);
        MinionEntity loaded = BBEntities.MINION.get().create(helper.getLevel());
        loaded.load(tag);
        if (!loaded.home().equals(minion.home()) || loaded.inventory.countItem(Items.WHEAT) != 5 || !loaded.head().equals(minion.head())
                || BodyEffects.body(loaded).state(BodyPart.LEFT_LEG) != Body.State.IMPLANT || loaded.job() != minion.job()) {
            helper.fail("A saved minion should come back the same");
            return;
        }
        helper.succeed();
    }
}
