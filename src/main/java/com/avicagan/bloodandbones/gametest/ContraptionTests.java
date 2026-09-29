package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.ShackleHookBlock;
import com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.machine.CarcassMachineBlock;
import com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rule 5 (docs/BRIEF-AUDIT.md package 8): every block moves on a Create contraption and keeps what it holds, nothing drops
 * or doubles on the way, a moved machine loses its drive and takes the drive where it is set down as Create's own do; a
 * carcass hung on a Shackle Hook rides in the hook's data and hangs again where it is set down; and on a Sable ship a hook
 * holds its carcass joined to the ship, a resting carcass is pinned to the deck, and both go where the ship goes.
 * <p>
 * Create's own contraption tests assemble a contraption, move it and take it apart, then look at what arrived. These do
 * the same with a Mechanical Piston two poles long, built in the test (Create's use structure files).
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class ContraptionTests {
    /** The row the piston pushes along: its front block is at (3, ROW, LINE), pushed two blocks east. */
    private static final int ROW = 3;
    private static final int LINE = 5;
    private static final BlockPos FRONT = new BlockPos(3, ROW, LINE);

    // ---------------------------------------------------------------- the piston

    /**
     * A Mechanical Piston two poles long at (2, y, z), facing east, whose motor starts two ticks in: it pushes whatever
     * stands in front of it (and in front of that) two blocks on and sets it down there. Which way a motor turns a piston
     * is not worth guessing: if the front has not moved by tick 60, the motor turns the other way.
     */
    private static void pushTwoEast(GameTestHelper helper, int y, int z) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 1; x++) {
            helper.setBlock(new BlockPos(x, y, z), AllBlocks.PISTON_EXTENSION_POLE.getDefaultState().setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.EAST));
        }
        helper.setBlock(new BlockPos(2, y, z), AllBlocks.MECHANICAL_PISTON.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST).setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
        helper.setBlock(new BlockPos(2, y - 1, z), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        Block front = helper.getBlockState(new BlockPos(3, y, z)).getBlock();
        BlockPos motor = helper.absolutePos(new BlockPos(2, y - 1, z));
        helper.runAfterDelay(2, () -> ((CreativeMotorBlockEntity) level.getBlockEntity(motor)).generatedSpeed.setValue(-64));
        helper.runAfterDelay(60, () -> {
            if (helper.getBlockState(new BlockPos(3, y, z)).is(front)) {
                ((CreativeMotorBlockEntity) level.getBlockEntity(motor)).generatedSpeed.setValue(64);
            }
        });
    }

    /** A Creative Motor at {@code at} facing up, turning at {@code rpm}. */
    private static void motor(GameTestHelper helper, BlockPos at, int rpm) {
        helper.setBlock(at, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        ((CreativeMotorBlockEntity) helper.getBlockEntity(at)).generatedSpeed.setValue(rpm);
    }

    /**
     * Nothing lies about as an item in this test's own ground, nor any of {@code kinds} (what the test put in its blocks)
     * a little way round it, where a moving contraption may have knocked it: nothing dropped or doubled on the way.
     */
    private static void nothingDropped(GameTestHelper helper, net.minecraft.world.level.ItemLike... kinds) {
        List<ItemEntity> items = new ArrayList<>(helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()));
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(3.0))) {
            if (!items.contains(item) && java.util.Arrays.stream(kinds).anyMatch(kind -> item.getItem().is(kind.asItem()))) {
                items.add(item);
            }
        }
        helper.assertTrue(items.isEmpty(), "something dropped on the way: " + items.stream().map(item -> item.getItem().toString()).toList());
    }

    /** No contraption of this test's is still moving. */
    private static void setDown(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds()).isEmpty(), "the contraption is still moving");
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> T be(GameTestHelper helper, BlockPos at, Class<T> type) {
        BlockEntity be = helper.getBlockEntity(at);
        helper.assertTrue(type.isInstance(be), "no " + type.getSimpleName() + " at " + at + ", found " + be);
        return (T) be;
    }

    // ---------------------------------------------------------------- the four machines

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void manglerRidesAContraption(GameTestHelper helper) {
        machineRides(helper, BBBlocks.MANGLER.get());
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void guillotineRidesAContraption(GameTestHelper helper) {
        machineRides(helper, BBBlocks.GUILLOTINE.get());
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void beheaderRidesAContraption(GameTestHelper helper) {
        machineRides(helper, BBBlocks.BEHEADER.get());
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void degloverRidesAContraption(GameTestHelper helper) {
        machineRides(helper, BBBlocks.DEGLOVER.get());
    }

    /**
     * A machine turning on a motor is pushed off it, two blocks on, onto another motor of another speed. As Create's own
     * kinetic blocks do, it leaves its drive and takes the drive where it is set down (it turns at the new motor's speed),
     * and it keeps what it holds: its output, its filter, its stroke count and a Guillotine's wind (which carries on from
     * where it was, never back to nothing).
     */
    private static void machineRides(GameTestHelper helper, CarcassMachineBlock block) {
        BlockPos to = FRONT.east(2);
        motor(helper, FRONT.below(), 16);
        motor(helper, to.below(), 32);
        helper.setBlock(FRONT, block.defaultBlockState());
        CarcassMachineBlockEntity machine = be(helper, FRONT, CarcassMachineBlockEntity.class);
        machine.output.setStackInSlot(0, new ItemStack(Items.BEEF, 5));
        machine.output.setStackInSlot(4, new ItemStack(Items.BONE, 3));
        machine.filtering.setFilter(new ItemStack(Items.COW_SPAWN_EGG));
        machine.strokes = 7;
        machine.wind = 0.4F;
        boolean guillotine = block.kind == com.avicagan.bloodandbones.machine.MachineKind.GUILLOTINE;
        pushTwoEast(helper, ROW, LINE);
        helper.runAfterDelay(1, () -> helper.assertTrue(Math.abs(machine.getSpeed()) == 16, "the machine should turn on its motor first, it turns at " + machine.getSpeed()));
        helper.succeedWhen(() -> {
            setDown(helper);
            helper.assertBlockPresent(block, to);
            helper.assertBlockNotPresent(block, FRONT);
            CarcassMachineBlockEntity moved = be(helper, to, CarcassMachineBlockEntity.class);
            helper.assertTrue(Math.abs(moved.getSpeed()) == 32, "set down on the other motor it should turn at its 32 RPM, it turns at " + moved.getSpeed());
            helper.assertTrue(moved.output.getStackInSlot(0).is(Items.BEEF) && moved.output.getStackInSlot(0).getCount() == 5
                    && moved.output.getStackInSlot(4).is(Items.BONE) && moved.output.getStackInSlot(4).getCount() == 3, "the output changed on the way");
            for (int slot = 0; slot < moved.output.getSlots(); slot++) {
                helper.assertTrue(slot == 0 || slot == 4 || moved.output.getStackInSlot(slot).isEmpty(), "something new in the output: " + moved.output.getStackInSlot(slot));
            }
            helper.assertTrue(moved.filtering.getFilter().is(Items.COW_SPAWN_EGG), "the filter was lost on the way");
            helper.assertTrue(moved.strokes == 7, "it struck on the way, or forgot its strokes: " + moved.strokes);
            if (guillotine) {
                helper.assertTrue(moved.wind >= 0.4F, "the Guillotine lost its wind on the way: " + moved.wind);
            }
            nothingDropped(helper, Items.BEEF, Items.BONE, Items.COW_SPAWN_EGG, block);
        });
    }

    // ---------------------------------------------------------------- racks, spits, jars, tables

    /** The Bleeding Rack arrives with the blood in it, and none of it spills. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void bleedingRackRidesAContraptionWithItsBlood(GameTestHelper helper) {
        helper.setBlock(FRONT, BBBlocks.BLEEDING_RACK.getDefaultState());
        com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity rack = be(helper, FRONT, com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity.class);
        helper.assertTrue(rack.collect(new FluidStack(BBFluids.blood(), 2500), IFluidHandler.FluidAction.EXECUTE) == 2500, "the rack did not take the blood");
        pushTwoEast(helper, ROW, LINE);
        helper.succeedWhen(() -> {
            setDown(helper);
            helper.assertBlockPresent(BBBlocks.BLEEDING_RACK.get(), FRONT.east(2));
            FluidStack blood = be(helper, FRONT.east(2), com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity.class).getFluid();
            helper.assertTrue(blood.getFluid().isSame(BBFluids.blood()) && blood.getAmount() == 2500, "the rack should still hold 2500 mB of blood, it holds " + blood.getAmount());
            for (BlockPos at : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(10, 6, 10))) {
                helper.assertTrue(helper.getBlockState(at).getFluidState().isEmpty(), "blood spilled at " + at);
            }
            nothingDropped(helper, BBBlocks.BLEEDING_RACK.get());
        });
    }

    /**
     * A Spit Roast with a whole cow on it, part cooked, pushes a Specimen Jar with a heart in it: both arrive with what
     * they hold, and the cow does not come off into the world.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void spitRoastAndSpecimenJarRideAContraption(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(FRONT, BBBlocks.SPIT_ROAST.getDefaultState());
        helper.setBlock(FRONT.east(), BBBlocks.SPECIMEN_JAR.getDefaultState());
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(7, 2, 8));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        helper.assertTrue(carcass != null, "Carcass assembly returned null");
        com.avicagan.bloodandbones.cooking.SpitRoastBlockEntity spit = be(helper, FRONT, com.avicagan.bloodandbones.cooking.SpitRoastBlockEntity.class);
        helper.assertTrue(spit.skewer(level, carcass), "the spit did not take the cow");
        spit.progress = 123.0F;
        int pieces = spit.pieces().size();
        ItemStack heart = BBItems.HEART.get().of(net.minecraft.resources.ResourceLocation.withDefaultNamespace("cow"), false);
        helper.assertTrue(be(helper, FRONT.east(), com.avicagan.bloodandbones.cooking.SpecimenJarBlockEntity.class).put(heart.copy()), "the jar did not take the heart");
        pushTwoEast(helper, ROW, LINE);
        helper.succeedWhen(() -> {
            setDown(helper);
            helper.assertBlockPresent(BBBlocks.SPIT_ROAST.get(), FRONT.east(2));
            helper.assertBlockPresent(BBBlocks.SPECIMEN_JAR.get(), FRONT.east(3));
            com.avicagan.bloodandbones.cooking.SpitRoastBlockEntity moved = be(helper, FRONT.east(2), com.avicagan.bloodandbones.cooking.SpitRoastBlockEntity.class);
            helper.assertTrue(moved.whole() && moved.pieces().size() == pieces, "the spit should still hold the whole cow, " + pieces + " pieces; it holds " + moved.pieces().size());
            helper.assertTrue(moved.progress == 123.0F, "the cooking went on or went back on the way: " + moved.progress);
            helper.assertTrue(CarcassSavedData.get(level).carcass(carcass.id) == null, "the cow came off the spit into the world");
            helper.assertTrue(ItemStack.isSameItemSameComponents(be(helper, FRONT.east(3), com.avicagan.bloodandbones.cooking.SpecimenJarBlockEntity.class).specimen(), heart),
                    "the jar lost its heart on the way");
            nothingDropped(helper, BBItems.CARCASS_PIECE.get(), BBItems.HEART.get(), BBBlocks.SPIT_ROAST.get(), BBBlocks.SPECIMEN_JAR.get());
        });
    }

    /** The Butcher's Table arrives with the piece on it and its filter. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void butcherTableRidesAContraption(GameTestHelper helper) {
        helper.setBlock(FRONT, BBBlocks.BUTCHER_TABLE.getDefaultState());
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(7, 2, 8));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        helper.assertTrue(carcass != null, "Carcass assembly returned null");
        ItemStack head = CarcassPieceItem.of(carcass, "head");
        com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity table = be(helper, FRONT, com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity.class);
        table.filtering.setFilter(new ItemStack(Items.COW_SPAWN_EGG));
        helper.assertTrue(table.put(head.copy()), "the table did not take the head");
        pushTwoEast(helper, ROW, LINE);
        helper.succeedWhen(() -> {
            setDown(helper);
            helper.assertBlockPresent(BBBlocks.BUTCHER_TABLE.get(), FRONT.east(2));
            com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity moved = be(helper, FRONT.east(2), com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity.class);
            helper.assertTrue(ItemStack.isSameItemSameComponents(moved.specimen(), head), "the table lost its piece on the way");
            helper.assertTrue(moved.filtering.getFilter().is(Items.COW_SPAWN_EGG), "the table lost its filter on the way");
            nothingDropped(helper, BBItems.CARCASS_PIECE.get(), Items.COW_SPAWN_EGG, BBBlocks.BUTCHER_TABLE.get());
        });
    }

    /**
     * The Surgery Table with its Surgical Rig, a blade laid on it, a filter, and a villager lying on its seat. The table
     * arrives with its rig (only one: the rig is not dropped as well), the blade and the filter; its seat does not go with
     * it (a contraption seats only on Create's own seats), so the patient gets up where the table was and no seat is left
     * behind.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void surgeryTableAndItsSeatRideAContraption(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(FRONT, BBBlocks.SURGERY_TABLE.getDefaultState()
                .setValue(com.avicagan.bloodandbones.body.SurgeryTableBlock.ATTACHMENT, com.avicagan.bloodandbones.body.TableAttachment.SURGICAL));
        com.avicagan.bloodandbones.body.SurgeryTableBlockEntity table = be(helper, FRONT, com.avicagan.bloodandbones.body.SurgeryTableBlockEntity.class);
        table.filtering.setFilter(new ItemStack(Items.COW_SPAWN_EGG));
        helper.assertTrue(table.put(new ItemStack(BBItems.CLEAVER.get())), "the table did not take the Cleaver");
        net.minecraft.world.entity.npc.Villager patient = helper.spawn(EntityType.VILLAGER, FRONT.above());
        patient.setNoAi(true);
        helper.assertTrue(com.avicagan.bloodandbones.body.SurgeryTableBlock.lieDown(level, helper.absolutePos(FRONT), patient), "the villager did not lie down");
        pushTwoEast(helper, ROW, LINE);
        helper.succeedWhen(() -> {
            setDown(helper);
            helper.assertBlockPresent(BBBlocks.SURGERY_TABLE.get(), FRONT.east(2));
            helper.assertBlockProperty(FRONT.east(2), com.avicagan.bloodandbones.body.SurgeryTableBlock.ATTACHMENT, com.avicagan.bloodandbones.body.TableAttachment.SURGICAL);
            com.avicagan.bloodandbones.body.SurgeryTableBlockEntity moved = be(helper, FRONT.east(2), com.avicagan.bloodandbones.body.SurgeryTableBlockEntity.class);
            helper.assertTrue(moved.item().is(BBItems.CLEAVER.get()), "the table lost its Cleaver on the way");
            helper.assertTrue(moved.filtering.getFilter().is(Items.COW_SPAWN_EGG), "the table lost its filter on the way");
            helper.assertTrue(level.getEntitiesOfClass(com.avicagan.bloodandbones.body.SurgerySeatEntity.class, helper.getBounds()).isEmpty(), "a seat was left behind");
            helper.assertTrue(patient.isAlive() && patient.getVehicle() == null, "the patient should get up where the table was");
            nothingDropped(helper, BBItems.SURGICAL_RIG.get(), BBItems.CLEAVER.get(), Items.COW_SPAWN_EGG, BBBlocks.SURGERY_TABLE.get());
        });
    }

    /**
     * A Blood Trough full of blood pushes a Charging Cradle with canisters and sheets in it: both arrive with what they
     * hold, and minions find them where they are now (the per-level lists move with them).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void bloodTroughAndChargingCradleRideAContraption(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(FRONT, BBBlocks.BLOOD_TROUGH.getDefaultState());
        helper.setBlock(FRONT.east(), BBBlocks.CHARGING_CRADLE.getDefaultState());
        com.avicagan.bloodandbones.minion.BloodTroughBlockEntity trough = be(helper, FRONT, com.avicagan.bloodandbones.minion.BloodTroughBlockEntity.class);
        trough.tank().fill(new FluidStack(BBFluids.blood(), 3000), IFluidHandler.FluidAction.EXECUTE);
        com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity cradle = be(helper, FRONT.east(), com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity.class);
        cradle.inventory.setStackInSlot(0, new ItemStack(BBItems.SOUL_CANISTER.get()));
        cradle.inventory.setStackInSlot(1, new ItemStack(BBItems.SOUL_CANISTER.get()));
        cradle.inventory.setStackInSlot(com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity.FULL, new ItemStack(BBItems.EMPTY_SOUL_CANISTER.get()));
        cradle.inventory.setStackInSlot(com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity.SHEETS, new ItemStack(com.simibubi.create.AllItems.BRASS_SHEET.get(), 12));
        pushTwoEast(helper, ROW, LINE);
        helper.succeedWhen(() -> {
            setDown(helper);
            helper.assertBlockPresent(BBBlocks.BLOOD_TROUGH.get(), FRONT.east(2));
            helper.assertBlockPresent(BBBlocks.CHARGING_CRADLE.get(), FRONT.east(3));
            helper.assertTrue(be(helper, FRONT.east(2), com.avicagan.bloodandbones.minion.BloodTroughBlockEntity.class).amount() == 3000, "the trough lost blood on the way");
            com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity moved = be(helper, FRONT.east(3), com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity.class);
            helper.assertTrue(moved.fullCanisters() == 2 && moved.inventory.getStackInSlot(com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity.FULL).is(BBItems.EMPTY_SOUL_CANISTER.get())
                    && moved.inventory.getStackInSlot(com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity.SHEETS).getCount() == 12, "the cradle's canisters or sheets changed on the way");
            helper.assertTrue(com.avicagan.bloodandbones.minion.BloodTroughBlockEntity.all(level).contains(helper.absolutePos(FRONT.east(2)))
                    && !com.avicagan.bloodandbones.minion.BloodTroughBlockEntity.all(level).contains(helper.absolutePos(FRONT)), "minions should find the trough where it is now");
            helper.assertTrue(com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity.all(level).contains(helper.absolutePos(FRONT.east(3)))
                    && !com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity.all(level).contains(helper.absolutePos(FRONT.east())), "minions should find the cradle where it is now");
            nothingDropped(helper, BBItems.SOUL_CANISTER.get(), BBItems.EMPTY_SOUL_CANISTER.get(), com.simibubi.create.AllItems.BRASS_SHEET.get(), BBBlocks.BLOOD_TROUGH.get(), BBBlocks.CHARGING_CRADLE.get());
        });
    }

    /**
     * A Fluid Backtank set down, of the iron tier and full of blood, pushes a Backtank Port: the tank arrives with its tier
     * and its blood (and is not dropped as an item as well), the port still takes pipes.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fluidBacktankAndBacktankPortRideAContraption(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(FRONT, BBBlocks.FLUID_BACKTANK.getDefaultState().setValue(com.avicagan.bloodandbones.backtank.FluidBacktankBlock.TIER,
                com.avicagan.bloodandbones.backtank.BacktankTier.IRON));
        helper.setBlock(FRONT.east(), BBBlocks.BACKTANK_PORT.getDefaultState().setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.UP));
        be(helper, FRONT, com.avicagan.bloodandbones.backtank.FluidBacktankBlockEntity.class).setFluid(new FluidStack(BBFluids.blood(), 3000));
        pushTwoEast(helper, ROW, LINE);
        helper.succeedWhen(() -> {
            setDown(helper);
            helper.assertBlockPresent(BBBlocks.FLUID_BACKTANK.get(), FRONT.east(2));
            helper.assertBlockProperty(FRONT.east(2), com.avicagan.bloodandbones.backtank.FluidBacktankBlock.TIER, com.avicagan.bloodandbones.backtank.BacktankTier.IRON);
            FluidStack blood = be(helper, FRONT.east(2), com.avicagan.bloodandbones.backtank.FluidBacktankBlockEntity.class).tank().getFluid();
            helper.assertTrue(blood.getFluid().isSame(BBFluids.blood()) && blood.getAmount() == 3000, "the backtank should still hold 3000 mB of blood, it holds " + blood.getAmount());
            helper.assertBlockPresent(BBBlocks.BACKTANK_PORT.get(), FRONT.east(3));
            helper.assertTrue(level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, helper.absolutePos(FRONT.east(3)), Direction.UP) != null,
                    "the port should take pipes where it is set down");
            nothingDropped(helper, BBBlocks.FLUID_BACKTANK.get(), BBBlocks.BACKTANK_PORT.get(), BBItems.backtank(com.avicagan.bloodandbones.backtank.BacktankTier.IRON));
        });
    }

    /**
     * Built into a Sable ship (as Aeronautics builds one round them), the blocks that hold things keep them and drop
     * nothing: Sable takes a block's data along and then removes the block from the world with its block entity still
     * there, as a piston would move it, so a block that dropped what it held when removed would double it.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void blocksGoOntoAShipWhole(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(8, 2, 8));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        helper.assertTrue(carcass != null, "Carcass assembly returned null");
        ItemStack head = CarcassPieceItem.of(carcass, "head");
        List<BlockPos> deck = new ArrayList<>();
        for (int x = 1; x <= 7; x++) {
            BlockPos at = new BlockPos(x, 3, 3);
            helper.setBlock(at, Blocks.STONE);
            deck.add(at);
        }
        BlockPos jar = new BlockPos(1, 4, 3);
        BlockPos butcher = new BlockPos(2, 4, 3);
        BlockPos surgery = new BlockPos(3, 4, 3);
        BlockPos backtank = new BlockPos(4, 4, 3);
        BlockPos steelTable = new BlockPos(5, 4, 3);
        BlockPos rack = new BlockPos(6, 4, 3);
        BlockPos machine = new BlockPos(7, 4, 3);
        helper.setBlock(jar, BBBlocks.SPECIMEN_JAR.getDefaultState());
        helper.setBlock(butcher, BBBlocks.BUTCHER_TABLE.getDefaultState());
        helper.setBlock(surgery, BBBlocks.SURGERY_TABLE.getDefaultState()
                .setValue(com.avicagan.bloodandbones.body.SurgeryTableBlock.ATTACHMENT, com.avicagan.bloodandbones.body.TableAttachment.SURGICAL));
        helper.setBlock(backtank, BBBlocks.FLUID_BACKTANK.getDefaultState());
        helper.setBlock(steelTable, BBBlocks.STEEL_TABLE.getDefaultState());
        helper.setBlock(rack, BBBlocks.STEEL_RACK.getDefaultState());
        helper.setBlock(machine, BBBlocks.MANGLER.getDefaultState());
        be(helper, jar, com.avicagan.bloodandbones.cooking.SpecimenJarBlockEntity.class).put(new ItemStack(Items.ROTTEN_FLESH));
        be(helper, butcher, com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity.class).put(head.copy());
        be(helper, surgery, com.avicagan.bloodandbones.body.SurgeryTableBlockEntity.class).put(new ItemStack(BBItems.CLEAVER.get()));
        be(helper, backtank, com.avicagan.bloodandbones.backtank.FluidBacktankBlockEntity.class).setFluid(new FluidStack(BBFluids.blood(), 1000));
        be(helper, steelTable, com.avicagan.bloodandbones.decoration.SteelTableBlockEntity.class).put(new ItemStack(Items.BONE));
        be(helper, rack, com.avicagan.bloodandbones.decoration.SteelRackBlockEntity.class).put(0, new ItemStack(Items.DIAMOND));
        be(helper, machine, CarcassMachineBlockEntity.class).output.setStackInSlot(0, new ItemStack(Items.BEEF, 4));
        be(helper, machine, CarcassMachineBlockEntity.class).filtering.setFilter(new ItemStack(Items.COW_SPAWN_EGG));
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos at : deck) {
            blocks.add(helper.absolutePos(at));
            blocks.add(helper.absolutePos(at.above()));
        }
        ServerSubLevel ship = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level, blocks.get(6), blocks,
                new dev.ryanhcode.sable.companion.math.BoundingBox3i(helper.absolutePos(new BlockPos(1, 3, 3)), helper.absolutePos(new BlockPos(7, 4, 3))));
        helper.assertTrue(ship != null && !ship.isRemoved() && helper.getBlockState(surgery).isAir(), "the blocks did not become a ship");
        CarcassAssembler.bindColliders(level, ship);
        helper.runAfterDelay(5, () -> {
            nothingDropped(helper, Items.ROTTEN_FLESH, BBItems.CARCASS_PIECE.get(), BBItems.CLEAVER.get(), BBItems.SURGICAL_RIG.get(), Items.BONE, Items.DIAMOND, Items.BEEF,
                    Items.COW_SPAWN_EGG, BBItems.backtank(com.avicagan.bloodandbones.backtank.BacktankTier.COPPER));
            Map<Class<?>, BlockEntity> onShip = new java.util.HashMap<>();
            for (var holder : ship.getPlot().getLoadedChunks()) {
                for (BlockEntity be : holder.getChunk().getBlockEntities().values()) {
                    onShip.put(be.getClass(), be);
                }
            }
            helper.assertTrue(onShip.get(com.avicagan.bloodandbones.cooking.SpecimenJarBlockEntity.class) instanceof com.avicagan.bloodandbones.cooking.SpecimenJarBlockEntity j
                    && j.specimen().is(Items.ROTTEN_FLESH), "the jar lost its flesh");
            helper.assertTrue(onShip.get(com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity.class) instanceof com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity t
                    && ItemStack.isSameItemSameComponents(t.specimen(), head), "the Butcher's Table lost its piece");
            helper.assertTrue(onShip.get(com.avicagan.bloodandbones.body.SurgeryTableBlockEntity.class) instanceof com.avicagan.bloodandbones.body.SurgeryTableBlockEntity t
                    && t.item().is(BBItems.CLEAVER.get()), "the Surgery Table lost its Cleaver");
            helper.assertTrue(onShip.get(com.avicagan.bloodandbones.backtank.FluidBacktankBlockEntity.class) instanceof com.avicagan.bloodandbones.backtank.FluidBacktankBlockEntity t
                    && t.tank().getFluidAmount() == 1000, "the backtank lost its blood");
            helper.assertTrue(onShip.get(com.avicagan.bloodandbones.decoration.SteelTableBlockEntity.class) instanceof com.avicagan.bloodandbones.decoration.SteelTableBlockEntity t
                    && t.specimen().is(Items.BONE), "the steel table lost its bone");
            helper.assertTrue(onShip.get(com.avicagan.bloodandbones.decoration.SteelRackBlockEntity.class) instanceof com.avicagan.bloodandbones.decoration.SteelRackBlockEntity r
                    && r.item(0).is(Items.DIAMOND), "the rack lost its diamond");
            helper.assertTrue(onShip.get(CarcassMachineBlockEntity.class) instanceof CarcassMachineBlockEntity m
                    && m.output.getStackInSlot(0).getCount() == 4 && m.filtering.getFilter().is(Items.COW_SPAWN_EGG), "the Mangler lost its beef or its filter");
            SubLevelContainer.getContainer(level).removeSubLevel(ship, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- a carcass on a moving hook

    /**
     * A cow hung on a Shackle Hook under a block that a piston pushes (ARCHITECTURE 3.5): as the contraption is put
     * together the cow goes into the hook's data and out of the world (it does not fall where the hook was), the moving
     * hook's actor has it to draw, and where the hook is set down it hangs again: the same carcass, skinned as it was, every
     * piece back and joined, each where it was on the body, the hook holding it at its tip. Nothing drops, and no second
     * carcass is left behind.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void hungCarcassRidesAContraptionInItsHooksData(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int y = 6;
        BlockPos hookAt = new BlockPos(3, y - 1, LINE);
        BlockPos movedHook = hookAt.east(2);
        helper.setBlock(new BlockPos(3, y, LINE), Blocks.STONE);
        helper.setBlock(hookAt, BBBlocks.SHACKLE_HOOK.getDefaultState().setValue(ShackleHookBlock.FACING, Direction.UP));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, LINE));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        helper.assertTrue(carcass != null, "Carcass assembly returned null");
        UUID id = carcass.id;
        carcass.skinned = true;
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, LINE))));
        player.setOldPosAndRot();
        Map<String, Vector3d> before = new java.util.HashMap<>();
        helper.runAfterDelay(10, () -> {
            ServerSubLevel leg = bodies(helper, level, carcass).get("right_hind_leg");
            helper.assertTrue(CarcassDrag.start(level, player, leg.getPlot().getCenterBlock(), null), "could not start dragging");
            be(helper, hookAt, ShackleHookBlockEntity.class).toggle(level, player);
            helper.assertTrue(be(helper, hookAt, ShackleHookBlockEntity.class).isOccupied(), "the hook did not take the cow");
        });
        // hung and still: where each piece is on the torso, before it goes
        helper.runAfterDelay(150, () -> {
            ShackleHookBlockEntity hook = be(helper, hookAt, ShackleHookBlockEntity.class);
            Vector3d gap = hookGap(level, hook, carcass);
            helper.assertTrue(gap != null && gap.length() < 0.35, "the cow should hang at the hook's tip before it moves, it is " + gap + " off");
            before.putAll(onTorso(helper, level, carcass));
            pushTwoEast(helper, y, LINE);
        });
        boolean[] seenMoving = {false};
        helper.onEachTick(() -> {
            List<AbstractContraptionEntity> moving = level.getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds());
            if (!moving.isEmpty() && !seenMoving[0]) {
                seenMoving[0] = true;
                // on the way: the carcass rides in the hook's data, not in the world
                helper.assertTrue(CarcassSavedData.get(level).carcass(id) == null, "the cow is still in the world while its hook moves");
                boolean drawn = false;
                for (var actor : moving.get(0).getContraption().getActors()) {
                    CompoundTag draw = actor.getRight() == null ? new CompoundTag() : actor.getRight().data.getCompound("Draw");
                    drawn |= draw.getString("Entity").equals("minecraft:cow") && draw.getList("Pieces", net.minecraft.nbt.Tag.TAG_COMPOUND).size() == 5;
                }
                helper.assertTrue(drawn, "the moving hook has nothing to draw");
            }
        });
        helper.succeedWhen(() -> {
            setDown(helper);
            helper.assertTrue(seenMoving[0], "the hook never moved");
            helper.assertBlockPresent(BBBlocks.SHACKLE_HOOK.get(), movedHook);
            ShackleHookBlockEntity hook = be(helper, movedHook, ShackleHookBlockEntity.class);
            CarcassSavedData.Carcass back = CarcassSavedData.get(level).carcass(id);
            helper.assertTrue(back != null && hook.isOccupied() && id.equals(hook.hookedCarcass()), "the hook should hang the same cow again where it was set down");
            helper.assertTrue(back.skinned, "the cow came back unskinned");
            helper.assertTrue(back.bones.size() == 6 && back.joints.size() == 5 && back.liveJoints.size() == 5, "every piece should be back and joined: "
                    + back.bones.keySet() + ", " + back.liveJoints.size() + " live joints");
            Vector3d gap = hookGap(level, hook, back);
            helper.assertTrue(gap != null && gap.length() < 0.35, "the cow should hang at the moved hook's tip, it is " + gap + " off");
            Map<String, Vector3d> after = onTorso(helper, level, back);
            for (Map.Entry<String, Vector3d> piece : before.entrySet()) {
                Vector3d now = after.get(piece.getKey());
                helper.assertTrue(now != null && now.distance(piece.getValue()) < 0.35, piece.getKey() + " should hang where it was on the body, it moved "
                        + (now == null ? "away" : String.format("%.2f", now.distance(piece.getValue()))));
            }
            // no second cow left where the hook was, nothing dropped
            long cows = CarcassSavedData.get(level).all().stream().filter(c -> c.entity.getPath().equals("cow")
                    && CarcassAssembler.boneWorldPosition(level, c, c.rootBone) != null
                    && helper.getBounds().inflate(2).contains(vec(CarcassAssembler.boneWorldPosition(level, c, c.rootBone)))).count();
            helper.assertTrue(cows == 1, "there should be one cow, there are " + cows);
            nothingDropped(helper, BBItems.CARCASS_PIECE.get(), BBBlocks.SHACKLE_HOOK.get());
        });
    }

    /**
     * A Mechanical Piston already out to its full length, with a cow hung on a Shackle Hook under the block at its head,
     * has its motor's speed changed (Create then tries to move it, finds it at its limit and gives up). Create starts the
     * hook's actor before it gives up, which takes the cow into the hook's data and out of the world; the hook, still where
     * it was, must hang it again. (It found itself by where the contraption keeps it, which for a piston with poles out is
     * shifted back by them, so it never did, and the cow was gone for good.)
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void hungCarcassStaysWhenAPistonAtItsLimitGivesUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int y = 6;
        // piston, pole and head out east, a stone at the head and the hook under it
        helper.setBlock(new BlockPos(1, y, LINE), AllBlocks.MECHANICAL_PISTON.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST).setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true)
                .setValue(com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.STATE,
                        com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.PistonState.EXTENDED));
        helper.setBlock(new BlockPos(2, y, LINE), AllBlocks.PISTON_EXTENSION_POLE.getDefaultState().setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(3, y, LINE), AllBlocks.MECHANICAL_PISTON_HEAD.getDefaultState().setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(4, y, LINE), Blocks.STONE);
        BlockPos hookAt = new BlockPos(4, y - 1, LINE);
        helper.setBlock(hookAt, BBBlocks.SHACKLE_HOOK.getDefaultState().setValue(ShackleHookBlock.FACING, Direction.UP));
        BlockPos motorAt = new BlockPos(1, y - 1, LINE);
        motor(helper, motorAt, 0);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(6, 2, LINE));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        helper.assertTrue(carcass != null, "Carcass assembly returned null");
        UUID id = carcass.id;
        helper.runAfterDelay(10, () -> hang(helper, carcass, be(helper, hookAt, ShackleHookBlockEntity.class), new BlockPos(6, 2, LINE)));
        UUID[] torsoBefore = {null};
        helper.runAfterDelay(150, () -> {
            ShackleHookBlockEntity hook = be(helper, hookAt, ShackleHookBlockEntity.class);
            Vector3d gap = hookGap(level, hook, carcass);
            helper.assertTrue(hook.holdsFast() && gap != null && gap.length() < 0.35, "the cow should hang at the hook's tip first, it is " + gap + " off");
            torsoBefore[0] = carcass.bones.get(carcass.rootBone);
            // a speed change: Create tries to move the piston, and gives up (it is out as far as it goes)
            ((CreativeMotorBlockEntity) helper.getBlockEntity(motorAt)).generatedSpeed.setValue(64);
        });
        helper.runAfterDelay(200, () -> {
            setDown(helper);
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, y, LINE));
            helper.assertBlockPresent(BBBlocks.SHACKLE_HOOK.get(), hookAt);
            ShackleHookBlockEntity hook = be(helper, hookAt, ShackleHookBlockEntity.class);
            CarcassSavedData.Carcass back = CarcassSavedData.get(level).carcass(id);
            helper.assertTrue(back != null, "the cow was lost when the piston gave up");
            // made again from the hook's data: Create did start the hook's actor, and took the cow out of the world
            helper.assertTrue(!torsoBefore[0].equals(back.bones.get(back.rootBone)), "the hook's actor never started, so this tested nothing");
            helper.assertTrue(id.equals(hook.hookedCarcass()) && hook.holdsFast(), "the hook should hold the same cow again");
            helper.assertTrue(back.bones.size() == 6 && back.liveJoints.size() == 5, "every piece should be back and joined: " + back.bones.keySet());
            Vector3d gap = hookGap(level, hook, back);
            helper.assertTrue(gap != null && gap.length() < 0.35, "the cow should hang at the hook's tip again, it is " + gap + " off");
            nothingDropped(helper, BBItems.CARCASS_PIECE.get(), BBBlocks.SHACKLE_HOOK.get());
            helper.succeed();
        });
    }

    /**
     * A real Mechanical Bearing turns a post with an arm and a Shackle Hook under the arm, a cow hung on it, about a
     * quarter round and stops (ARCHITECTURE 3.5): the cow rides in the hook's data and hangs again from the hook where it
     * was set down, whole, with its belly turned as far as the bearing turned the hook. Which way the bearing turns is
     * read from where the hook ends up, not assumed.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void turnedHookTurnsItsCarcass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bearingAt = new BlockPos(5, 2, 5);
        BlockPos motorAt = bearingAt.below();
        helper.setBlock(motorAt, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        helper.setBlock(bearingAt, AllBlocks.MECHANICAL_BEARING.getDefaultState().setValue(com.simibubi.create.content.contraptions.bearing.BearingBlock.FACING, Direction.UP));
        for (int y = 3; y <= 6; y++) {
            helper.setBlock(new BlockPos(5, y, 5), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(6, 6, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(7, 6, 5), Blocks.STONE);
        BlockPos hookAt = new BlockPos(7, 5, 5);
        helper.setBlock(hookAt, BBBlocks.SHACKLE_HOOK.getDefaultState().setValue(ShackleHookBlock.FACING, Direction.UP));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(8, 2, 5));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        helper.assertTrue(carcass != null, "Carcass assembly returned null");
        UUID id = carcass.id;
        // hung by someone standing east of it: its belly faces east
        helper.runAfterDelay(10, () -> hang(helper, carcass, be(helper, hookAt, ShackleHookBlockEntity.class), new BlockPos(9, 2, 5)));
        Vector3d[] belly = {null};
        helper.runAfterDelay(150, () -> {
            ShackleHookBlockEntity hook = be(helper, hookAt, ShackleHookBlockEntity.class);
            helper.assertTrue(hook.holdsFast(), "the hook should hold the cow before the bearing turns");
            belly[0] = bodies(helper, level, carcass).get(carcass.rootBone).logicalPose().orientation().transform(new Vector3d(0, 0, -1));
            // 16 RPM turns a bearing 4.8 degrees a tick: stopped some 19 ticks after it starts, it sets down a quarter round
            ((CreativeMotorBlockEntity) helper.getBlockEntity(motorAt)).generatedSpeed.setValue(16);
        });
        boolean[] seenMoving = {false};
        helper.onEachTick(() -> seenMoving[0] |= !level.getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds()).isEmpty());
        helper.runAfterDelay(170, () -> ((CreativeMotorBlockEntity) helper.getBlockEntity(motorAt)).generatedSpeed.setValue(0));
        helper.runAfterDelay(230, () -> {
            setDown(helper);
            helper.assertTrue(seenMoving[0], "the bearing never turned");
            helper.assertBlockNotPresent(BBBlocks.SHACKLE_HOOK.get(), hookAt);
            BlockPos movedHook = null;
            for (BlockPos at : new BlockPos[]{new BlockPos(5, 5, 7), new BlockPos(5, 5, 3)}) {
                if (helper.getBlockState(at).is(BBBlocks.SHACKLE_HOOK.get())) {
                    movedHook = at;
                }
            }
            helper.assertTrue(movedHook != null, "the hook should be set down a quarter round from where it was");
            // the turn the bearing made, about the post: east of it to south (clockwise from above) or to north
            double turn = movedHook.getZ() > 5 ? Math.PI / 2 : -Math.PI / 2;
            ShackleHookBlockEntity hook = be(helper, movedHook, ShackleHookBlockEntity.class);
            CarcassSavedData.Carcass back = CarcassSavedData.get(level).carcass(id);
            helper.assertTrue(back != null && id.equals(hook.hookedCarcass()) && hook.holdsFast(), "the moved hook should hold the same cow again");
            helper.assertTrue(back.bones.size() == 6 && back.liveJoints.size() == 5, "every piece should be back and joined: " + back.bones.keySet());
            Vector3d gap = hookGap(level, hook, back);
            helper.assertTrue(gap != null && gap.length() < 0.35, "the cow should hang at the moved hook's tip, it is " + gap + " off");
            // a turn about the upright taking east to south is -90 degrees about +y (x to z)
            Vector3d expected = new org.joml.Quaterniond().rotateY(-turn).transform(new Vector3d(belly[0]));
            Vector3d now = bodies(helper, level, back).get(back.rootBone).logicalPose().orientation().transform(new Vector3d(0, 0, -1));
            double angle = Math.toDegrees(new Vector3d(now.x, 0, now.z).angle(new Vector3d(expected.x, 0, expected.z)));
            helper.assertTrue(angle < 25.0, "its belly should face the way the bearing turned it; it is " + String.format("%.0f", angle) + " degrees off");
            nothingDropped(helper, BBItems.CARCASS_PIECE.get(), BBBlocks.SHACKLE_HOOK.get());
            helper.succeed();
        });
    }

    /** How far the hooked point of the hook's carcass is from its tip in the world, or null. */
    private static Vector3d hookGap(ServerLevel level, ShackleHookBlockEntity hook, CarcassSavedData.Carcass carcass) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (hook.hookedSubLevel() == null || !(container.getSubLevel(hook.hookedSubLevel()) instanceof ServerSubLevel torso) || torso.isRemoved()) {
            return null;
        }
        Vector3d at = torso.logicalPose().transformPosition(new Vector3d(hook.hookedAnchor()), new Vector3d());
        return at.sub(hook.tipWorld(level));
    }

    /** Every piece's middle in the torso's own frame. */
    private static Map<String, Vector3d> onTorso(GameTestHelper helper, ServerLevel level, CarcassSavedData.Carcass carcass) {
        Map<String, ServerSubLevel> bodies = bodies(helper, level, carcass);
        ServerSubLevel torso = bodies.get(carcass.rootBone);
        Map<String, Vector3d> out = new java.util.HashMap<>();
        for (Map.Entry<String, ServerSubLevel> body : bodies.entrySet()) {
            out.put(body.getKey(), torso.logicalPose().transformPositionInverse(new Vector3d(body.getValue().logicalPose().position()), new Vector3d())
                    .sub(torso.logicalPose().transformPositionInverse(new Vector3d(torso.logicalPose().position()), new Vector3d())));
        }
        return out;
    }

    private static Map<String, ServerSubLevel> bodies(GameTestHelper helper, ServerLevel level, CarcassSavedData.Carcass carcass) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        Map<String, ServerSubLevel> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, UUID> bone : carcass.bones.entrySet()) {
            if (container.getSubLevel(bone.getValue()) instanceof ServerSubLevel body && !body.isRemoved()) {
                out.put(bone.getKey(), body);
            } else {
                helper.fail("Bone " + bone.getKey() + " has no live body");
            }
        }
        return out;
    }

    private static Vec3 vec(Vector3d v) {
        return new Vec3(v.x, v.y, v.z);
    }

    // ---------------------------------------------------------------- Sable ships

    /** A Sable ship made of the blocks at {@code at} (relative), with colliders now, as a body is given them. */
    private static ServerSubLevel ship(GameTestHelper helper, List<BlockPos> at) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> blocks = at.stream().map(helper::absolutePos).toList();
        BlockPos min = blocks.get(0);
        BlockPos max = blocks.get(0);
        for (BlockPos p : blocks) {
            min = new BlockPos(Math.min(min.getX(), p.getX()), Math.min(min.getY(), p.getY()), Math.min(min.getZ(), p.getZ()));
            max = new BlockPos(Math.max(max.getX(), p.getX()), Math.max(max.getY(), p.getY()), Math.max(max.getZ(), p.getZ()));
        }
        ServerSubLevel ship = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level, blocks.get(0), blocks,
                new dev.ryanhcode.sable.companion.math.BoundingBox3i(min, max));
        helper.assertTrue(ship != null && !ship.isRemoved() && helper.getBlockState(at.get(0)).isAir(), "the blocks did not become a ship");
        CarcassAssembler.bindColliders(level, ship);
        return ship;
    }

    /** The Shackle Hook built into a ship. */
    private static ShackleHookBlockEntity hookOn(GameTestHelper helper, ServerSubLevel ship) {
        for (var holder : ship.getPlot().getLoadedChunks()) {
            for (BlockEntity be : holder.getChunk().getBlockEntities().values()) {
                if (be instanceof ShackleHookBlockEntity hook) {
                    return hook;
                }
            }
        }
        helper.fail("no Shackle Hook on the ship");
        return null;
    }

    /** A stand-in player drags a carcass by its hind leg to a hook and hangs it there. */
    private static void hang(GameTestHelper helper, CarcassSavedData.Carcass carcass, ShackleHookBlockEntity hook, BlockPos standAt) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(standAt)));
        player.setOldPosAndRot();
        ServerSubLevel leg = bodies(helper, level, carcass).get("right_hind_leg");
        helper.assertTrue(CarcassDrag.start(level, player, leg.getPlot().getCenterBlock(), null), "could not start dragging");
        hook.toggle(level, player);
        helper.assertTrue(hook.isOccupied() && !CarcassDrag.isDragging(player), "the hook did not take the cow");
    }

    /**
     * A Shackle Hook under a ship's deck holds a cow joined to the ship (the checks, 15.18, found that it could not: its
     * joint was made to the world at the tip's position in the ship's plot, which Sable refuses). It hoists the cow up as
     * a hook in the world does and holds it at the tip.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void hookOnAShipHoldsItsCarcass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 6, z), Blocks.STONE);
                blocks.add(new BlockPos(x, 6, z));
            }
        }
        // the deck stands on four posts of the world, the hook hangs under its middle
        for (int[] corner : new int[][]{{2, 2}, {2, 6}, {6, 2}, {6, 6}}) {
            for (int y = 2; y <= 5; y++) {
                helper.setBlock(new BlockPos(corner[0], y, corner[1]), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(4, 5, 4), BBBlocks.SHACKLE_HOOK.getDefaultState().setValue(ShackleHookBlock.FACING, Direction.UP));
        blocks.add(new BlockPos(4, 5, 4));
        ServerSubLevel ship = ship(helper, blocks);
        ShackleHookBlockEntity hook = hookOn(helper, ship);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(4, 2, 4));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        helper.assertTrue(carcass != null, "Carcass assembly returned null");
        helper.runAfterDelay(10, () -> hang(helper, carcass, hook, new BlockPos(4, 2, 6)));
        helper.runAfterDelay(160, () -> {
            helper.assertTrue(hook.ship(level) == ship, "the hook should know its ship");
            helper.assertTrue(hook.isOccupied() && hook.holdsFast(), "the hook should hold the cow fast by a joint to the ship");
            Vector3d gap = hookGap(level, hook, carcass);
            helper.assertTrue(gap != null && gap.length() < 0.35, "the cow should hang at the tip under the deck, it is " + gap + " off");
            Vector3d torso = CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
            helper.assertTrue(torso != null && torso.y < hook.tipWorld(level).y - 0.3, "the cow should hang below the hook");
            SubLevelContainer.getContainer(level).removeSubLevel(ship, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            helper.succeed();
        });
    }

    /**
     * A cow lying on a ship's deck, with nothing of the world under it, rests there: the deck's blocks hold it up
     * (CarcassRest.isSupported read only the world's, so it unfolded as soon as it folded), and it is pinned to the deck,
     * not to the world.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void restingCarcassIsPinnedToItsDeck(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 2; x <= 7; x++) {
            for (int z = 2; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
                blocks.add(new BlockPos(x, 3, z));
            }
        }
        for (int[] corner : new int[][]{{2, 2}, {2, 7}, {7, 2}, {7, 7}}) {
            helper.setBlock(new BlockPos(corner[0], 2, corner[1]), Blocks.STONE);
        }
        ServerSubLevel ship = ship(helper, blocks);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(4, 5, 4));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        helper.assertTrue(carcass != null, "Carcass assembly returned null");
        int[] restingFor = {0};
        helper.onEachTick(() -> restingFor[0] = carcass.resting ? restingFor[0] + 1 : 0);
        helper.succeedWhen(() -> {
            // resting for longer than the support check's second, twice over
            helper.assertTrue(restingFor[0] > 50, "the cow should rest on the deck (resting for " + restingFor[0] + " ticks)");
            helper.assertTrue(ship.getUniqueId().equals(carcass.restDeck), "it should be pinned to the deck, not the world");
            Vector3d torso = CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
            helper.assertTrue(helper.getLevel().getBlockState(BlockPos.containing(torso.x, torso.y - 0.8, torso.z)).isAir(), "there should be no world block under it");
            SubLevelContainer.getContainer(level).removeSubLevel(ship, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        });
    }

    /**
     * A ship that moves: a cow resting on its deck and a cow hung from a Shackle Hook on its frame, then the ship driven
     * four blocks east. Both go with it: the resting one stays where it lay on the deck, still resting, and the hung one
     * stays on its hook at the tip where the ship has carried it.
     */
    @GameTest(template = "empty_wide", timeoutTicks = 700)
    public static void carcassesRideAMovingShip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int z0 = 14;
        List<BlockPos> blocks = new ArrayList<>();
        // the deck lies on the floor and slides on it as it goes
        for (int x = 3; x <= 8; x++) {
            for (int z = z0; z <= z0 + 5; z++) {
                blocks.add(new BlockPos(x, 2, z));
            }
        }
        // a gallows at the back: two posts and a beam, the hook under the beam
        for (int y = 3; y <= 5; y++) {
            blocks.add(new BlockPos(3, y, z0));
            blocks.add(new BlockPos(8, y, z0));
        }
        for (int x = 3; x <= 8; x++) {
            blocks.add(new BlockPos(x, 6, z0));
        }
        for (BlockPos at : blocks) {
            helper.setBlock(at, Blocks.STONE);
        }
        BlockPos hookAt = new BlockPos(5, 5, z0);
        helper.setBlock(hookAt, BBBlocks.SHACKLE_HOOK.getDefaultState().setValue(ShackleHookBlock.FACING, Direction.UP));
        blocks.add(hookAt);
        ServerSubLevel ship = ship(helper, blocks);
        ShackleHookBlockEntity hook = hookOn(helper, ship);
        Cow lyingCow = helper.spawn(EntityType.COW, new BlockPos(6, 4, z0 + 3));
        CarcassSavedData.Carcass lying = CarcassAssembler.assemble(lyingCow, null);
        lyingCow.discard();
        Cow hungCow = helper.spawn(EntityType.COW, new BlockPos(5, 4, z0 + 1));
        CarcassSavedData.Carcass hung = CarcassAssembler.assemble(hungCow, null);
        hungCow.discard();
        helper.assertTrue(lying != null && hung != null, "Carcass assembly returned null");
        helper.runAfterDelay(10, () -> hang(helper, hung, hook, new BlockPos(5, 3, z0 + 3)));
        Vector3d[] lyingOnDeck = {null};
        Vector3d[] shipFrom = {null};
        int drive = 60;
        helper.runAfterDelay(260, () -> {
            helper.assertTrue(lying.resting && ship.getUniqueId().equals(lying.restDeck), "the lying cow should rest pinned to the deck before the ship moves");
            Vector3d gap = hookGap(level, hook, hung);
            helper.assertTrue(gap != null && gap.length() < 0.35, "the hung cow should hang at the tip before the ship moves, it is " + gap + " off");
            lyingOnDeck[0] = ship.logicalPose().transformPositionInverse(CarcassAssembler.boneWorldPosition(level, lying, lying.rootBone), new Vector3d());
            shipFrom[0] = new Vector3d(ship.logicalPose().position());
        });
        // drive it: four blocks east over three seconds, kept level
        for (int t = 261; t < 261 + drive; t++) {
            helper.runAfterDelay(t, () -> {
                dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship);
                Vector3d v = handle.getLinearVelocity(new Vector3d());
                Vector3d w = handle.getAngularVelocity(new Vector3d());
                handle.addLinearAndAngularVelocity(new Vector3d(4.0 * 20.0 / drive - v.x, 0.0, -v.z), new Vector3d(w).negate());
            });
        }
        helper.runAfterDelay(261 + drive + 30, () -> {
            double moved = ship.logicalPose().position().x - shipFrom[0].x;
            helper.assertTrue(moved > 3.0, "the ship should have gone some four blocks east, it went " + moved);
            helper.assertTrue(lying.resting, "the lying cow should still rest");
            Vector3d onDeck = ship.logicalPose().transformPositionInverse(CarcassAssembler.boneWorldPosition(level, lying, lying.rootBone), new Vector3d());
            helper.assertTrue(onDeck.distance(lyingOnDeck[0]) < 0.25, "the lying cow should lie where it lay on the deck, it slid " + onDeck.distance(lyingOnDeck[0]));
            Vector3d gap = hookGap(level, hook, hung);
            helper.assertTrue(hook.isOccupied() && hook.holdsFast() && gap != null && gap.length() < 0.5, "the hung cow should hang at the tip where the ship took it, it is "
                    + gap + " off");
            SubLevelContainer.getContainer(level).removeSubLevel(ship, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            helper.succeed();
        });
    }

    /** Drive a ship east at {@code speed} blocks a second, kept level, every tick from {@code from} for {@code ticks}. */
    private static void drive(GameTestHelper helper, java.util.function.Supplier<ServerSubLevel> shipLater, int from, int ticks, double speed) {
        for (int t = from; t < from + ticks; t++) {
            helper.runAfterDelay(t, () -> {
                ServerSubLevel ship = shipLater.get();
                if (ship == null || ship.isRemoved()) {
                    return;
                }
                dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship);
                Vector3d v = handle.getLinearVelocity(new Vector3d());
                Vector3d w = handle.getAngularVelocity(new Vector3d());
                handle.addLinearAndAngularVelocity(new Vector3d(speed - v.x, 0.0, -v.z), new Vector3d(w).negate());
            });
        }
    }

    /** A deck lying on the floor with a gallows at its back, the hook under the gallows' beam; the hook's place is last. */
    private static List<BlockPos> gallowsDeck(GameTestHelper helper, int z0) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 3; x <= 8; x++) {
            for (int z = z0; z <= z0 + 5; z++) {
                blocks.add(new BlockPos(x, 2, z));
            }
        }
        for (int y = 3; y <= 5; y++) {
            blocks.add(new BlockPos(3, y, z0));
            blocks.add(new BlockPos(8, y, z0));
        }
        for (int x = 3; x <= 8; x++) {
            blocks.add(new BlockPos(x, 6, z0));
        }
        for (BlockPos at : blocks) {
            helper.setBlock(at, Blocks.STONE);
        }
        BlockPos hookAt = new BlockPos(5, 5, z0);
        helper.setBlock(hookAt, BBBlocks.SHACKLE_HOOK.getDefaultState().setValue(ShackleHookBlock.FACING, Direction.UP));
        blocks.add(hookAt);
        return blocks;
    }

    /**
     * A ship built round a Shackle Hook that already holds a cow (Aeronautics builds a ship out of blocks where they
     * stand): the hook, read back in the ship's plot, keeps the cow it held (its body hangs where the tip still is), holds
     * it fast joined to the ship, and takes it along when the ship is driven.
     */
    @GameTest(template = "empty_wide", timeoutTicks = 500)
    public static void shipBuiltRoundAHungHookKeepsItsCarcass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int z0 = 14;
        List<BlockPos> blocks = gallowsDeck(helper, z0);
        BlockPos hookAt = blocks.get(blocks.size() - 1);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 4, z0 + 1));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        helper.assertTrue(carcass != null, "Carcass assembly returned null");
        UUID id = carcass.id;
        helper.runAfterDelay(10, () -> hang(helper, carcass, be(helper, hookAt, ShackleHookBlockEntity.class), new BlockPos(5, 3, z0 + 3)));
        ServerSubLevel[] ship = {null};
        Vector3d[] shipFrom = {null};
        helper.runAfterDelay(160, () -> {
            ShackleHookBlockEntity hook = be(helper, hookAt, ShackleHookBlockEntity.class);
            Vector3d gap = hookGap(level, hook, carcass);
            helper.assertTrue(hook.holdsFast() && gap != null && gap.length() < 0.35, "the cow should hang at the world hook's tip first, it is " + gap + " off");
            ship[0] = ship(helper, blocks);
            shipFrom[0] = new Vector3d(ship[0].logicalPose().position());
        });
        helper.runAfterDelay(170, () -> {
            ShackleHookBlockEntity hook = hookOn(helper, ship[0]);
            helper.assertTrue(id.equals(hook.hookedCarcass()) && hook.holdsFast(), "the hook on the new ship should still hold its cow, fast");
            Vector3d gap = hookGap(level, hook, carcass);
            helper.assertTrue(gap != null && gap.length() < 0.35, "the cow should hang at the ship's hook's tip, it is " + gap + " off");
        });
        drive(helper, () -> ship[0], 171, 60, 4.0 * 20.0 / 60);
        helper.runAfterDelay(261, () -> {
            double moved = ship[0].logicalPose().position().x - shipFrom[0].x;
            helper.assertTrue(moved > 3.0, "the ship should have gone some four blocks east, it went " + moved);
            ShackleHookBlockEntity hook = hookOn(helper, ship[0]);
            Vector3d gap = hookGap(level, hook, carcass);
            helper.assertTrue(id.equals(hook.hookedCarcass()) && hook.holdsFast() && gap != null && gap.length() < 0.5,
                    "the cow should hang at the tip where the ship took it, it is " + gap + " off");
            SubLevelContainer.getContainer(level).removeSubLevel(ship[0], dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            helper.succeed();
        });
    }

    /**
     * A cow resting on the floor (pinned to the world) that a ship is then built out of, as a ship is assembled again at
     * a dock: within the second its pin moves to the deck, and when the ship is driven the cow goes with it, still resting,
     * where it lay on the deck. (Pinned only once, when it folded, it stayed pinned to the world, hung in the air as the
     * deck moved out from under it, and dropped once none of the deck was left under it.)
     */
    @GameTest(template = "empty_wide", timeoutTicks = 600)
    public static void restingCarcassGoesWithAShipBuiltUnderIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int z0 = 14;
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 3; x <= 8; x++) {
            for (int z = z0; z <= z0 + 5; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                blocks.add(new BlockPos(x, 2, z));
            }
        }
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 4, z0 + 2));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        helper.assertTrue(carcass != null, "Carcass assembly returned null");
        ServerSubLevel[] ship = {null};
        Vector3d[] onDeck = {null};
        Vector3d[] shipFrom = {null};
        helper.runAfterDelay(250, () -> {
            helper.assertTrue(carcass.resting && carcass.restDeck == null, "the cow should rest pinned to the world first");
            ship[0] = ship(helper, blocks);
        });
        helper.runAfterDelay(280, () -> {
            helper.assertTrue(carcass.resting && ship[0].getUniqueId().equals(carcass.restDeck), "the cow should be pinned to the deck built under it");
            onDeck[0] = ship[0].logicalPose().transformPositionInverse(CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone), new Vector3d());
            shipFrom[0] = new Vector3d(ship[0].logicalPose().position());
        });
        drive(helper, () -> ship[0], 281, 60, 4.0 * 20.0 / 60);
        helper.runAfterDelay(371, () -> {
            double moved = ship[0].logicalPose().position().x - shipFrom[0].x;
            helper.assertTrue(moved > 3.0, "the ship should have gone some four blocks east, it went " + moved);
            helper.assertTrue(carcass.resting && ship[0].getUniqueId().equals(carcass.restDeck), "the cow should still rest, pinned to the deck");
            Vector3d now = ship[0].logicalPose().transformPositionInverse(CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone), new Vector3d());
            helper.assertTrue(now.distance(onDeck[0]) < 0.25, "the cow should lie where it lay on the deck, it slid " + now.distance(onDeck[0]));
            SubLevelContainer.getContainer(level).removeSubLevel(ship[0], dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            helper.succeed();
        });
    }

    /**
     * A cow dropped onto a ship that is already under way: it lies still on the deck, though not in the world, and rests
     * there, pinned to the deck, while the ship is still moving; then it stays where it lies on the deck as the ship goes
     * on. (Its stillness was measured in the world, so on a moving deck it never rested, and stayed a whole ragdoll held
     * on only by friction.)
     */
    @GameTest(template = "empty_wide", timeoutTicks = 420)
    public static void carcassDroppedOnAMovingShipRestsOnIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int z0 = 13;
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 1; x <= 7; x++) {
            for (int z = z0; z <= z0 + 6; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                blocks.add(new BlockPos(x, 2, z));
            }
        }
        // the middle of the deck, in its plot, to drop the cow over wherever the deck has got to
        BlockPos middle = helper.absolutePos(new BlockPos(4, 2, z0 + 3));
        Vector3d middleWorld = new Vector3d(middle.getX() + 0.5, middle.getY() + 0.5, middle.getZ() + 0.5);
        ServerSubLevel ship = ship(helper, blocks);
        Vector3d plotMiddle = ship.logicalPose().transformPositionInverse(middleWorld, new Vector3d());
        // one block a second, for as long as the test runs (twenty blocks at most)
        double speed = 1.0;
        drive(helper, () -> ship, 1, 400, speed);
        CarcassSavedData.Carcass[] carcass = {null};
        helper.runAfterDelay(40, () -> {
            Vector3d over = ship.logicalPose().transformPosition(plotMiddle, new Vector3d()).add(0.0, 2.5, 0.0);
            helper.assertTrue(dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship).getLinearVelocity(new Vector3d()).x > speed * 0.5, "the ship should be under way");
            Cow cow = helper.spawn(EntityType.COW, helper.relativeVec(new Vec3(over.x, over.y, over.z)));
            carcass[0] = CarcassAssembler.assemble(cow, null);
            cow.discard();
            helper.assertTrue(carcass[0] != null, "Carcass assembly returned null");
        });
        double[] restedAt = {-1.0};
        Vector3d[] onDeck = {null};
        int[] restingFor = {0};
        helper.onEachTick(() -> {
            if (carcass[0] == null || !carcass[0].resting) {
                restingFor[0] = 0;
                return;
            }
            if (restingFor[0]++ == 0 && restedAt[0] < 0) {
                // how fast the ship was going when it came to rest, and where on the deck it lay
                restedAt[0] = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship).getLinearVelocity(new Vector3d()).x;
                onDeck[0] = ship.logicalPose().transformPositionInverse(CarcassAssembler.boneWorldPosition(level, carcass[0], carcass[0].rootBone), new Vector3d());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(restingFor[0] > 40, "the cow should rest on the moving deck (resting for " + restingFor[0] + " ticks)");
            helper.assertTrue(restedAt[0] > speed * 0.5, "it should have come to rest while the ship was moving, the ship went " + restedAt[0] + " blocks a second");
            helper.assertTrue(ship.getUniqueId().equals(carcass[0].restDeck), "it should be pinned to the deck");
            Vector3d now = ship.logicalPose().transformPositionInverse(CarcassAssembler.boneWorldPosition(level, carcass[0], carcass[0].rootBone), new Vector3d());
            helper.assertTrue(now.distance(onDeck[0]) < 0.25, "it should stay where it lies on the deck, it slid " + now.distance(onDeck[0]));
            SubLevelContainer.getContainer(level).removeSubLevel(ship, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        });
    }
}
