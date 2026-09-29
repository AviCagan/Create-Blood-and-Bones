package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.BloodAndBones;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A head's disposition (docs/NEXT.md 1.2): how its temper scales each sort of task, fight, tend, fetch and work, one task
 * over its sort, the tasks done at its maker's side, and night and day. Every head has one ("none" when its data names
 * none); a body with no head is mindless. Read from {@code data/<ns>/minion_disposition/<name>.json}, server only, over the
 * defaults below; anything a file leaves out counts as 1.
 *
 * @param kinds  by sort of task
 * @param tasks  by task, in place of its sort's
 * @param withMe on any task done at its maker's side (loyal)
 * @param night  after dark (nocturnal)
 * @param day    by day
 */
public record MinionDisposition(Map<MinionTask.Kind, Float> kinds, Map<ResourceLocation, Float> tasks, float withMe, float night, float day) {
    public static final MinionDisposition NONE = new MinionDisposition(Map.of(), Map.of(), 1.0F, 1.0F, 1.0F);
    /** The one a body with no head has. */
    public static final String MINDLESS = "mindless";

    /** What it multiplies a fitness at this task by: its task's own, else its sort's; with me; by night or by day. */
    public float multiplier(MinionTask task, MinionTask.Anchor anchor, boolean isNight) {
        float m = tasks.containsKey(task.id) ? tasks.get(task.id) : kinds.getOrDefault(task.kind, 1.0F);
        if (anchor == MinionTask.Anchor.MAKER) {
            m *= withMe;
        }
        return m * (isNight ? night : day);
    }

    /** A disposition's name's key: "bloodandbones.minion.disposition.meek" (a datapack's own, under its namespace). */
    public static String nameKey(String name) {
        ResourceLocation id = id(name);
        return id.getNamespace() + ".minion.disposition." + id.getPath();
    }

    /** A disposition's id from what a head's data calls it: a plain name is the mod's own ("meek"). */
    public static ResourceLocation id(String name) {
        return name.indexOf(':') < 0 ? BloodAndBones.asResource(name) : ResourceLocation.parse(name);
    }

    /** The shipped dispositions (docs/NEXT.md 1.2), which the files of the same names hold too. */
    public static final Map<String, MinionDisposition> DEFAULTS = defaults();

    private static Map<String, MinionDisposition> defaults() {
        Map<String, MinionDisposition> out = new LinkedHashMap<>();
        out.put("none", NONE);
        out.put("brave", kinds(1.25F, 1.0F, 1.0F, 1.0F));
        out.put("berserk", kinds(1.5F, 0.5F, 0.75F, 0.75F));
        // a golem, a bear, a turtle: better at keeping what is theirs
        out.put("territorial", new MinionDisposition(Map.of(), Map.of(MinionTask.GUARD.id, 1.25F, MinionTask.SENTRY.id, 1.25F), 1.0F, 1.0F, 1.0F));
        out.put("loyal", new MinionDisposition(Map.of(), Map.of(), 1.25F, 1.0F, 1.0F));
        out.put("docile", kinds(0.75F, 1.25F, 1.0F, 1.0F));
        out.put("meek", kinds(0.5F, 1.25F, 1.0F, 1.0F));
        out.put("skittish", kinds(0.5F, 1.0F, 1.25F, 1.0F));
        out.put("nocturnal", new MinionDisposition(Map.of(), Map.of(), 1.0F, 1.25F, 0.75F));
        // the nitwit
        out.put("dim", kinds(0.75F, 0.75F, 0.75F, 0.75F));
        out.put(MINDLESS, kinds(0.5F, 0.25F, 0.75F, 0.75F));
        return java.util.Collections.unmodifiableMap(out);
    }

    private static MinionDisposition kinds(float fight, float tend, float fetch, float work) {
        Map<MinionTask.Kind, Float> kinds = new EnumMap<>(MinionTask.Kind.class);
        kinds.put(MinionTask.Kind.FIGHT, fight);
        kinds.put(MinionTask.Kind.TEND, tend);
        kinds.put(MinionTask.Kind.FETCH, fetch);
        kinds.put(MinionTask.Kind.WORK, work);
        kinds.values().removeIf(v -> v == 1.0F);
        return new MinionDisposition(java.util.Collections.unmodifiableMap(kinds), Map.of(), 1.0F, 1.0F, 1.0F);
    }

    /** As a disposition file writes it: only what is not 1. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        if (!kinds.isEmpty()) {
            JsonObject k = new JsonObject();
            for (MinionTask.Kind kind : MinionTask.Kind.values()) {
                if (kinds.containsKey(kind)) {
                    k.addProperty(kind.key(), kinds.get(kind));
                }
            }
            o.add("kinds", k);
        }
        if (!tasks.isEmpty()) {
            JsonObject t = new JsonObject();
            tasks.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> t.addProperty(e.getKey().toString(), e.getValue()));
            o.add("tasks", t);
        }
        if (withMe != 1.0F) {
            o.addProperty("with_me", withMe);
        }
        if (night != 1.0F || day != 1.0F) {
            o.addProperty("night", night);
            o.addProperty("day", day);
        }
        return o;
    }

    /** A disposition file: "kinds", "tasks", "with_me", "night", "day", each 1 where left out. */
    public static MinionDisposition read(JsonObject o) {
        Map<MinionTask.Kind, Float> kinds = new EnumMap<>(MinionTask.Kind.class);
        if (o.has("kinds")) {
            o.getAsJsonObject("kinds").entrySet().forEach(e -> {
                MinionTask.Kind kind = MinionTask.Kind.byKey(e.getKey());
                if (kind == null || kind == MinionTask.Kind.NONE) {
                    throw new IllegalArgumentException("unknown kind " + e.getKey() + ": one of " + List.of("fight", "tend", "fetch", "work"));
                }
                kinds.put(kind, e.getValue().getAsFloat());
            });
        }
        Map<ResourceLocation, Float> tasks = new LinkedHashMap<>();
        if (o.has("tasks")) {
            o.getAsJsonObject("tasks").entrySet().forEach(e -> tasks.put(ResourceLocation.parse(e.getKey()), e.getValue().getAsFloat()));
        }
        return new MinionDisposition(java.util.Collections.unmodifiableMap(kinds), java.util.Collections.unmodifiableMap(tasks),
                o.has("with_me") ? o.get("with_me").getAsFloat() : 1.0F, o.has("night") ? o.get("night").getAsFloat() : 1.0F,
                o.has("day") ? o.get("day").getAsFloat() : 1.0F);
    }
}
