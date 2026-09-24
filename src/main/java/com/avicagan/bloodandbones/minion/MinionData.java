package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.parts.PartSlots;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.ResolvedMob;
import com.avicagan.bloodandbones.parts.SlotInfo;
import com.avicagan.bloodandbones.parts.TraitList;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
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
 * attributes. A part's "traits" are a trait list, and layer as armour's do (docs/PARTS-AND-TRAITS.md section 4.2).
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

    /**
     * A part's minion traits for one mob: every layer's "traits" list, the general key's first ("leg"), then the
     * specific one's ("leg.hind"), each adding to or editing what the ones before gave.
     */
    public static List<TraitList.Resolved> traits(ResolvedMob mob, String key) {
        List<String> keys = new ArrayList<>();
        int dot = key.indexOf('.');
        if (dot > 0) {
            keys.add(key.substring(0, dot));
        }
        keys.add(key);
        List<TraitList.Resolved> out = List.of();
        for (String k : keys) {
            for (JsonElement layer : mob.minion().getOrDefault(k, List.of())) {
                if (layer.isJsonObject() && layer.getAsJsonObject().has("traits")) {
                    TraitList list = TraitList.CODEC.parse(JsonOps.INSTANCE, layer.getAsJsonObject().get("traits")).result().orElse(null);
                    if (list != null) {
                        out = list.applyTo(out, mob.entity());
                    }
                }
            }
        }
        return out;
    }

    /**
     * Every source of a build's minion traits, one list each (docs/PARTS-AND-TRAITS.md section 6.4): the torso's, each
     * fitted piece's for the slot it is (a rabbit's hind leg its "leg.hind" traits), and the organ's "minion" list. A
     * piece whose mob has no rig gives nothing. Pure: data in, lists out.
     */
    public static List<List<TraitList.Resolved>> traits(PartsData.Store store, MinionBuild build) {
        List<List<TraitList.Resolved>> out = new ArrayList<>();
        List<PieceRef> pieces = new ArrayList<>();
        pieces.add(build.torso());
        build.parts().forEach(f -> pieces.add(f.piece()));
        for (PieceRef piece : pieces) {
            Optional<Rig> rig = store.rig(piece.entity(), piece.baby());
            if (rig.isPresent()) {
                SlotInfo slot = PartSlots.of(store, piece.entity(), rig.get(), piece.bone());
                // a torso extension is torso, a neck is head, as far as what they bring goes
                String key = switch (slot.slot()) {
                    case TORSO_EXT -> "torso";
                    case NECK -> "head";
                    default -> slot.key();
                };
                out.add(traits(store.resolve(piece.entity(), piece.baby()), key));
            }
        }
        build.organ().ifPresent(organ -> {
            ResolvedMob.Organ traits = store.resolve(organ.entity(), organ.baby()).organs().get(organ.organ());
            if (traits != null) {
                out.add(traits.minion());
            }
        });
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
