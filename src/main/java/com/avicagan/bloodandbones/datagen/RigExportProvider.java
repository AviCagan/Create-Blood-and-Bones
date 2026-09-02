package com.avicagan.bloodandbones.datagen;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigTarget;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Builds rig files straight from the vanilla entity models, so a carcass is exactly the mob's own parts.
 * Which mobs, and what to do with their odd parts, comes from the target files in the directory named
 * by the {@code bloodandbones.rig_targets} system property (the data run sets it to src/main/rig_targets).
 */
public class RigExportProvider implements DataProvider {
    public static final String TARGETS_PROPERTY = "bloodandbones.rig_targets";

    private final PackOutput output;

    public RigExportProvider(PackOutput output) {
        this.output = output;
    }

    public static List<RigTarget> loadTargets() {
        String dir = System.getProperty(TARGETS_PROPERTY);
        if (dir == null) {
            throw new IllegalStateException("System property " + TARGETS_PROPERTY + " is not set; the data run should point it at src/main/rig_targets");
        }
        Path root = Path.of(dir);
        List<RigTarget> targets = new ArrayList<>();
        try (Stream<Path> files = Files.list(root)) {
            for (Path file : files.sorted().toList()) {
                if (!file.toString().endsWith(".json")) {
                    continue;
                }
                try (Reader reader = Files.newBufferedReader(file)) {
                    JsonElement json = JsonParser.parseReader(reader);
                    targets.add(RigTarget.CODEC.parse(JsonOps.INSTANCE, json)
                            .getOrThrow(message -> new IllegalStateException("Bad rig target " + file + ": " + message)));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read rig targets from " + root, e);
        }
        return targets;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<ModelLayerLocation, LayerDefinition> roots = LayerDefinitions.createRoots();
        List<CompletableFuture<?>> futures = new ArrayList<>();
        Path base = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(BloodAndBones.MOD_ID).resolve("rig");
        for (RigTarget target : loadTargets()) {
            ModelLayerLocation layer = new ModelLayerLocation(target.model(), target.layer());
            LayerDefinition definition = roots.get(layer);
            if (definition == null) {
                throw new IllegalStateException("No layer definition for " + layer);
            }
            ModelPart root = definition.bakeRoot();
            Rig rig = RigDerivation.derive(target, root);
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
