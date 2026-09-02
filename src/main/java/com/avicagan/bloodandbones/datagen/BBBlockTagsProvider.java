package com.avicagan.bloodandbones.datagen;

import com.avicagan.bloodandbones.BloodAndBones;
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
    }
}
