package com.avicagan.bloodandbones.datagen;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class BBBlockTagsProvider extends BlockTagsProvider {
    public BBBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookup, BloodAndBones.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BBTags.CHILLS)
                .add(Blocks.ICE, Blocks.FROSTED_ICE, Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW, Blocks.SNOW)
                .addOptionalTag(ResourceLocation.fromNamespaceAndPath("create_dragons_plus", "passive_block_freezers"));
        tag(BBTags.PRESERVES)
                .add(Blocks.PACKED_ICE, Blocks.BLUE_ICE);
        // a carcass standing on one is held there to be worked, not let fall off it (CarcassSlump)
        tag(BBTags.HOLDS_CARCASSES).add(
                BBBlocks.BUTCHER_TABLE.get(), BBBlocks.SURGERY_TABLE.get(), BBBlocks.STEEL_TABLE.get(), BBBlocks.BLEEDING_RACK.get(),
                BBBlocks.MANGLER.get(), BBBlocks.GUILLOTINE.get(), BBBlocks.BEHEADER.get(), BBBlocks.DEGLOVER.get());
        // built into a Sable ship, these blocks go with what they hold: Sable takes their data along, then removes the old
        // block with its block entity already gone, so what they hold is not dropped as well (Sable's own way, for blocks
        // whose removal drops their contents)
        tag(BBTags.SILENT_ASSEMBLY_REMOVAL).add(
                BBBlocks.SPECIMEN_JAR.get(), BBBlocks.BUTCHER_HOOK.get(), BBBlocks.BUTCHER_TABLE.get(), BBBlocks.SURGERY_TABLE.get(),
                BBBlocks.FLUID_BACKTANK.get(), BBBlocks.BACKTANK_PORT.get(), BBBlocks.STEEL_TABLE.get(), BBBlocks.STEEL_RACK.get(),
                BBBlocks.CHARGING_CRADLE.get(), BBBlocks.BLOOD_TROUGH.get(), BBBlocks.BLEEDING_RACK.get(), BBBlocks.SPIT_ROAST.get(),
                BBBlocks.MANGLER.get(), BBBlocks.GUILLOTINE.get(), BBBlocks.BEHEADER.get(), BBBlocks.DEGLOVER.get());
    }
}
