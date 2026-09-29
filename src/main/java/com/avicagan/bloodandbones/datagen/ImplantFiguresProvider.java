package com.avicagan.bloodandbones.datagen;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.ImplantFigures;
import com.avicagan.bloodandbones.body.ImplantItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Every implant's figures as a data file (ImplantFigures), written from the figures each implant item is built with, so
 * the files and the items agree until a datapack changes a file.
 */
public class ImplantFiguresProvider implements DataProvider {
    private final PackOutput output;

    public ImplantFiguresProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> files = new ArrayList<>();
        for (var item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (item instanceof ImplantItem implant && id.getNamespace().equals(BloodAndBones.MOD_ID)) {
                files.add(DataProvider.saveStable(cache, ImplantFigures.write(implant.defaultSpec()),
                        output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(id.getNamespace()).resolve("implant").resolve(id.getPath() + ".json")));
            }
        }
        return CompletableFuture.allOf(files.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Blood & Bones implant figures";
    }
}
