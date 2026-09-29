package com.avicagan.bloodandbones.datagen;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.Tissue;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Sable reads block mass from data, per block state. A limb cell's mass is its box volume times the density of what it
 * is made of (Tissue: flesh, bone or plate, each its own block), so a leg is light, a body heavy and a golem's plate
 * heavier than a cow's flesh of the same size, without any code knowing about it. A datapack retunes them by
 * overriding these files.
 */
public class PhysicsPropertiesProvider implements DataProvider {
    private final PackOutput output;

    public PhysicsPropertiesProvider(PackOutput output) {
        this.output = output;
    }

    /**
     * One file for each tissue's block (CarcassPartBlock#tissue). Sable matches every state of a block against every
     * override of its file, for each world as it loads and for each joining client, so the work grows as a block's
     * states times its overrides: three blocks of 4,096 states cost three times what one did, where a tissue property
     * on one block (12,288 states, each file checked against all of them) cost nine times, some twenty seconds a world.
     */
    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> files = new ArrayList<>();
        for (Tissue tissue : Tissue.values()) {
            String block = BuiltInRegistries.BLOCK.getKey(BBBlocks.carcassPart(tissue)).getPath();
            JsonObject root = new JsonObject();
            root.addProperty("selector", BloodAndBones.MOD_ID + ":" + block);
            JsonObject defaults = new JsonObject();
            defaults.addProperty("sable:mass", tissue.density);
            defaults.addProperty("sable:friction", 0.45); // wet flesh slides; high friction makes dragging stick-slip
            defaults.addProperty("sable:restitution", 0.0);
            root.add("properties", defaults);

            JsonObject overrides = new JsonObject();
            for (int x = 1; x <= 16; x++) {
                for (int y = 1; y <= 16; y++) {
                    for (int z = 1; z <= 16; z++) {
                        double volume = (x / 16.0) * (y / 16.0) * (z / 16.0);
                        JsonObject props = new JsonObject();
                        props.addProperty("sable:mass", round(volume * tissue.density));
                        overrides.add("size_x=" + x + ",size_y=" + y + ",size_z=" + z, props);
                    }
                }
            }
            root.add("overrides", overrides);

            Path path = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(BloodAndBones.MOD_ID).resolve("physics_block_properties")
                    .resolve(block + ".json");
            files.add(DataProvider.saveStable(cache, root, path));
        }
        return CompletableFuture.allOf(files.toArray(CompletableFuture[]::new));
    }

    private static double round(double value) {
        return Math.max(0.0005, Math.round(value * 10000.0) / 10000.0);
    }

    @Override
    public String getName() {
        return "Blood & Bones physics block properties";
    }
}
