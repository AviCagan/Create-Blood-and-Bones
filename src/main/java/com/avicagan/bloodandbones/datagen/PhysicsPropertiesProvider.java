package com.avicagan.bloodandbones.datagen;

import com.avicagan.bloodandbones.BloodAndBones;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Sable reads block mass from data, per block state. A limb cell's mass is its box volume times the flesh
 * density, so a leg is light and a body is heavy without any code knowing about it.
 */
public class PhysicsPropertiesProvider implements DataProvider {
    private final PackOutput output;

    public PhysicsPropertiesProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        JsonObject root = new JsonObject();
        root.addProperty("selector", BloodAndBones.MOD_ID + ":carcass_part");
        JsonObject defaults = new JsonObject();
        defaults.addProperty("sable:mass", RigDerivation.FLESH_DENSITY);
        defaults.addProperty("sable:friction", 1.2);
        defaults.addProperty("sable:restitution", 0.0);
        root.add("properties", defaults);

        JsonObject overrides = new JsonObject();
        for (int x = 1; x <= 16; x++) {
            for (int y = 1; y <= 16; y++) {
                for (int z = 1; z <= 16; z++) {
                    double volume = (x / 16.0) * (y / 16.0) * (z / 16.0);
                    JsonObject props = new JsonObject();
                    props.addProperty("sable:mass", round(volume * RigDerivation.FLESH_DENSITY));
                    overrides.add("size_x=" + x + ",size_y=" + y + ",size_z=" + z, props);
                }
            }
        }
        root.add("overrides", overrides);

        Path path = output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(BloodAndBones.MOD_ID).resolve("physics_block_properties").resolve("carcass_part.json");
        return DataProvider.saveStable(cache, root, path);
    }

    private static double round(double value) {
        return Math.max(0.0005, Math.round(value * 10000.0) / 10000.0);
    }

    @Override
    public String getName() {
        return "Blood & Bones physics block properties";
    }
}
