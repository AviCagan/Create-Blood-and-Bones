package com.avicagan.bloodandbones.carcass.butchery;

import com.avicagan.bloodandbones.BloodAndBones;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the butchery tables. Yields are rolled on the server; the client keeps the copy the server sends it,
 * which only the recipe viewer reads.
 */
public class ButcheryManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    public static final ButcheryManager INSTANCE = new ButcheryManager();

    private volatile Map<ResourceLocation, ButcheryTable> tables = Map.of();
    /** what the server last sent us */
    private static volatile Map<ResourceLocation, ButcheryTable> clientTables = Map.of();
    /** told when new tables arrive from the server; the recipe viewer sets it to refresh its pages */
    public static Runnable onClientTables = () -> {
    };

    private ButcheryManager() {
        super(GSON, "butchery");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, ButcheryTable> loaded = new HashMap<>();
        jsons.forEach((id, json) -> ButcheryTable.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> BloodAndBones.LOGGER.error("Bad butchery table {}: {}", id, error))
                .ifPresent(table -> loaded.put(table.entity(), table)));
        tables = Map.copyOf(loaded);
        BloodAndBones.LOGGER.info("Loaded {} butchery tables", tables.size());
    }

    /** Every loaded table, by table id. */
    public static Map<ResourceLocation, ButcheryTable> all() {
        return INSTANCE.tables;
    }

    public static Optional<ButcheryTable> forEntity(ResourceLocation entity) {
        return Optional.ofNullable(INSTANCE.tables.get(entity));
    }

    /** Client side: every table the server told us about, by mob. */
    public static Map<ResourceLocation, ButcheryTable> clientAll() {
        return clientTables;
    }

    public static void receiveClientTables(Map<ResourceLocation, ButcheryTable> received) {
        clientTables = Map.copyOf(received);
        BloodAndBones.LOGGER.debug("Client now knows {} butchery tables", clientTables.size());
        onClientTables.run();
    }
}
