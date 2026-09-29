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
 * Butchery tables. Yields are rolled on the server; the client keeps the copy the server sends it, which only the recipe
 * viewer reads. Every mob's table is worked out from its rig and what its groups say butchery gives ({@code "butchery"}
 * in a mob_group file), with its own mob_traits file's {@code "butchery"} for what it does differently (rule 2: by group,
 * the mob only where it differs), so retuning a group retunes every mob in it. A table file
 * ({@code data/<ns>/butchery/<entity ns>/<entity path>.json}) is an optional override a datapack may give one mob; the
 * mod ships none.
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

    /** Every table file a datapack gave, by mob. */
    public static Map<ResourceLocation, ButcheryTable> all() {
        return INSTANCE.tables;
    }

    /** Every mob's table, by mob: each mob with a carcass body, a table file's where one was given, else its groups'. */
    public static Map<ResourceLocation, ButcheryTable> everyTable() {
        Map<ResourceLocation, ButcheryTable> out = new java.util.TreeMap<>();
        for (ResourceLocation entity : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet()) {
            forEntity(entity).ifPresent(table -> out.put(entity, table));
        }
        out.putAll(INSTANCE.tables);
        return out;
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

    /**
     * A mob's table from its groups' butchery settings and its own file's, spread over its rig (its own, or its generic
     * body).
     */
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
        Optional<ButcheryTable> table = byGroup(entity, rig.get(), store);
        DERIVED.put(entity, new Derived(rig.get(), store.generation(), table));
        return table;
    }

    /** A mob's table worked out from this parts data (a test's copy, say), over this rig; never kept. */
    public static Optional<ButcheryTable> byGroup(ResourceLocation entity, com.avicagan.bloodandbones.carcass.rig.Rig rig,
                                                  com.avicagan.bloodandbones.parts.PartsData.Store store) {
        try {
            return Optional.of(ButcheryDerivation.derive(rig, store.resolve(entity, false).carcass().butchery()));
        } catch (RuntimeException e) {
            BloodAndBones.LOGGER.error("Cannot work out a butchery table for {} from its groups: {}", entity, e.getMessage());
            return Optional.empty();
        }
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
