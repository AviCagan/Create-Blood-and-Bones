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
 * which only the recipe viewer reads. A table is a mob's own, an optional override (rule 2): a mob with none (a modded
 * one, or one a datapack took the table away from) gets one worked out from its rig and what its groups say butchery
 * gives ({@code "butchery"} in a mob_group or mob_traits file), the same way datagen works out the vanilla mobs' tables.
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
        ButcheryTable own = INSTANCE.tables.get(entity);
        return own != null ? Optional.of(own) : byGroup(entity);
    }

    /** Only a table file's table. */
    public static Optional<ButcheryTable> fileTable(ResourceLocation entity) {
        return Optional.ofNullable(INSTANCE.tables.get(entity));
    }

    /** Tables worked out from groups, with the rig and the parts data they came from. */
    private static final Map<ResourceLocation, Derived> DERIVED = new java.util.concurrent.ConcurrentHashMap<>();

    private record Derived(com.avicagan.bloodandbones.carcass.rig.Rig rig, int generation, Optional<ButcheryTable> table) {
    }

    /** A mob's table from its groups' butchery settings, spread over its rig (its own, or its generic body). */
    public static Optional<ButcheryTable> byGroup(ResourceLocation entity) {
        Optional<com.avicagan.bloodandbones.carcass.rig.Rig> rig = com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(entity);
        if (rig.isEmpty()) {
            return Optional.empty();
        }
        com.avicagan.bloodandbones.parts.PartsData.Store store = com.avicagan.bloodandbones.parts.PartsData.SERVER;
        Derived known = DERIVED.get(entity);
        if (known != null && known.rig() == rig.get() && known.generation() == store.generation()) {
            return known.table();
        }
        Optional<ButcheryTable> table;
        try {
            table = Optional.of(ButcheryDerivation.derive(rig.get(), store.resolve(entity, false).carcass().butchery(), false));
        } catch (RuntimeException e) {
            BloodAndBones.LOGGER.error("Cannot work out a butchery table for {} from its groups: {}", entity, e.getMessage());
            table = Optional.empty();
        }
        DERIVED.put(entity, new Derived(rig.get(), store.generation(), table));
        return table;
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
