package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.BacktankTier;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.Surgery;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.cyber.Coupler;
import com.avicagan.bloodandbones.cyber.Module;
import com.avicagan.bloodandbones.cyber.ModuleActions;
import com.avicagan.bloodandbones.cyber.Modules;
import com.avicagan.bloodandbones.cyber.SetBonus;
import com.avicagan.bloodandbones.cyber.Throttle;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The cybernetic throttle, the modules in brass limbs, and the two set bonuses. */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class CyberTests {
    private static void wear(Player player, int soulBlood) {
        ItemStack tank = new ItemStack(BBItems.backtank(BacktankTier.IRON));
        FluidBacktankItem.setFluid(tank, new FluidStack(BBFluids.soulBlood(), soulBlood));
        player.setItemSlot(EquipmentSlot.CHEST, tank);
    }

    private static int tank(Player player) {
        return FluidBacktankItem.fluid(FluidBacktankItem.wornBy(player)).getAmount();
    }

    /** A brass limb with these modules, fitted in that part. */
    private static void fit(Player player, BodyPart part, Module... modules) {
        ItemStack chassis = new ItemStack(switch (part.kind()) {
            case ARM -> BBItems.HYDRAULIC_ARM.get();
            case LEG -> BBItems.PISTON_LEG.get();
            default -> BBItems.OPTIC_EYE.get();
        });
        Modules.set(chassis, List.of(modules));
        BodyEffects.body(player).fit(part, chassis);
        BodyEffects.refresh(player);
    }

    private static BodyPart mainArm(Player player) {
        return BodyEffects.armFor(player, InteractionHand.MAIN_HAND);
    }

    /** The drain climbs with the cube of the spool, full in two seconds; a held throttle pays as it goes and a tap costs the tap. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void throttleSpoolsAndDrainsSteeply(GameTestHelper helper) {
        if (Throttle.level(0) != 0.0F || Throttle.level(Throttle.SPOOL_TICKS / 2) != 0.5F || Throttle.level(Throttle.SPOOL_TICKS * 3) != 1.0F
                || Throttle.drain(1.0F) != Throttle.FULL_DRAIN || Math.abs(Throttle.drain(1.0F) / Throttle.drain(0.5F) - 8.0F) > 1.0E-4F) {
            helper.fail("Spool should run 0 to 1 over " + Throttle.SPOOL_TICKS + " ticks and cost eight times as much at full as at half");
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
        fit(player, mainArm(player), Module.PISTON_RAM);
        wear(player, 4000);
        if (!Throttle.press(player, mainArm(player), 0) || Throttle.spooling(player) != Module.PISTON_RAM) {
            helper.fail("The throttle should take hold of the Piston Ram");
            return;
        }
        long start = helper.getLevel().getGameTime();
        helper.onEachTick(() -> Throttle.tick(player));
        helper.runAfterDelay(60, () -> {
            float level = Throttle.level(player);
            int spent = 4000 - tank(player);
            // spooling up to full costs about 37 mB, then a second at full 150 more
            if (level != 1.0F || spent < 150 || spent > 240) {
                helper.fail("Three seconds held should be at full spool and have cost about 190 mB, not " + spent + " at " + level
                        + " (" + (helper.getLevel().getGameTime() - start) + " ticks)");
                return;
            }
            Throttle.release(player, true);
            if (Throttle.spooling(player) != null || 4000 - tank(player) != spent + Throttle.TAP) {
                helper.fail("Letting go should fire for the tap's " + Throttle.TAP + " mB and stop");
                return;
            }
            helper.succeed();
        });
    }

    /** With no soul blood the throttle chokes: nothing is held, nothing fires. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void throttleChokesDry(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        fit(player, mainArm(player), Module.PISTON_RAM);
        wear(player, 0);
        if (Throttle.press(player, mainArm(player), 0) || Throttle.spooling(player) != null) {
            helper.fail("A dry tank should choke the throttle");
            return;
        }
        wear(player, 3);
        Throttle.press(player, mainArm(player), 0);
        for (int i = 0; i < 40 && Throttle.spooling(player) != null; i++) {
            Throttle.tick(player);
        }
        helper.succeed();
    }

    /** Modules go into a brass limb at the table, a full limb swaps its first, a wrench takes the last out; the wrong limb takes none. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void modulesFitAtTheTable(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 3), BBBlocks.SURGERY_TABLE.getDefaultState());
        SurgeryTableBlockEntity table = (SurgeryTableBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 2, 3)));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BodyPart arm = mainArm(player);
        fit(player, arm);
        table.put(new ItemStack(BBItems.module(Module.GYROSCOPIC_STABILIZER)));
        if (Surgery.action(BodyEffects.body(player), table.item(), arm) != Surgery.Action.NONE) {
            helper.fail("A leg module should not go in an arm");
            return;
        }
        table.take();
        for (Module module : new Module[]{Module.PISTON_RAM, Module.GRAPPLING_SPOOL}) {
            table.put(new ItemStack(BBItems.module(module)));
            if (Surgery.operate(helper.getLevel(), player, table, arm) != Surgery.Action.FIT_MODULE) {
                helper.fail("An arm module on the table should fit into a brass arm");
                return;
            }
        }
        table.put(new ItemStack(BBItems.module(Module.MAGNET_COIL)));
        Surgery.operate(helper.getLevel(), player, table, arm);
        if (!Modules.of(BodyEffects.body(player).implant(arm)).equals(List.of(Module.MAGNET_COIL, Module.GRAPPLING_SPOOL))
                || player.getInventory().countItem(BBItems.module(Module.PISTON_RAM)) != 1) {
            helper.fail("A full arm should swap its first module, handing it back: " + Modules.of(BodyEffects.body(player).implant(arm)));
            return;
        }
        table.put(new ItemStack(com.simibubi.create.AllItems.WRENCH.get()));
        if (Surgery.operate(helper.getLevel(), player, table, arm) != Surgery.Action.TAKE_MODULE
                || !Modules.of(BodyEffects.body(player).implant(arm)).equals(List.of(Module.MAGNET_COIL))
                || player.getInventory().countItem(BBItems.module(Module.GRAPPLING_SPOOL)) != 1) {
            helper.fail("A wrench on the table should take the last module out");
            return;
        }
        if (BodyEffects.body(player).state(arm) != Body.State.IMPLANT) {
            helper.fail("Changing modules must not take the arm off");
            return;
        }
        helper.succeed();
    }

    /** The Piston Ram: looking down at the ground it launches you, harder at full spool; at a mob it knocks it away. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pistonRamLaunchesAndStrikes(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)), 0.0F, 90.0F);
        fit(player, mainArm(player), Module.PISTON_RAM);
        wear(player, 1000);
        Throttle.press(player, mainArm(player), 0);
        Throttle.release(player, true);
        double tap = player.getDeltaMovement().y;
        if (Math.abs(tap - ModuleActions.LAUNCH) > 1.0E-6) {
            helper.fail("A tap of the ram at the ground should throw you up at " + ModuleActions.LAUNCH + ", not " + tap);
            return;
        }
        player.setDeltaMovement(Vec3.ZERO);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 4));
        pig.setNoAi(true);
        // looking down at the pig's back, not so steeply that the ram strikes the ground
        player.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)), 0.0F, 30.0F);
        float health = pig.getHealth();
        Throttle.press(player, mainArm(player), 0);
        Throttle.release(player, true);
        if (pig.getHealth() >= health || pig.getDeltaMovement().horizontalDistance() < 0.3) {
            helper.fail("The ram should hurt and shove the pig: " + pig.getDeltaMovement());
            return;
        }
        helper.succeed();
    }

    /** The Barometric Vent: a puff up, then a slow drift down that never builds a fall. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void barometricVentHovers(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new Vec3(2.5, 6.0, 2.5)));
        fit(player, BodyPart.LEFT_LEG, Module.BAROMETRIC_VENT);
        wear(player, 1000);
        Throttle.press(player, BodyPart.LEFT_LEG, 0);
        Throttle.release(player, true);
        if (!ModuleActions.hovering(player) || player.getDeltaMovement().y < ModuleActions.HOVER_LIFT - 1.0E-6) {
            helper.fail("Letting the vent go should lift you and start a hover");
            return;
        }
        player.setDeltaMovement(0.0, -1.0, 0.0);
        player.fallDistance = 10.0F;
        long now = helper.getLevel().getGameTime();
        ModuleActions.move(player, true, now - 10, now + 20, Vec3.ZERO, 0.0);
        if (player.getDeltaMovement().y != -ModuleActions.HOVER_SINK || player.fallDistance != 0.0F) {
            helper.fail("A hover should hold the fall to a slow sink and keep no fall: " + player.getDeltaMovement());
            return;
        }
        helper.succeed();
    }

    /** The Gyroscopic Stabilizer pays for a fall by the block; what the tank cannot pay for is taken as damage. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stabilizerPaysForTheFall(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        fit(player, BodyPart.LEFT_LEG, Module.GYROSCOPIC_STABILIZER);
        wear(player, 1000);
        double safe = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        float health = player.getHealth();
        player.causeFallDamage((float) safe + 10.0F, 1.0F, player.damageSources().fall());
        int cost = 10 * ModuleActions.STABILIZER_MB_PER_BLOCK;
        if (player.getHealth() != health || tank(player) != 1000 - cost) {
            helper.fail("Ten blocks past safe should cost " + cost + " mB and no health: tank " + tank(player) + ", health " + player.getHealth());
            return;
        }
        wear(player, cost / 2);
        player.causeFallDamage((float) safe + 10.0F, 1.0F, player.damageSources().fall());
        float taken = health - player.getHealth();
        if (tank(player) != 0 || taken < 4.0F || taken > 6.0F) {
            helper.fail("Half paid should take about half the fall's 10 damage: " + taken);
            return;
        }
        helper.succeed();
    }

    /** The Magnet Coil draws loose items in, always, from a few blocks. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void magnetCoilDrawsItems(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)));
        fit(player, mainArm(player), Module.MAGNET_COIL);
        wear(player, 1000);
        ItemEntity item = new ItemEntity(helper.getLevel(), player.getX() + 2.5, player.getY() + 0.5, player.getZ(), new ItemStack(Items.IRON_INGOT));
        item.setNoPickUpDelay();
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
        ModuleActions.tick(player);
        if (item.getDeltaMovement().x >= -0.1) {
            helper.fail("The coil should draw the item toward you: " + item.getDeltaMovement());
            return;
        }
        helper.succeed();
    }

    /** The Grappling Spool reels a chicken in to you, and reels you in to a wall. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void grapplingSpoolReels(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // looking a little down, at the chicken five blocks off, and past it at the wall
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)), 0.0F, 14.0F);
        fit(player, mainArm(player), Module.GRAPPLING_SPOOL);
        wear(player, 1000);
        Chicken chicken = helper.spawn(EntityType.CHICKEN, new BlockPos(1, 2, 6));
        chicken.setNoAi(true);
        Throttle.press(player, mainArm(player), 0);
        Throttle.release(player, true);
        ModuleActions.tick(player);
        if (chicken.getDeltaMovement().z >= -0.5) {
            helper.fail("A chicken is light: it should be reeled in toward you: " + chicken.getDeltaMovement());
            return;
        }
        chicken.discard();
        ModuleActions.clear(player);
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(new BlockPos(1, y, 7), Blocks.STONE);
        }
        player.setDeltaMovement(Vec3.ZERO);
        Throttle.press(player, mainArm(player), 0);
        Throttle.release(player, true);
        ModuleActions.tick(player);
        if (player.getDeltaMovement().length() < ModuleActions.REEL - 1.0E-6 || player.getDeltaMovement().z < 0.8) {
            helper.fail("A wall is heavy: you should be reeled toward it: " + player.getDeltaMovement());
            return;
        }
        ModuleActions.clear(player);
        helper.succeed();
    }

    /** The Rotational Coupler: looking at a shaft's end, the arm drives it; let go and the shaft stops. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void couplerDrivesAShaft(GameTestHelper helper) {
        BlockPos shaft = new BlockPos(3, 2, 2);
        helper.setBlock(shaft, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // eye level with the shaft's middle, a block and a half west of it, looking east
        Vec3 eye = helper.absoluteVec(new Vec3(1.5, 2.5, 2.5));
        player.moveTo(eye.x, eye.y - player.getEyeHeight(), eye.z, -90.0F, 0.0F);
        fit(player, mainArm(player), Module.ROTATIONAL_COUPLER);
        wear(player, 4000);
        if (Coupler.rpm(0.0F) != Coupler.BASE_RPM || Coupler.rpm(1.0F) != Coupler.MAX_RPM) {
            helper.fail("The coupler should run " + Coupler.BASE_RPM + " to " + Coupler.MAX_RPM + " RPM");
            return;
        }
        Throttle.press(player, mainArm(player), 0);
        helper.onEachTick(() -> Throttle.tick(player));
        helper.runAfterDelay(20, () -> {
            BlockPos coupler = helper.absolutePos(shaft.west());
            if (!helper.getLevel().getBlockState(coupler).is(BBBlocks.COUPLER.get())
                    || !(helper.getLevel().getBlockEntity(helper.absolutePos(shaft)) instanceof KineticBlockEntity kinetic) || kinetic.getSpeed() == 0.0F) {
                helper.fail("Holding the coupler at the shaft's end should drive it");
                return;
            }
            Throttle.release(player, true);
            if (helper.getLevel().getBlockState(coupler).is(BBBlocks.COUPLER.get())) {
                helper.fail("Letting go should take the coupler back out");
                return;
            }
            helper.runAfterDelay(5, () -> {
                if (helper.getLevel().getBlockEntity(helper.absolutePos(shaft)) instanceof KineticBlockEntity k && k.getSpeed() != 0.0F) {
                    helper.fail("With the coupler gone the shaft should stop");
                    return;
                }
                helper.succeed();
            });
        });
    }

    /** The Analytical Lens counts as Create's goggles, while its eye has soul blood. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void analyticalLensIsGoggles(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        fit(player, BodyPart.LEFT_EYE, Module.ANALYTICAL_LENS);
        if (com.simibubi.create.content.equipment.goggles.GogglesItem.isWearingGoggles(player)) {
            helper.fail("A dry lens should see nothing");
            return;
        }
        wear(player, 100);
        if (!com.simibubi.create.content.equipment.goggles.GogglesItem.isWearingGoggles(player)) {
            helper.fail("A running Analytical Lens should count as goggles");
            return;
        }
        helper.succeed();
    }

    /** Four flesh grafts make the flesh set, four brass modules the brass set; both kinds in one body, neither, and no penalty. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void setBonusesNeedFourAndNoMixing(GameTestHelper helper) {
        Player flesh = helper.makeMockPlayer(GameType.SURVIVAL);
        Body body = BodyEffects.body(flesh);
        body.fit(BodyPart.LEFT_ARM, new ItemStack(BBItems.FLESH_ARM.get()));
        body.fit(BodyPart.RIGHT_ARM, new ItemStack(BBItems.FLESH_ARM.get()));
        body.fit(BodyPart.LEFT_LEG, new ItemStack(BBItems.SINEW_LEG.get()));
        if (SetBonus.flesh(body)) {
            helper.fail("Three grafts are not a set");
            return;
        }
        body.fit(BodyPart.RIGHT_LEG, new ItemStack(BBItems.SINEW_LEG.get()));
        if (!SetBonus.flesh(body) || SetBonus.brass(body)) {
            helper.fail("Four grafts should be the flesh set");
            return;
        }
        body.fit(BodyPart.HEART, new ItemStack(BBItems.PUMP_HEART.get()));
        if (SetBonus.flesh(body)) {
            helper.fail("A cybernetic heart among the grafts should cancel the flesh set");
            return;
        }
        Player brass = helper.makeMockPlayer(GameType.SURVIVAL);
        fit(brass, BodyPart.LEFT_ARM, Module.PISTON_RAM, Module.MAGNET_COIL);
        fit(brass, BodyPart.RIGHT_ARM, Module.GRAPPLING_SPOOL);
        if (SetBonus.brass(BodyEffects.body(brass))) {
            helper.fail("Three modules are not a set");
            return;
        }
        fit(brass, BodyPart.LEFT_LEG, Module.GYROSCOPIC_STABILIZER);
        wear(brass, 100);
        BodyEffects.refresh(brass);
        if (!SetBonus.brass(BodyEffects.body(brass)) || brass.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < SetBonus.BRASS_KNOCKBACK_RESISTANCE - 1.0E-6
                || Throttle.efficiency(brass) != SetBonus.BRASS_DRAIN) {
            helper.fail("Four modules should be the brass set: cheaper throttle, hard to shove");
            return;
        }
        BodyEffects.body(brass).fit(BodyPart.STOMACH, new ItemStack(BBItems.FURNACE_STOMACH.get()));
        BodyEffects.refresh(brass);
        if (SetBonus.brass(BodyEffects.body(brass)) || brass.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) != 0.0 || Throttle.efficiency(brass) != 1.0F) {
            helper.fail("A graft among the brass should cancel the set and nothing worse");
            return;
        }
        helper.succeed();
    }
}
