package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassPartBlock;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public class BBBlocks {
    /**
     * One block per limb cell of a physics carcass. Invisible; the limb is drawn by its block entity renderer
     * and the physics box comes from the block state. Never obtainable, never breakable.
     */
    public static final BlockEntry<CarcassPartBlock> CARCASS_PART = BloodAndBones.REGISTRATE
            .block("carcass_part", CarcassPartBlock::new)
            .properties(p -> p.mapColor(MapColor.COLOR_RED)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false))
            .blockstate(NonNullBiConsumer.noop())
            .loot(NonNullBiConsumer.noop())
            .lang("Carcass")
            .register();

    public static void register() {
    }
}
