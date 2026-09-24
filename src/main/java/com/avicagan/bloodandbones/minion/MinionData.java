package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.parts.ResolvedMob;
import com.google.gson.JsonElement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reading the minion side of a mob's resolved data: a part's "minion" object in each layer, the most specific
 * key and the latest layer winning ("leg.hind" before "leg"). Numbers not in data come from the mob's own
 * attributes.
 */
public final class MinionData {
    private MinionData() {
    }

    /** A field of a part's minion data, if any layer gives it. */
    public static Optional<JsonElement> field(ResolvedMob mob, String key, String field) {
        List<String> keys = new ArrayList<>();
        keys.add(key);
        int dot = key.indexOf('.');
        if (dot > 0) {
            keys.add(key.substring(0, dot));
        }
        for (String k : keys) {
            List<JsonElement> layers = mob.minion().getOrDefault(k, List.of());
            for (int i = layers.size() - 1; i >= 0; i--) {
                JsonElement layer = layers.get(i);
                if (layer.isJsonObject() && layer.getAsJsonObject().has(field)) {
                    return Optional.of(layer.getAsJsonObject().get(field));
                }
            }
        }
        return Optional.empty();
    }

    public static float number(ResolvedMob mob, String key, String field, String inner, float fallback) {
        return field(mob, key, field).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
                .filter(o -> o.has(inner)).map(o -> o.get(inner).getAsFloat()).orElse(fallback);
    }

    public static String text(ResolvedMob mob, String key, String field, String inner, String fallback) {
        return field(mob, key, field).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
                .filter(o -> o.has(inner)).map(o -> o.get(inner).getAsString()).orElse(fallback);
    }

    /** A true/false inside a part's minion object ("movement": {"rideable": true}); false if absent. */
    public static boolean flag(ResolvedMob mob, String key, String field, String inner) {
        return field(mob, key, field).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
                .filter(o -> o.has(inner)).map(o -> o.get(inner).getAsBoolean()).orElse(false);
    }

    public static float scalar(ResolvedMob mob, String key, String field, float fallback) {
        return field(mob, key, field).filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsFloat).orElse(fallback);
    }

    public static List<ResourceLocation> ids(ResolvedMob mob, String key, String field) {
        List<ResourceLocation> out = new ArrayList<>();
        field(mob, key, field).filter(JsonElement::isJsonArray).ifPresent(a -> a.getAsJsonArray().forEach(e -> out.add(ResourceLocation.parse(e.getAsString()))));
        return out;
    }

    /** One of the mob's own attributes as vanilla sets it, or the fallback. */
    @SuppressWarnings("unchecked")
    public static double attribute(ResourceLocation entity, Holder<Attribute> attribute, double fallback) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entity);
        if (type.isEmpty() || !DefaultAttributes.hasSupplier(type.get())) {
            return fallback;
        }
        var supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type.get());
        return supplier.hasAttribute(attribute) ? supplier.getBaseValue(attribute) : fallback;
    }
}
