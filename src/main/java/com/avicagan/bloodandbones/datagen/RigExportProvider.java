package com.avicagan.bloodandbones.datagen;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Builds rig files straight from the vanilla entity models, so a carcass is exactly the mob's own parts.
 */
public class RigExportProvider implements DataProvider {
    private record Target(ResourceLocation entity, ModelLayerLocation layer, ResourceLocation texture) {
    }

    private static final List<Target> TARGETS = List.of(
            new Target(ResourceLocation.withDefaultNamespace("cow"), ModelLayers.COW,
                    ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"))
    );

    private final PackOutput output;

    public RigExportProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<ModelLayerLocation, LayerDefinition> roots = LayerDefinitions.createRoots();
        List<CompletableFuture<?>> futures = new ArrayList<>();
        Path base = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(BloodAndBones.MOD_ID).resolve("rig");
        for (Target target : TARGETS) {
            LayerDefinition definition = roots.get(target.layer());
            if (definition == null) {
                throw new IllegalStateException("No layer definition for " + target.layer());
            }
            ModelPart root = definition.bakeRoot();
            Rig rig = RigDerivation.derive(target.entity(), target.layer(), target.texture(), root);
            JsonElement json = Rig.CODEC.encodeStart(JsonOps.INSTANCE, rig).getOrThrow();
            Path path = base.resolve(target.entity().getNamespace()).resolve(target.entity().getPath() + ".json");
            futures.add(DataProvider.saveStable(cache, json, path));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Blood & Bones carcass rigs";
    }
}
