package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity;
import com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity;
import com.avicagan.bloodandbones.client.CarcassPartRenderer;
import com.avicagan.bloodandbones.client.ShackleHookRenderer;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

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

    public static void register() {
    }
}
