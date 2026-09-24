package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity;
import com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity;
import com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity;
import com.avicagan.bloodandbones.client.BleedingRackRenderer;
import com.avicagan.bloodandbones.client.CarcassPartRenderer;
import com.avicagan.bloodandbones.client.ShackleHookRenderer;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class BBBlockEntities {
    public static final BlockEntityEntry<CarcassPartBlockEntity> CARCASS_PART = BloodAndBones.REGISTRATE
            .blockEntity("carcass_part", CarcassPartBlockEntity::new)
            .validBlock(BBBlocks.CARCASS_PART)
            .renderer(() -> CarcassPartRenderer::new)
            .register();

    public static final BlockEntityEntry<ShackleHookBlockEntity> SHACKLE_HOOK = BloodAndBones.REGISTRATE
            .blockEntity("shackle_hook", ShackleHookBlockEntity::new)
            .validBlock(BBBlocks.SHACKLE_HOOK)
            .renderer(() -> ShackleHookRenderer::new)
            .register();

    public static final BlockEntityEntry<BleedingRackBlockEntity> BLEEDING_RACK = BloodAndBones.REGISTRATE
            .blockEntity("bleeding_rack", BleedingRackBlockEntity::new)
            .validBlock(BBBlocks.BLEEDING_RACK)
            .renderer(() -> BleedingRackRenderer::new)
            .register();

    public static final BlockEntityEntry<com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity> CARCASS_MACHINE = BloodAndBones.REGISTRATE
            .blockEntity("carcass_machine", com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity::new)
            .visual(() -> com.avicagan.bloodandbones.machine.CarcassMachineVisual::new, true)
            .validBlocks(BBBlocks.MANGLER, BBBlocks.GUILLOTINE, BBBlocks.BEHEADER, BBBlocks.DEGLOVER)
            .renderer(() -> com.avicagan.bloodandbones.machine.CarcassMachineRenderer::new)
            .register();

    public static final BlockEntityEntry<com.avicagan.bloodandbones.cooking.SpitRoastBlockEntity> SPIT_ROAST = BloodAndBones.REGISTRATE
            .blockEntity("spit_roast", com.avicagan.bloodandbones.cooking.SpitRoastBlockEntity::new)
            .visual(() -> com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual::shaft, true)
            .validBlocks(BBBlocks.SPIT_ROAST)
            .renderer(() -> com.avicagan.bloodandbones.cooking.SpitRoastRenderer::new)
            .register();

    public static final BlockEntityEntry<com.avicagan.bloodandbones.cyber.CouplerBlockEntity> COUPLER = BloodAndBones.REGISTRATE
            .blockEntity("coupler", com.avicagan.bloodandbones.cyber.CouplerBlockEntity::new)
            .visual(() -> com.avicagan.bloodandbones.cyber.CouplerVisual::new, true)
            .validBlocks(BBBlocks.COUPLER)
            .renderer(() -> com.avicagan.bloodandbones.cyber.CouplerRenderer::new)
            .register();

    public static final BlockEntityEntry<com.avicagan.bloodandbones.cooking.SpecimenJarBlockEntity> SPECIMEN_JAR = BloodAndBones.REGISTRATE
            .blockEntity("specimen_jar", com.avicagan.bloodandbones.cooking.SpecimenJarBlockEntity::new)
            .validBlocks(BBBlocks.SPECIMEN_JAR)
            .renderer(() -> com.avicagan.bloodandbones.cooking.SpecimenJarRenderer::new)
            .register();

    public static final BlockEntityEntry<com.avicagan.bloodandbones.cooking.ButcherHookBlockEntity> BUTCHER_HOOK = BloodAndBones.REGISTRATE
            .blockEntity("butcher_hook", com.avicagan.bloodandbones.cooking.ButcherHookBlockEntity::new)
            .validBlocks(BBBlocks.BUTCHER_HOOK)
            .renderer(() -> com.avicagan.bloodandbones.cooking.ButcherHookRenderer::new)
            .register();

    public static final BlockEntityEntry<com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity> BUTCHER_TABLE = BloodAndBones.REGISTRATE
            .blockEntity("butcher_table", com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity::new)
            .validBlocks(BBBlocks.BUTCHER_TABLE)
            .renderer(() -> com.avicagan.bloodandbones.cooking.ButcherTableRenderer::new)
            .register();

    public static final BlockEntityEntry<com.avicagan.bloodandbones.body.SurgeryTableBlockEntity> SURGERY_TABLE = BloodAndBones.REGISTRATE
            .blockEntity("surgery_table", com.avicagan.bloodandbones.body.SurgeryTableBlockEntity::new)
            .validBlocks(BBBlocks.SURGERY_TABLE)
            .renderer(() -> com.avicagan.bloodandbones.client.SurgeryTableRenderer::new)
            .register();

    public static final BlockEntityEntry<com.avicagan.bloodandbones.backtank.FluidBacktankBlockEntity> FLUID_BACKTANK = BloodAndBones.REGISTRATE
            .blockEntity("fluid_backtank", com.avicagan.bloodandbones.backtank.FluidBacktankBlockEntity::new)
            .validBlocks(BBBlocks.FLUID_BACKTANK)
            .register();

    public static final BlockEntityEntry<com.avicagan.bloodandbones.body.BacktankPortBlockEntity> BACKTANK_PORT = BloodAndBones.REGISTRATE
            .blockEntity("backtank_port", com.avicagan.bloodandbones.body.BacktankPortBlockEntity::new)
            .validBlocks(BBBlocks.BACKTANK_PORT)
            .register();

    public static final BlockEntityEntry<com.avicagan.bloodandbones.minion.BloodTroughBlockEntity> BLOOD_TROUGH = BloodAndBones.REGISTRATE
            .blockEntity("blood_trough", com.avicagan.bloodandbones.minion.BloodTroughBlockEntity::new)
            .validBlocks(BBBlocks.BLOOD_TROUGH)
            .renderer(() -> com.avicagan.bloodandbones.client.BloodTroughRenderer::new)
            .register();

    public static void register() {
    }

    /** Mod bus listener; RegisterCapabilitiesEvent fires after registries freeze, so .get() is safe here. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        BleedingRackBlockEntity.registerCapabilities(event);
        com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity.registerCapabilities(event);
        com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity.registerCapabilities(event);
        // a backtank set down is open to pipes on every side; worn or held, spouts and item drains fill and empty it
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, FLUID_BACKTANK.get(), (be, side) -> be.tank());
        // the port's nozzle side only
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, BACKTANK_PORT.get(),
                (be, side) -> side == null || side == be.getBlockState().getValue(net.minecraft.world.level.block.DirectionalBlock.FACING) ? be.handler() : null);
        // pipes, Spouts and buckets fill a trough from any side
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, BLOOD_TROUGH.get(), (be, side) -> be.tank());
        for (var entry : BBItems.BACKTANKS.values()) {
            event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM,
                    (stack, context) -> new net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack(BBDataComponents.FLUID, stack,
                            ((com.avicagan.bloodandbones.backtank.FluidBacktankItem) stack.getItem()).tier().capacity()), entry.get());
        }
    }
}
