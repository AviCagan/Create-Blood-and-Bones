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

    /**
     * A Spout fills an empty canister with 1000 mB of soul blood; an Item Drain takes it back out: plain Create recipes,
     * and a real Spout over a Depot runs the filling one.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
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
        BlockPos depot = new BlockPos(3, 2, 3);
        BlockPos spout = depot.above(2);
        helper.setBlock(depot, AllBlocks.DEPOT.getDefaultState());
        helper.setBlock(spout, AllBlocks.SPOUT.getDefaultState());
        ServerLevel level = helper.getLevel();
        var tank = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, helper.absolutePos(spout), Direction.UP);
        IItemHandler onDepot = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, helper.absolutePos(depot), null);
        if (tank == null || onDepot == null || tank.fill(new net.neoforged.neoforge.fluids.FluidStack(BBFluids.soulBlood(), 1000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) != 1000
                || !onDepot.insertItem(0, new ItemStack(BBItems.EMPTY_SOUL_CANISTER.get()), false).isEmpty()) {
            helper.fail("The Spout should take a bucket's worth of soul blood, and the Depot the empty canister");
            return;
        }
        helper.succeedWhen(() -> helper.assertTrue(onDepot.getStackInSlot(0).is(BBItems.SOUL_CANISTER.get()) && tank.getFluidInTank(0).isEmpty(),
                "the Spout has not filled the canister yet: " + onDepot.getStackInSlot(0) + ", " + tank.getFluidInTank(0).getAmount() + " mB left"));
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
        // a pig's head would hunt; kept to carrying, it leaves the other tests' animals alone
        many.setJob(BloodAndBones.asResource("courier"));
        ResourceLocation thickHide = BloodAndBones.asResource("thick_hide");
        helper.runAfterDelay(25, () -> {
            if (com.avicagan.bloodandbones.parts.ActiveTraits.of(flesh).level(thickHide) < 1
                    || !(flesh.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) >= 1.0)) {
                helper.fail("A flesh cow should keep the cow's thick hide: " + com.avicagan.bloodandbones.parts.ActiveTraits.of(flesh).entries());
                return;
            }
            // its parts' own minion traits still work (a cow's sure-footed legs); only the hide's are gone
            if (com.avicagan.bloodandbones.parts.ActiveTraits.of(brass).level(thickHide) != 0 || !brass.hides().isEmpty()
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

    // ---- slice 8: the cradle fed by hoppers and Mechanical Arms, repair by Deployer, poison, the filter slot

    /** A turning cradle with nothing in it yet. */
    private static ChargingCradleBlockEntity emptyCradle(GameTestHelper helper, BlockPos at) {
        BlockPos motor = at.below();
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        helper.setBlock(at, BBBlocks.CHARGING_CRADLE.getDefaultState());
        if (helper.getLevel().getBlockEntity(helper.absolutePos(motor)) instanceof CreativeMotorBlockEntity creative) {
            creative.generatedSpeed.setValue(64);
        }
        return (ChargingCradleBlockEntity) helper.getBlockEntity(at);
    }

    /**
     * A hopper beside the cradle feeds it full canisters through its item handler, and the cradle swaps one into a brass
     * minion powered down beside it; the empty stays for a funnel or an arm to take.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void cradleSwapsFromHopper(GameTestHelper helper) {
        BlockPos at = new BlockPos(5, 2, 5);
        ChargingCradleBlockEntity cradle = emptyCradle(helper, at);
        BlockPos hopperAt = at.west();
        helper.setBlock(hopperAt, net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.HopperBlock.FACING, Direction.EAST));
        var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) helper.getBlockEntity(hopperAt);
        hopper.setItem(0, new ItemStack(BBItems.SOUL_CANISTER.get(), 2));
        MinionEntity minion = minion(helper, new BlockPos(7, 2, 5), brassCow(), 10.0F);
        minion.powerDown();
        helper.succeedWhen(() -> {
            helper.assertTrue(hopper.isEmpty(), "the hopper has not fed both canisters in yet");
            helper.assertTrue(!minion.poweredDown() && minion.power() >= MinionStats.CANISTER - 1.0F, "the cradle has not woken the minion yet");
            helper.assertTrue(cradle.fullCanisters() == 1 && cradle.inventory.getStackInSlot(ChargingCradleBlockEntity.FULL).is(BBItems.EMPTY_SOUL_CANISTER.get()),
                    "one full canister should be left, and the empty kept");
        });
    }

    /**
     * A Mechanical Arm turned by a cogwheel beside it (the arm's base is a cog) on a creative motor, told to take from
     * one place and put in another, as its item's placement does.
     */
    private static void arm(GameTestHelper helper, BlockPos at, Direction cogSide, BlockPos takeFrom, BlockPos putIn) {
        ServerLevel level = helper.getLevel();
        BlockPos cog = at.relative(cogSide);
        helper.setBlock(cog.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        helper.setBlock(cog, AllBlocks.COGWHEEL.getDefaultState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Y));
        helper.setBlock(at, AllBlocks.MECHANICAL_ARM.getDefaultState());
        if (level.getBlockEntity(helper.absolutePos(cog.below())) instanceof CreativeMotorBlockEntity creative) {
            creative.generatedSpeed.setValue(128);
        }
        BlockPos armAt = helper.absolutePos(at);
        net.minecraft.nbt.ListTag points = new net.minecraft.nbt.ListTag();
        BlockPos from = helper.absolutePos(takeFrom);
        var take = com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint.create(level, from, level.getBlockState(from));
        BlockPos to = helper.absolutePos(putIn);
        var put = com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint.create(level, to, level.getBlockState(to));
        if (take == null || put == null) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("A Mechanical Arm cannot work with " + level.getBlockState(take == null ? from : to));
        }
        // a point starts as somewhere to put things; once round, it is somewhere to take from
        take.cycleMode();
        points.add(take.serialize(armAt));
        points.add(put.serialize(armAt));
        var be = (com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity) level.getBlockEntity(armAt);
        net.minecraft.nbt.CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
        tag.put("InteractionPoints", points);
        be.loadWithComponents(tag, level.registryAccess());
    }

    /**
     * Mechanical Arms work the cradle as they work a Depot: one takes a full canister off a Depot and puts it in the
     * cradle, the cradle swaps it into a brass minion powered down beside it, and another arm takes the empty out onto a
     * second Depot, for a Spout.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void armLoadsCradle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(5, 2, 5);
        ChargingCradleBlockEntity cradle = emptyCradle(helper, at);
        BlockPos fullDepot = new BlockPos(3, 2, 2);
        BlockPos emptyDepot = new BlockPos(7, 2, 8);
        helper.setBlock(fullDepot, AllBlocks.DEPOT.getDefaultState());
        helper.setBlock(emptyDepot, AllBlocks.DEPOT.getDefaultState());
        IItemHandler full = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, helper.absolutePos(fullDepot), null);
        IItemHandler empties = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, helper.absolutePos(emptyDepot), null);
        full.insertItem(0, new ItemStack(BBItems.SOUL_CANISTER.get()), false);
        arm(helper, new BlockPos(5, 2, 2), Direction.EAST, fullDepot, at);
        arm(helper, new BlockPos(5, 2, 8), Direction.WEST, at, emptyDepot);
        MinionEntity minion = minion(helper, new BlockPos(7, 2, 5), brassCow(), 10.0F);
        minion.powerDown();
        helper.succeedWhen(() -> {
            helper.assertTrue(full.getStackInSlot(0).isEmpty(), "the first arm has not taken the canister off the Depot yet");
            helper.assertTrue(!minion.poweredDown() && minion.power() >= MinionStats.CANISTER - 1.0F, "the cradle has not woken the minion yet");
            helper.assertTrue(empties.getStackInSlot(0).is(BBItems.EMPTY_SOUL_CANISTER.get()), "the second arm has not brought the empty out yet");
            helper.assertTrue(cradle.fullCanisters() == 0 && cradle.inventory.getStackInSlot(ChargingCradleBlockEntity.FULL).isEmpty(),
                    "the cradle should be empty again");
        });
    }

    /** A Deployer facing down onto this spot, turning. */
    private static com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity deployerOver(GameTestHelper helper, BlockPos spot) {
        BlockPos deployerPos = spot.above(2);
        helper.setBlock(deployerPos, AllBlocks.DEPLOYER.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.DOWN)
                .setValue(com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
        helper.setBlock(deployerPos.east(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));
        if (helper.getLevel().getBlockEntity(helper.absolutePos(deployerPos.east())) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(128);
        }
        return (com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity) helper.getBlockEntity(deployerPos);
    }

    /**
     * A Deployer holding brass sheets over a hurt brass minion mends it as a hand does, 10 health a sheet, and stops
     * using them once it is whole; set to punch, with a sword, it does not hurt it.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void deployerRepairsBrass(GameTestHelper helper) {
        BlockPos spot = new BlockPos(3, 2, 3);
        MinionEntity minion = minion(helper, spot, brassCow(), 800.0F);
        minion.setNoAi(true);
        minion.setHealth(minion.getMaxHealth() - 12.0F);
        var deployer = deployerOver(helper, spot);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> deployer.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.BRASS_SHEET.get(), 3)))
                .thenWaitUntil(() -> helper.assertTrue(minion.getHealth() >= minion.getMaxHealth(),
                        "the Deployer has not mended it yet (" + minion.getHealth() + " of " + minion.getMaxHealth() + ")"))
                .thenIdle(60)
                .thenExecute(() -> {
                    ItemStack left = deployer.getPlayer().getMainHandItem();
                    helper.assertTrue(left.is(AllItems.BRASS_SHEET.get()) && left.getCount() == 1,
                            "two sheets should mend 12 health, and the third stay in hand once it is whole: " + left);
                    deployer.changeMode();
                    deployer.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
                })
                .thenIdle(80)
                .thenExecute(() -> helper.assertTrue(minion.isAlive() && minion.getHealth() >= minion.getMaxHealth(),
                        "a Deployer's punch should never hurt a minion: " + minion.getHealth()))
                .thenSucceed();
    }

    /**
     * A Deployer's stand-in player, even one placed by the minion's maker, only charges, feeds and mends: it never takes
     * the minion apart on the table, folds it, changes its module or filter, takes what it holds, or hurts it, awake or
     * down. The maker's own Cleaver still takes it apart, and its filter comes back with the rest.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void deployerNeverTakesMinionApart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos tablePos = new BlockPos(3, 2, 3);
        helper.setBlock(tablePos, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.ASSEMBLY));
        SurgeryTableBlockEntity table = (SurgeryTableBlockEntity) helper.getBlockEntity(tablePos);
        BlockPos at = helper.absolutePos(tablePos.above());
        MinionEntity minion = BBEntities.MINION.get().create(level);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(maker, at, brassCow(), 800.0F);
        level.addFreshEntity(minion);
        minion.setNoAi(true);
        minion.setModule(com.avicagan.bloodandbones.cyber.Module.MAGNET_COIL);
        minion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.BOW));
        ItemStack filter = listFilter(net.minecraft.world.item.Items.IRON_INGOT);
        maker.setShiftKeyDown(true);
        maker.setItemInHand(InteractionHand.MAIN_HAND, filter.copy());
        minion.interact(maker, InteractionHand.MAIN_HAND);
        maker.setShiftKeyDown(false);
        var stand = new com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer(level, maker.getUUID());
        if (!minion.isMaker(stand)) {
            helper.fail("The stand-in of a Deployer its maker placed passes for its maker");
            return;
        }
        java.util.function.Consumer<ItemStack> use = stack -> {
            stand.setItemInHand(InteractionHand.MAIN_HAND, stack);
            minion.interact(stand, InteractionHand.MAIN_HAND);
        };
        use.accept(new ItemStack(BBItems.module(com.avicagan.bloodandbones.cyber.Module.ANALYTICAL_LENS)));
        use.accept(new ItemStack(AllItems.WRENCH.get()));
        stand.setShiftKeyDown(true);
        use.accept(new ItemStack(AllItems.WRENCH.get()));
        use.accept(listFilter(net.minecraft.world.item.Items.GOLD_INGOT));
        stand.setShiftKeyDown(false);
        use.accept(ItemStack.EMPTY);
        float health = minion.getHealth();
        minion.hurt(minion.damageSources().playerAttack(stand), 5.0F);
        if (minion.module() != com.avicagan.bloodandbones.cyber.Module.MAGNET_COIL || !minion.getMainHandItem().is(net.minecraft.world.item.Items.BOW)
                || !ItemStack.isSameItemSameComponents(minion.filter().stack(), filter) || minion.getHealth() != health) {
            helper.fail("A Deployer changed or hurt it: module " + minion.module() + ", holding " + minion.getMainHandItem() + ", filter "
                    + minion.filter().stack() + ", health " + minion.getHealth() + " of " + health);
            return;
        }
        helper.runAfterDelay(10, () -> {
            minion.powerDown();
            use.accept(new ItemStack(BBItems.CLEAVER.get()));
            stand.setShiftKeyDown(true);
            for (int i = 0; i < 15; i++) {
                use.accept(ItemStack.EMPTY);
            }
            stand.setShiftKeyDown(false);
            minion.hurt(minion.damageSources().playerAttack(stand), 5.0F);
            if (minion.isRemoved() || table.build().isPresent() || minion.getHealth() != health) {
                helper.fail("A Deployer should never take it apart, fold it or hurt it lying down: removed " + minion.isRemoved() + ", "
                        + minion.getHealth() + " of " + health);
                return;
            }
            maker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.CLEAVER.get()));
            minion.interact(maker, InteractionHand.MAIN_HAND);
            if (!minion.isRemoved() || table.build().isEmpty() || maker.getInventory().countItem(AllItems.FILTER.get()) != 1) {
                helper.fail("Its maker's Cleaver should take it apart, the filter coming back: " + minion.isRemoved());
                return;
            }
            helper.succeed();
        });
    }

    /** A pool of water three deep walled in glass, its inside three wide from this corner. */
    private static void pool(GameTestHelper helper, int x) {
        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = 0; dz <= 4; dz++) {
                boolean wall = dx == 0 || dx == 4 || dz == 0 || dz == 4;
                for (int y = 2; y <= 5; y++) {
                    helper.setBlock(new BlockPos(x + dx, y, 1 + dz), wall ? net.minecraft.world.level.block.Blocks.GLASS
                            : y <= 4 ? net.minecraft.world.level.block.Blocks.WATER : net.minecraft.world.level.block.Blocks.AIR);
                }
            }
        }
    }

    /**
     * Brass is immune to poison, wither and hunger, and does not drown (docs/PARTS-AND-TRAITS.md section 6.6); flesh is
     * poisoned and drowns, out of air under water.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cyberImmuneToPoison(GameTestHelper helper) {
        pool(helper, 0);
        pool(helper, 5);
        MinionEntity brass = minion(helper, new BlockPos(2, 2, 3), brassCow(), 800.0F);
        MinionEntity flesh = minion(helper, new BlockPos(7, 2, 3), fleshCow(), 800.0F);
        brass.setNoAi(true);
        flesh.setNoAi(true);
        for (var effect : List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.HUNGER)) {
            if (brass.addEffect(new MobEffectInstance(effect, 200)) || brass.hasEffect(effect)) {
                helper.fail("Brass should take no " + effect.value().getDescriptionId());
                return;
            }
        }
        if (!flesh.addEffect(new MobEffectInstance(MobEffects.POISON, 200))) {
            helper.fail("Flesh should be poisoned");
            return;
        }
        flesh.removeAllEffects();
        brass.setAirSupply(0);
        flesh.setAirSupply(0);
        float brassHealth = brass.getHealth();
        float fleshHealth = flesh.getHealth();
        helper.runAfterDelay(70, () -> {
            if (!brass.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) || !flesh.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
                helper.fail("Both should be under water");
                return;
            }
            if (brass.getHealth() < brassHealth) {
                helper.fail("Brass should not drown: " + brass.getHealth() + " health, " + brass.getAirSupply() + " air");
                return;
            }
            if (!(flesh.getHealth() < fleshHealth)) {
                helper.fail("Flesh out of air under water should drown");
                return;
            }
            helper.succeed();
        });
    }

    /** A Create Filter listing these items. */
    static ItemStack listFilter(net.minecraft.world.item.Item... items) {
        ItemStack filter = new ItemStack(AllItems.FILTER.get());
        filter.set(com.simibubi.create.AllDataComponents.FILTER_ITEMS, net.minecraft.world.item.component.ItemContainerContents.fromItems(
                java.util.Arrays.stream(items).map(ItemStack::new).toList()));
        return filter;
    }

    /** A Create Attribute Filter passing what has these attributes (any of them). */
    static ItemStack attributeFilter(com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry... attributes) {
        ItemStack filter = new ItemStack(AllItems.ATTRIBUTE_FILTER.get());
        filter.set(com.simibubi.create.AllDataComponents.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES, List.of(attributes));
        return filter;
    }

    /**
     * The filter slot: its maker crouch-uses a Filter on a brass minion and it goes in (the one there before coming back),
     * any other item sets a copy, a crouching Wrench takes it out; flesh has no slot. What it lets through is Create's own
     * test (a list, a blacklist, an Attribute Filter), and mobs are asked about as their spawn eggs. It stays with the
     * minion saved and loaded.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void filterSlotWorksAsCreates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        MinionEntity brass = BBEntities.MINION.get().create(level);
        brass.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        brass.setup(maker, at, brassCow(), 800.0F);
        MinionEntity flesh = BBEntities.MINION.get().create(level);
        flesh.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        flesh.setup(maker, at, fleshCow(), 800.0F);
        java.util.function.BiConsumer<MinionEntity, ItemStack> crouchUse = (minion, stack) -> {
            maker.setShiftKeyDown(true);
            maker.setItemInHand(InteractionHand.MAIN_HAND, stack);
            minion.interact(maker, InteractionHand.MAIN_HAND);
            maker.setShiftKeyDown(false);
        };
        ItemStack iron = listFilter(net.minecraft.world.item.Items.IRON_INGOT);
        crouchUse.accept(flesh, iron.copy());
        if (!flesh.filter().isEmpty() || maker.getMainHandItem().isEmpty()) {
            helper.fail("Flesh has no filter slot");
            return;
        }
        crouchUse.accept(brass, iron.copy());
        if (!ItemStack.isSameItemSameComponents(brass.filter().stack(), iron) || !maker.getMainHandItem().isEmpty()) {
            helper.fail("A Filter should go into a brass minion's slot");
            return;
        }
        if (!brass.filter().allows(level, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5))
                || brass.filter().allows(level, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE))) {
            helper.fail("A list of iron should pass iron and nothing else");
            return;
        }
        ItemStack notIron = iron.copy();
        notIron.set(com.simibubi.create.AllDataComponents.FILTER_ITEMS_BLACKLIST, true);
        crouchUse.accept(brass, notIron.copy());
        if (maker.getInventory().countItem(AllItems.FILTER.get()) != 1 || brass.filter().allows(level, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT))
                || !brass.filter().allows(level, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE))) {
            helper.fail("A blacklist should go in, the old Filter coming back, and pass all but iron");
            return;
        }
        var consumable = new com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry(
                com.simibubi.create.content.logistics.item.filter.attribute.AllItemAttributeTypes.CONSUMABLE.createAttribute(), false);
        brass.filter().set(attributeFilter(consumable));
        if (!brass.filter().allows(level, new ItemStack(net.minecraft.world.item.Items.APPLE)) || brass.filter().allows(level, new ItemStack(net.minecraft.world.item.Items.STICK))) {
            helper.fail("An Attribute Filter for food should pass an apple and not a stick");
            return;
        }
        net.minecraft.world.entity.animal.Cow cow = helper.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(7, 2, 7));
        net.minecraft.world.entity.animal.Pig pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, new BlockPos(7, 2, 3));
        cow.setNoAi(true);
        pig.setNoAi(true);
        brass.filter().set(listFilter(net.minecraft.world.item.Items.PIG_SPAWN_EGG));
        boolean eggs = brass.filter().allows(level, pig) && !brass.filter().allows(level, cow);
        brass.filter().set(new ItemStack(net.minecraft.world.item.Items.COW_SPAWN_EGG));
        eggs &= brass.filter().allows(level, cow) && !brass.filter().allows(level, pig);
        var vanilla = new com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry(
                new com.simibubi.create.content.logistics.item.filter.attribute.attributes.AddedByAttribute("minecraft"), true);
        brass.filter().set(attributeFilter(vanilla));
        eggs &= !brass.filter().allows(level, cow);
        if (!eggs) {
            helper.fail("A mob should be asked about as its spawn egg: in a list, alone, or by an Attribute Filter");
            return;
        }
        // any item goes in as a copy, and stays in hand
        crouchUse.accept(brass, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 4));
        if (!brass.filter().stack().is(net.minecraft.world.item.Items.IRON_INGOT) || maker.getMainHandItem().getCount() != 4) {
            helper.fail("A plain item should set a copy and stay in hand");
            return;
        }
        net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
        brass.saveWithoutId(saved);
        MinionEntity loaded = BBEntities.MINION.get().create(level);
        loaded.load(saved);
        if (!loaded.filter().stack().is(net.minecraft.world.item.Items.IRON_INGOT)) {
            helper.fail("The filter should be saved with it");
            return;
        }
        // the Wrench takes the place of the iron in hand: nothing iron should come back
        crouchUse.accept(brass, new ItemStack(AllItems.WRENCH.get()));
        if (!brass.filter().isEmpty() || maker.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT) != 0) {
            helper.fail("A crouching Wrench should take the filter out, and a plain item's copy is not given back");
            return;
        }
        helper.succeed();
    }

    /**
     * A brass courier with a Filter of iron in its slot carries only iron to the chest: it leaves the cobblestone lying
     * nearer to it than the iron.
     */
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void filteredCourierOnlyMovesIron(GameTestHelper helper) {
        MinionJobTests.pen(helper);
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(1, 2, 1), net.minecraft.world.level.block.Blocks.CHEST);
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        MinionEntity minion = BBEntities.MINION.get().create(level);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(maker, at, brassCow(), 1000.0F);
        level.addFreshEntity(minion);
        if (!minion.setJob(BloodAndBones.asResource("courier"))) {
            helper.fail("A cow's head should offer courier");
            return;
        }
        maker.setShiftKeyDown(true);
        maker.setItemInHand(InteractionHand.MAIN_HAND, listFilter(net.minecraft.world.item.Items.IRON_INGOT));
        minion.interact(maker, InteractionHand.MAIN_HAND);
        maker.setShiftKeyDown(false);
        BlockPos near = helper.absolutePos(new BlockPos(6, 2, 3));
        net.minecraft.world.entity.item.ItemEntity cobble = new net.minecraft.world.entity.item.ItemEntity(level, near.getX() + 0.5, near.getY() + 0.2, near.getZ() + 0.5,
                new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 2));
        level.addFreshEntity(cobble);
        BlockPos far = helper.absolutePos(new BlockPos(8, 2, 8));
        level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, far.getX() + 0.5, far.getY() + 0.2, far.getZ() + 0.5,
                new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 3)));
        helper.succeedWhen(() -> {
            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 1));
            helper.assertTrue(minion.filter().stack().is(AllItems.FILTER.get()), "the Filter should be in its slot");
            helper.assertTrue(chest.countItem(net.minecraft.world.item.Items.IRON_INGOT) == 3, "the courier has not put the iron in the chest yet");
            helper.assertTrue(cobble.isAlive() && cobble.getItem().getCount() == 2 && chest.countItem(net.minecraft.world.item.Items.COBBLESTONE) == 0
                    && minion.inventory.countItem(net.minecraft.world.item.Items.COBBLESTONE) == 0, "it should leave the cobblestone alone");
        });
    }
}
