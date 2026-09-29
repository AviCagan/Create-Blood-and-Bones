package com.avicagan.bloodandbones.body;

import com.avicagan.bloodandbones.parts.PartsData;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An implant's figures as data (rule 3): {@code data/<ns>/implant/<item>.json} gives any of walk, jump, work, attack,
 * reach, safe_fall and drain, and what it leaves out stays as the item was built (ImplantItem#defaultSpec). The mod's own
 * files are written by datagen from those, so they agree until a datapack retunes them. What an implant is (the part it
 * replaces, what it runs on, what else it does, its modules) stays with the item: that is behaviour, not a figure.
 */
public final class ImplantFigures {
    /** Worked out once per implant for each generation of the parts data. */
    private static final Map<ResourceLocation, Known> KNOWN = new ConcurrentHashMap<>();

    private record Known(PartsData.Store store, int generation, ImplantSpec base, ImplantSpec spec) {
    }

    private ImplantFigures() {
    }

    /** The figures in use: the data's where it gives them, the item's own otherwise. */
    public static ImplantSpec of(ImplantItem item, ImplantSpec base) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        // the server's data when this side has it (a single player world has both), else what the server sent
        PartsData.Store store = PartsData.SERVER.implantFiles().isEmpty() ? PartsData.CLIENT : PartsData.SERVER;
        Known known = KNOWN.get(id);
        if (known != null && known.store() == store && known.generation() == store.generation() && known.base() == base) {
            return known.spec();
        }
        ImplantSpec spec = apply(base, store.implantFiles().get(id));
        KNOWN.put(id, new Known(store, store.generation(), base, spec));
        return spec;
    }

    /** A spec with a file's figures laid over it. */
    public static ImplantSpec apply(ImplantSpec base, JsonObject json) {
        if (json == null) {
            return base;
        }
        return new ImplantSpec(base.kind(), number(json, "walk", base.walk()), number(json, "jump", base.jump()), number(json, "work", base.work()),
                number(json, "attack", base.attack()), number(json, "reach", base.reach()), number(json, "safe_fall", base.safeFall()),
                base.fuel(), json.has("drain") ? Math.max(0, json.get("drain").getAsInt()) : base.drain(), base.ability(), base.texture(), base.slots());
    }

    private static float number(JsonObject json, String key, float fallback) {
        return json.has(key) ? json.get(key).getAsFloat() : fallback;
    }

    /** The file datagen writes for an implant: every figure, as built. */
    public static JsonObject write(ImplantSpec spec) {
        JsonObject json = new JsonObject();
        json.addProperty("walk", spec.walk());
        json.addProperty("jump", spec.jump());
        json.addProperty("work", spec.work());
        json.addProperty("attack", spec.attack());
        json.addProperty("reach", spec.reach());
        json.addProperty("safe_fall", spec.safeFall());
        json.addProperty("drain", spec.drain());
        return json;
    }
}
