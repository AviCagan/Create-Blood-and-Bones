package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.BacktankTier;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.body.BacktankPortBlockEntity;
import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.Surgery;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.body.Vent;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Powered implants and organs: they run on the backtank and stop when it is dry; what organs do; the Vent Arm; the port. */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class ImplantTests {
    private static void wear(Player player, BacktankTier tier, Fluid fluid, int amount) {
        ItemStack tank = new ItemStack(BBItems.backtank(tier));
        FluidBacktankItem.setFluid(tank, new FluidStack(fluid, amount));
        player.setItemSlot(EquipmentSlot.CHEST, tank);
    }

    private static int tank(Player player) {
        return FluidBacktankItem.fluid(FluidBacktankItem.wornBy(player)).getAmount();
    }

    /** A Piston Leg works on soul blood only, takes it each second, and goes dead when the tank is dry. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void poweredLegRunsOnTheTank(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double walk = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        Body body = BodyEffects.body(player);
        body.fit(BodyPart.LEFT_LEG, new ItemStack(BBItems.PISTON_LEG.get()));
        if (body.works(BodyPart.LEFT_LEG, player)) {
            helper.fail("With no backtank a Piston Leg should not work");
            return;
        }
        wear(player, BacktankTier.COPPER, BBFluids.blood(), 1000);
        if (body.works(BodyPart.LEFT_LEG, player)) {
            helper.fail("A Piston Leg should not run on blood");
            return;
        }
        wear(player, BacktankTier.COPPER, BBFluids.soulBlood(), 3);
        BodyEffects.refresh(player);
        if (!body.works(BodyPart.LEFT_LEG, player) || player.getAttributeValue(Attributes.MOVEMENT_SPEED) <= walk
                || player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) <= 3.0) {
            helper.fail("On soul blood a Piston Leg should walk faster than flesh and land soft");
            return;
        }
        BodyEffects.drain(player);
        BodyEffects.drain(player);
        BodyEffects.refresh(player);
        if (tank(player) != 0 || body.works(BodyPart.LEFT_LEG, player)
                || Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - walk * 0.6) > 1.0E-4) {
            helper.fail("Two seconds at 2 mB should empty 3 mB, leaving the leg dead as a missing one: " + tank(player));
            return;
        }
        helper.succeed();
    }

    /** A Hydraulic Arm on the main side hits harder and reaches further while it has soul blood. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hydraulicArmHitsHarder(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double attack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        BodyEffects.body(player).fit(BodyEffects.armFor(player, net.minecraft.world.InteractionHand.MAIN_HAND), new ItemStack(BBItems.HYDRAULIC_ARM.get()));
        wear(player, BacktankTier.IRON, BBFluids.soulBlood(), 1000);
        BodyEffects.refresh(player);
        if (player.getAttributeValue(Attributes.ATTACK_DAMAGE) != attack + 3 || player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) != reach + 1
                || Math.abs(BodyEffects.work(player) - 1.8F) > 1.0E-6F) {
            helper.fail("A working Hydraulic Arm should add 3 attack, 1 reach and break blocks 80% faster");
            return;
        }
        helper.succeed();
    }

    /** Organs: a heart swapped for a Pump Heart comes out in your hands; without a working heart you are weak, without eyes blind. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void organsOutAndIn(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 3), BBBlocks.SURGERY_TABLE.getDefaultState());
        SurgeryTableBlockEntity table = (SurgeryTableBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 2, 3)));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        table.put(new ItemStack(BBItems.PUMP_HEART.get()));
        if (Surgery.operate(helper.getLevel(), player, table, BodyPart.HEART) != Surgery.Action.REPLACE
                || player.getInventory().countItem(BBItems.HEART.get()) != 1 || BodyEffects.body(player).state(BodyPart.HEART) != Body.State.IMPLANT) {
            helper.fail("The Pump Heart should swap in for the heart, which comes out");
            return;
        }
        BodyEffects.second(player);
        if (!player.hasEffect(MobEffects.WEAKNESS) || !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            helper.fail("A Pump Heart with no soul blood should leave you weak and slow");
            return;
        }
        player.removeAllEffects();
        wear(player, BacktankTier.COPPER, BBFluids.soulBlood(), 1000);
        BodyEffects.second(player);
        if (player.hasEffect(MobEffects.WEAKNESS) || !player.hasEffect(MobEffects.REGENERATION)) {
            helper.fail("A running Pump Heart should heal, not weaken");
            return;
        }
        table.take();
        table.put(new ItemStack(BBItems.CLEAVER.get()));
        Surgery.operate(helper.getLevel(), player, table, BodyPart.LEFT_EYE);
        BodyEffects.second(player);
        if (player.hasEffect(MobEffects.BLINDNESS) || player.getInventory().countItem(BBItems.EYE.get()) != 1) {
            helper.fail("One eye out should come out as an eye and leave you seeing");
            return;
        }
        Surgery.operate(helper.getLevel(), player, table, BodyPart.RIGHT_EYE);
        BodyEffects.second(player);
        if (!player.hasEffect(MobEffects.BLINDNESS)) {
            helper.fail("Both eyes out should blind you");
            return;
        }
        helper.succeed();
    }

    /** The Vent Arm sprays the tank: lava sets what is ahead on fire, water puts it out, each shot taking 50 mB. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void ventArmSprays(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos from = helper.absolutePos(new BlockPos(1, 2, 4));
        player.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, -90.0F, 0.0F);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(5, 2, 4));
        pig.setNoAi(true);
        BodyEffects.body(player).fit(BodyEffects.armFor(player, net.minecraft.world.InteractionHand.MAIN_HAND), new ItemStack(BBItems.VENT_ARM.get()));
        wear(player, BacktankTier.IRON, Fluids.LAVA, 1000);
        if (Vent.spray(player) != Vent.Effect.FIRE || !pig.isOnFire() || tank(player) != 950) {
            helper.fail("Lava from the Vent Arm should set the pig ahead on fire and take 50 mB; tank " + tank(player));
            return;
        }
        helper.runAfterDelay(5, () -> {
            wear(player, BacktankTier.IRON, Fluids.WATER, 1000);
            if (Vent.spray(player) != Vent.Effect.SPLASH || pig.isOnFire()) {
                helper.fail("Water from the Vent Arm should put the pig out");
                return;
            }
            wear(player, BacktankTier.IRON, BBFluids.blood(), 1000);
            helper.runAfterDelay(5, () -> {
                if (Vent.effectOf(BBFluids.blood()) != Vent.Effect.SPILL || Vent.spray(player) != Vent.Effect.SPILL) {
                    helper.fail("Blood should just spill");
                    return;
                }
                helper.succeed();
            });
        });
    }

    /** No stomach: nothing can be eaten, but hunger never gets to starving. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void noStomachNeverStarves(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BodyEffects.body(player).lose(BodyPart.STOMACH);
        player.getFoodData().setFoodLevel(0);
        BodyEffects.second(player);
        if (player.getFoodData().getFoodLevel() < 1) {
            helper.fail("Without a stomach hunger should stop short of starving");
            return;
        }
        helper.succeed();
    }

    /** With no arms at all, anything can still be pushed onto the Surgery Table, so nobody is stuck. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void armlessCanStillUseTheTable(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 3), BBBlocks.SURGERY_TABLE.getDefaultState());
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BodyEffects.body(player).lose(BodyPart.LEFT_ARM);
        BodyEffects.body(player).lose(BodyPart.RIGHT_ARM);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(BBItems.HOOK_HAND.get()));
        var onTable = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, net.minecraft.world.InteractionHand.MAIN_HAND, at,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at), net.minecraft.core.Direction.UP, at, false));
        BodyEffects.onUseOnBlock(onTable);
        BlockPos floor = at.east(2).below();
        var elsewhere = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, net.minecraft.world.InteractionHand.MAIN_HAND, floor,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(floor), net.minecraft.core.Direction.UP, floor, false));
        BodyEffects.onUseOnBlock(elsewhere);
        if (onTable.isCanceled() || !elsewhere.isCanceled()) {
            helper.fail("An armless player should still reach the table, and nothing else");
            return;
        }
        helper.succeed();
    }

    /** The port is a worn tank to pipes, only for someone with a Port Arm (which needs nothing to run, so an empty tank fills). */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void portArmOpensTheTank(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        wear(player, BacktankTier.GOLD, BBFluids.blood(), 2000);
        if (BacktankPortBlockEntity.tankOf(player) != null) {
            helper.fail("Without a Port Arm the port should not reach the tank");
            return;
        }
        BodyEffects.body(player).fit(BodyPart.LEFT_ARM, new ItemStack(BBItems.PORT_ARM.get()));
        IFluidHandler port = BacktankPortBlockEntity.tankOf(player);
        if (port == null || port.drain(500, IFluidHandler.FluidAction.EXECUTE).getAmount() != 500 || tank(player) != 1500
                || port.fill(new FluidStack(BBFluids.blood(), 5000), IFluidHandler.FluidAction.EXECUTE) != 1500 || tank(player) != 3000) {
            helper.fail("Through the port a pipe should drain and fill the worn tank; tank " + tank(player));
            return;
        }
        helper.succeed();
    }
}
