package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.SurgeryTableBlock;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.body.TableAttachment;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity;
import com.avicagan.bloodandbones.minion.MinionAssembly;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.MinionStats;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Brass minions (docs/PARTS-AND-TRAITS.md section 6.6 and 6.7): skinned pieces under Brass Sheathing, woken and kept
 * running on soul blood in canisters that a Spout fills and a Charging Cradle swaps in. Each kind has what the other
 * lacks: brass drains a quarter as fast and shrugs off poison, but never heals itself; flesh mends on its blood.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class BrassMinionTests {
    private static PieceRef ref(String entity, String bone, boolean skinned) {
        return new PieceRef(ResourceLocation.withDefaultNamespace(entity), bone, ResourceLocation.withDefaultNamespace("textures/entity/" + entity + "/" + entity + ".png"),
                List.of(), 1.0F, skinned, Map.of(), false);
    }

    private static MinionBuild brassCow() {
        return new MinionBuild(true, ref("cow", "body", true), List.of(), true).with("head", ref("cow", "head", true))
                .with("right_front_leg", ref("cow", "right_front_leg", true)).with("left_front_leg", ref("cow", "left_front_leg", true))
                .with("right_hind_leg", ref("cow", "right_hind_leg", true)).with("left_hind_leg", ref("cow", "left_hind_leg", true));
    }

    private static MinionBuild fleshCow() {
        return MinionBuild.of(ref("cow", "body", false)).with("head", ref("cow", "head", false))
                .with("right_front_leg", ref("cow", "right_front_leg", false)).with("left_front_leg", ref("cow", "left_front_leg", false));
    }

    private static MinionEntity minion(GameTestHelper helper, BlockPos pos, MinionBuild build, float power) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = BBEntities.MINION.get().create(level);
        BlockPos at = helper.absolutePos(pos);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(helper.makeMockPlayer(GameType.SURVIVAL), at, build, power);
        level.addFreshEntity(minion);
        return minion;
    }

    /** A Spout fills an empty canister with 1000 mB of soul blood; an Item Drain takes it back out: plain Create recipes. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spoutFillsCanister(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var filling = recipes.byKey(BloodAndBones.asResource("soul_canister_filling"));
        var emptying = recipes.byKey(BloodAndBones.asResource("soul_canister_emptying"));
        if (filling.isEmpty() || filling.get().value().getType() != AllRecipeTypes.FILLING.getType()
                || !filling.get().value().getResultItem(helper.getLevel().registryAccess()).is(BBItems.SOUL_CANISTER.get())) {
            helper.fail("A Spout should fill the empty canister into a Soul Canister");
            return;
        }
        if (emptying.isEmpty() || emptying.get().value().getType() != AllRecipeTypes.EMPTYING.getType()) {
            helper.fail("An Item Drain should empty a Soul Canister");
            return;
        }
        helper.succeed();
    }

    /**
     * A skinned torso laid on the frame is brass: it takes only skinned pieces, will not wake on blood or before its
     * sheathing, and wakes on a Soul Canister (the empty comes back) with a canister's worth in it.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void brassFrameWakesOnCanister(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(3, 2, 3);
        helper.setBlock(at, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.ASSEMBLY));
        SurgeryTableBlockEntity table = (SurgeryTableBlockEntity) helper.getBlockEntity(at);
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack torso = new ItemStack(BBItems.CARCASS_PIECE.get());
        torso.set(BBDataComponents.PIECE.get(), ref("cow", "body", true).toPiece());
        if (!MinionAssembly.layDown(table, torso, level) || !table.build().orElseThrow().cybernetic()) {
            helper.fail("A skinned torso should make a brass frame");
            return;
        }
        ItemStack hide = new ItemStack(BBItems.CARCASS_PIECE.get());
        hide.set(BBDataComponents.PIECE.get(), ref("cow", "head", false).toPiece());
        if (MinionAssembly.fit(level, table, hide) == null) {
            helper.fail("A brass frame takes only skinned pieces");
            return;
        }
        if (MinionAssembly.wake(level, maker, table, new ItemStack(BBFluids.BLOOD.getBucket().get())) != null
                || MinionAssembly.wake(level, maker, table, new ItemStack(BBItems.SOUL_CANISTER.get())) != null) {
            helper.fail("Brass does not wake on blood, nor before it is sheathed");
            return;
        }
        if (!MinionAssembly.sheathe(table) || !table.build().orElseThrow().sheathed()) {
            helper.fail("Brass Sheathing should go on");
            return;
        }
        MinionEntity minion = MinionAssembly.wake(level, maker, table, new ItemStack(BBItems.SOUL_CANISTER.get()));
        if (minion == null || !minion.cybernetic() || minion.power() != MinionStats.CANISTER || maker.getInventory().countItem(BBItems.EMPTY_SOUL_CANISTER.get()) != 1) {
            helper.fail("A Soul Canister should wake it brass, full, the empty coming back");
            return;
        }
        helper.succeed();
    }

    /** Brass drains a quarter as fast as flesh doing the same: a minute idle, long enough that the drain's 0.05 mB steps do not hide it. */
    @GameTest(template = "empty", timeoutTicks = 1300)
    public static void brassDrainsAQuarter(GameTestHelper helper) {
        MinionEntity brass = minion(helper, new BlockPos(2, 2, 2), brassCow(), 500.0F);
        MinionEntity flesh = minion(helper, new BlockPos(7, 2, 7), fleshCow(), 500.0F);
        brass.setNoAi(true);
        flesh.setNoAi(true);
        helper.runAfterDelay(1200, () -> {
            float brassUsed = 500.0F - brass.power();
            float fleshUsed = 500.0F - flesh.power();
            if (!(fleshUsed > 2.0F) || !(brassUsed > 0.0F) || Math.abs(brassUsed / fleshUsed - MinionEntity.BRASS_DRAIN) > 0.05F) {
                helper.fail("Brass should use a quarter of what flesh uses: " + brassUsed + " vs " + fleshUsed);
                return;
            }
            helper.succeed();
        });
    }

    /** A turning cradle stocked with a canister wakes a brass minion powered down beside it, and keeps the empty. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cradleRevivesPoweredDown(GameTestHelper helper) {
        BlockPos motor = new BlockPos(4, 1, 4);
        BlockPos at = motor.above();
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        helper.setBlock(at, BBBlocks.CHARGING_CRADLE.getDefaultState());
        if (helper.getLevel().getBlockEntity(helper.absolutePos(motor)) instanceof CreativeMotorBlockEntity creative) {
            creative.generatedSpeed.setValue(64);
        }
        ChargingCradleBlockEntity cradle = (ChargingCradleBlockEntity) helper.getBlockEntity(at);
        cradle.inventory.insertItem(0, new ItemStack(BBItems.SOUL_CANISTER.get()), false);
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 5), brassCow(), 10.0F);
        minion.powerDown();
        helper.succeedWhen(() -> {
            helper.assertTrue(!minion.poweredDown() && minion.power() >= MinionStats.CANISTER - 1.0F, "the cradle has not swapped a canister in yet");
            helper.assertTrue(cradle.fullCanisters() == 0 && cradle.inventory.getStackInSlot(ChargingCradleBlockEntity.FULL).is(BBItems.EMPTY_SOUL_CANISTER.get()),
                    "the empty should stay in the cradle");
        });
    }

    /** A cradle turning at 64 RPM with a full canister in it, stocked by hand. */
    private static ChargingCradleBlockEntity turningCradle(GameTestHelper helper, BlockPos at) {
        BlockPos motor = at.below();
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        helper.setBlock(at, BBBlocks.CHARGING_CRADLE.getDefaultState());
        if (helper.getLevel().getBlockEntity(helper.absolutePos(motor)) instanceof CreativeMotorBlockEntity creative) {
            creative.generatedSpeed.setValue(64);
        }
        ChargingCradleBlockEntity cradle = (ChargingCradleBlockEntity) helper.getBlockEntity(at);
        cradle.inventory.insertItem(0, new ItemStack(BBItems.SOUL_CANISTER.get()), false);
        return cradle;
    }

    /** Low on soul blood and awake, a brass minion walks to a turning, stocked cradle across the room by itself, and is charged. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void lowBrassWalksToTheCradle(GameTestHelper helper) {
        ChargingCradleBlockEntity cradle = turningCradle(helper, new BlockPos(2, 2, 5));
        MinionEntity minion = minion(helper, new BlockPos(8, 2, 5), brassCow(), 200.0F);
        helper.succeedWhen(() -> {
            helper.assertTrue(minion.power() >= MinionStats.CANISTER - 1.0F && cradle.fullCanisters() == 0,
                    "it has not walked to the cradle and been charged yet (at " + minion.position() + ", " + minion.power() + " mB)");
        });
    }

    /** A cradle walled in where it cannot get to is no use: it never sets off for it, and the canister stays. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void walledInCradleIsNoUse(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 2, 5);
        ChargingCradleBlockEntity cradle = turningCradle(helper, at);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int up = 0; up < 3; up++) {
                helper.setBlock(at.relative(side).above(up), net.minecraft.world.level.block.Blocks.GLASS);
                helper.setBlock(at.relative(side).relative(side.getClockWise()).above(up), net.minecraft.world.level.block.Blocks.GLASS);
            }
        }
        helper.setBlock(at.above(), net.minecraft.world.level.block.Blocks.GLASS);
        MinionEntity minion = minion(helper, new BlockPos(8, 2, 5), brassCow(), 200.0F);
        helper.runAfterDelay(250, () -> {
            if (minion.power() > 200.0F || cradle.fullCanisters() != 1) {
                helper.fail("It should not be charged by a cradle it cannot reach (" + minion.power() + " mB, at " + minion.position() + ")");
                return;
            }
            helper.succeed();
        });
    }

    /** Pipes of items: full canisters and sheets go in, empties come out, and nothing else either way. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cradleAutomation(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 3), BBBlocks.CHARGING_CRADLE.getDefaultState());
        ChargingCradleBlockEntity cradle = (ChargingCradleBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 3));
        IItemHandler handler = cradle.automation();
        ItemStack rest = new ItemStack(BBItems.SOUL_CANISTER.get());
        for (int i = 0; i < handler.getSlots() && !rest.isEmpty(); i++) {
            rest = handler.insertItem(i, rest, false);
        }
        ItemStack empty = new ItemStack(BBItems.EMPTY_SOUL_CANISTER.get());
        for (int i = 0; i < handler.getSlots() && !empty.isEmpty(); i++) {
            empty = handler.insertItem(i, empty, false);
        }
        ItemStack sheet = new ItemStack(AllItems.BRASS_SHEET.get(), 5);
        for (int i = 0; i < handler.getSlots() && !sheet.isEmpty(); i++) {
            sheet = handler.insertItem(i, sheet, false);
        }
        if (!rest.isEmpty() || empty.isEmpty() || !sheet.isEmpty() || cradle.fullCanisters() != 1) {
            helper.fail("A full canister and sheets go in; an empty canister does not");
            return;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.extractItem(i, 64, true).isEmpty()) {
                helper.fail("Nothing but empties comes out, and there are none");
                return;
            }
        }
        helper.succeed();
    }

    /** Brass shrugs off poison and mends with a brass sheet by hand; flesh mends on its own blood. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void eachKindHasWhatTheOtherLacks(GameTestHelper helper) {
        MinionEntity brass = minion(helper, new BlockPos(2, 2, 2), brassCow(), 500.0F);
        MinionEntity flesh = minion(helper, new BlockPos(7, 2, 7), fleshCow(), 500.0F);
        brass.setNoAi(true);
        flesh.setNoAi(true);
        if (brass.addEffect(new MobEffectInstance(MobEffects.POISON, 100)) || !flesh.addEffect(new MobEffectInstance(MobEffects.POISON, 100))) {
            helper.fail("Brass should not take poison; flesh should");
            return;
        }
        flesh.removeAllEffects();
        brass.setHealth(brass.getMaxHealth() - 12.0F);
        flesh.setHealth(flesh.getMaxHealth() - 4.0F);
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        maker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.BRASS_SHEET.get()));
        brass.interact(maker, InteractionHand.MAIN_HAND);
        if (Math.abs(brass.getHealth() - (brass.getMaxHealth() - 2.0F)) > 0.01F || !maker.getMainHandItem().isEmpty()) {
            helper.fail("A brass sheet should mend 10 health on brass");
            return;
        }
        float brassHealth = brass.getHealth();
        float fleshHealth = flesh.getHealth();
        float fleshBlood = flesh.power();
        helper.runAfterDelay(MinionEntity.REGEN_TICKS * 2 + 5, () -> {
            if (!(flesh.getHealth() > fleshHealth) || !(flesh.power() < fleshBlood - MinionEntity.REGEN_COST + 0.01F)) {
                helper.fail("Flesh should mend itself, paying blood: " + flesh.getHealth() + " " + flesh.power());
                return;
            }
            if (brass.getHealth() > brassHealth) {
                helper.fail("Brass never heals itself");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Flesh keeps the hide traits of the mobs it is built of (a cow's thick hide: armour), up to three different mobs;
     * brass keeps none.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void fleshKeepsItsHidesTraits(GameTestHelper helper) {
        MinionEntity flesh = minion(helper, new BlockPos(2, 2, 2), fleshCow(), 500.0F);
        MinionEntity brass = minion(helper, new BlockPos(7, 2, 7), brassCow(), 500.0F);
        MinionBuild mixed = MinionBuild.of(ref("cow", "body", false)).with("head", ref("pig", "head", false))
                .with("right_front_leg", ref("rabbit", "right_front_leg", false)).with("left_front_leg", ref("horse", "left_front_leg", false))
                .with("right_hind_leg", ref("sheep", "right_hind_leg", false));
        MinionEntity many = minion(helper, new BlockPos(2, 2, 7), mixed, 500.0F);
        ResourceLocation thickHide = BloodAndBones.asResource("thick_hide");
        helper.runAfterDelay(25, () -> {
            if (com.avicagan.bloodandbones.parts.ActiveTraits.of(flesh).level(thickHide) < 1
                    || !(flesh.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) >= 1.0)) {
                helper.fail("A flesh cow should keep the cow's thick hide: " + com.avicagan.bloodandbones.parts.ActiveTraits.of(flesh).entries());
                return;
            }
            // brass has its parts' minion traits, but no hide's
            if (com.avicagan.bloodandbones.parts.ActiveTraits.of(brass).level(thickHide) != 0
                    || brass.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) != 0.0) {
                helper.fail("Brass keeps no hide traits: " + com.avicagan.bloodandbones.parts.ActiveTraits.of(brass).entries());
                return;
            }
            if (!many.hides().equals(List.of(ResourceLocation.withDefaultNamespace("cow"), ResourceLocation.withDefaultNamespace("pig"),
                    ResourceLocation.withDefaultNamespace("rabbit")))) {
                helper.fail("Flesh keeps the hides of three different mobs at most, torso first: " + many.hides());
                return;
            }
            helper.succeed();
        });
    }

    private static MinionEntity withModule(GameTestHelper helper, BlockPos pos, com.avicagan.bloodandbones.cyber.Module module) {
        MinionEntity minion = minion(helper, pos, brassCow(), 800.0F);
        minion.setNoAi(true);
        minion.setModule(module);
        return minion;
    }

    /** Its maker fits a module by hand (one already there comes back); the Grappling Spool is not for minions; a Wrench takes it out. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void brassMinionTakesAModule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        MinionEntity minion = BBEntities.MINION.get().create(level);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(maker, at, brassCow(), 800.0F);
        level.addFreshEntity(minion);
        maker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.module(com.avicagan.bloodandbones.cyber.Module.GRAPPLING_SPOOL)));
        minion.interact(maker, InteractionHand.MAIN_HAND);
        if (minion.module() != null) {
            helper.fail("A Grappling Spool is not for minions");
            return;
        }
        maker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.module(com.avicagan.bloodandbones.cyber.Module.MAGNET_COIL)));
        minion.interact(maker, InteractionHand.MAIN_HAND);
        maker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.module(com.avicagan.bloodandbones.cyber.Module.ANALYTICAL_LENS)));
        minion.interact(maker, InteractionHand.MAIN_HAND);
        if (minion.module() != com.avicagan.bloodandbones.cyber.Module.ANALYTICAL_LENS
                || maker.getInventory().countItem(BBItems.module(com.avicagan.bloodandbones.cyber.Module.MAGNET_COIL)) != 1) {
            helper.fail("The Lens should go in, the Magnet Coil coming back");
            return;
        }
        maker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.WRENCH.get()));
        minion.interact(maker, InteractionHand.MAIN_HAND);
        if (minion.module() != null || maker.getInventory().countItem(BBItems.module(com.avicagan.bloodandbones.cyber.Module.ANALYTICAL_LENS)) != 1) {
            helper.fail("A Wrench should take the module out");
            return;
        }
        helper.succeed();
    }

    /** A Magnet Coil draws loose items to it. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void magnetCoilDrawsItems(GameTestHelper helper) {
        MinionEntity minion = withModule(helper, new BlockPos(2, 2, 2), com.avicagan.bloodandbones.cyber.Module.MAGNET_COIL);
        BlockPos drop = helper.absolutePos(new BlockPos(7, 2, 2));
        net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), drop.getX() + 0.5, drop.getY() + 0.2,
                drop.getZ() + 0.5, new ItemStack(net.minecraft.world.item.Items.BONE));
        helper.getLevel().addFreshEntity(item);
        double start = item.distanceTo(minion);
        helper.succeedWhen(() -> helper.assertTrue(item.distanceTo(minion) < start - 2.0, "the bone has not drifted to it yet"));
    }

    /** A Rotational Coupler: standing by the end of a shaft, it drives it. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void couplerDrivesAShaft(GameTestHelper helper) {
        BlockPos shaft = new BlockPos(4, 2, 3);
        helper.setBlock(shaft, AllBlocks.SHAFT.getDefaultState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));
        withModule(helper, new BlockPos(3, 2, 3), com.avicagan.bloodandbones.cyber.Module.ROTATIONAL_COUPLER);
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockEntity(shaft) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity kinetic
                && Math.abs(kinetic.getSpeed()) > 0.0F, "the shaft is not turning yet"));
    }

    /**
     * A minion's coupler goes only into air: standing on a snow layer by a shaft end it does not couple, which would
     * leave the snow gone (as air) when it let go.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void couplerLeavesSnowAlone(GameTestHelper helper) {
        BlockPos shaft = new BlockPos(4, 2, 3);
        BlockPos feet = new BlockPos(3, 2, 3);
        helper.setBlock(shaft, AllBlocks.SHAFT.getDefaultState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));
        helper.setBlock(feet, net.minecraft.world.level.block.Blocks.SNOW);
        MinionEntity minion = withModule(helper, feet, com.avicagan.bloodandbones.cyber.Module.ROTATIONAL_COUPLER);
        helper.runAfterDelay(60, () -> {
            if (!helper.getBlockState(feet).is(net.minecraft.world.level.block.Blocks.SNOW) || com.avicagan.bloodandbones.cyber.Coupler.coupled(minion)) {
                helper.fail("It should leave the snow where it is, not couple over it: " + helper.getBlockState(feet));
                return;
            }
            helper.succeed();
        });
    }

    /** An Analytical Lens sees a zombie through a wall; without one it does not. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lensSeesThroughWalls(GameTestHelper helper) {
        for (int y = 2; y <= 4; y++) {
            for (int z = 0; z <= 6; z++) {
                helper.setBlock(new BlockPos(4, y, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        MinionEntity lens = withModule(helper, new BlockPos(2, 2, 3), com.avicagan.bloodandbones.cyber.Module.ANALYTICAL_LENS);
        MinionEntity plain = withModule(helper, new BlockPos(2, 2, 1), null);
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(6, 2, 3));
        zombie.setNoAi(true);
        if (!lens.hasLineOfSight(zombie) || plain.hasLineOfSight(zombie)) {
            helper.fail("The Lens should see through the wall and plain brass should not");
            return;
        }
        helper.succeed();
    }
}
