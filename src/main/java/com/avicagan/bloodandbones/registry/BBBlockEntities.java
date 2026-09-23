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
            .visual(() -> com.avicagan.bloodandbones.machine.CarcassMachineVisual::new, false)
            .validBlocks(BBBlocks.MANGLER, BBBlocks.GUILLOTINE, BBBlocks.BEHEADER, BBBlocks.DEGLOVER)
            .renderer(() -> com.avicagan.bloodandbones.machine.CarcassMachineRenderer::new)
            .register();

    public static void register() {
    }

    /** Mod bus listener; RegisterCapabilitiesEvent fires after registries freeze, so .get() is safe here. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        BleedingRackBlockEntity.registerCapabilities(event);
        com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity.registerCapabilities(event);
    }
}
