package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.parts.PartSlots;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.ResolvedMob;
import com.avicagan.bloodandbones.parts.SlotInfo;
import com.avicagan.bloodandbones.parts.TraitList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.util.Map;
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
        return field(mob, Map.of(), key, field);
    }

    /**
     * A field of a part's minion data for one particular piece: a layer's "variants" the piece's captured traits
     * match (docs/PARTS-AND-TRAITS.md section 4.2) come before that layer's own value, so a villager's head offers its
     * profession's jobs. A variant is {"if": {"trait": "profession", "equals": "farmer"}, "jobs": [...]} ("in": [...]
     * for several values, neither for any value at all); the last one that matches wins, and a later layer (a mob's
     * own file) still comes before the variants of the layers under it.
     */
    public static Optional<JsonElement> field(ResolvedMob mob, Map<String, String> traits, String key, String field) {
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
                if (!layer.isJsonObject()) {
                    continue;
                }
                JsonObject o = layer.getAsJsonObject();
                Optional<JsonElement> variant = variant(o, traits, field);
                if (variant.isPresent()) {
                    return variant;
                }
                if (o.has(field)) {
                    return Optional.of(o.get(field));
                }
            }
        }
        return Optional.empty();
    }

    /** The field from the last of a layer's variants this piece matches that gives it. */
    private static Optional<JsonElement> variant(JsonObject layer, Map<String, String> traits, String field) {
        if (traits.isEmpty() || !layer.has("variants") || !layer.get("variants").isJsonArray()) {
            return Optional.empty();
        }
        var variants = layer.getAsJsonArray("variants");
        for (int i = variants.size() - 1; i >= 0; i--) {
            if (variants.get(i).isJsonObject()) {
                JsonObject variant = variants.get(i).getAsJsonObject();
                if (variant.has(field) && matches(variant.getAsJsonObject("if"), traits)) {
                    return Optional.of(variant.get(field));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean matches(@org.jetbrains.annotations.Nullable JsonObject when, Map<String, String> traits) {
        return ResolvedMob.Variant.matches(when, traits);
    }

    public static float number(ResolvedMob mob, String key, String field, String inner, float fallback) {
        return number(mob, Map.of(), key, field, inner, fallback);
    }

    /** A number inside a part's minion object, for one piece (its variants first). */
    public static float number(ResolvedMob mob, Map<String, String> traits, String key, String field, String inner, float fallback) {
        return field(mob, traits, key, field).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
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
        return scalar(mob, Map.of(), key, field, fallback);
    }

    public static float scalar(ResolvedMob mob, Map<String, String> traits, String key, String field, float fallback) {
        return field(mob, traits, key, field).filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsFloat).orElse(fallback);
    }

    public static List<ResourceLocation> ids(ResolvedMob mob, String key, String field) {
        return ids(mob, Map.of(), key, field);
    }

    /** A list of ids in a part's minion object, for one piece (its variants first): a head's jobs. */
    public static List<ResourceLocation> ids(ResolvedMob mob, Map<String, String> traits, String key, String field) {
        List<ResourceLocation> out = new ArrayList<>();
        field(mob, traits, key, field).filter(JsonElement::isJsonArray).ifPresent(a -> a.getAsJsonArray().forEach(e -> out.add(ResourceLocation.parse(e.getAsString()))));
        return out;
    }

    /**
     * A part's minion traits for one mob: every layer's "traits" list, the general key's first ("leg"), then the
     * specific one's ("leg.hind"), each adding to or editing what the ones before gave.
     */
    public static List<TraitList.Resolved> traits(ResolvedMob mob, String key) {
        return traits(mob, Map.of(), key);
    }

    /**
     * The same for one particular piece: after each layer's own list, the "traits" of each of that layer's variants the
     * piece's captured traits match, in order (a warm frog's legs are fireproof, a cold one's frost-guarded).
     */
    public static List<TraitList.Resolved> traits(ResolvedMob mob, Map<String, String> captured, String key) {
        List<String> keys = new ArrayList<>();
        int dot = key.indexOf('.');
        if (dot > 0) {
            keys.add(key.substring(0, dot));
        }
        keys.add(key);
        List<TraitList.Resolved> out = List.of();
        for (String k : keys) {
            for (JsonElement layer : mob.minion().getOrDefault(k, List.of())) {
                if (!layer.isJsonObject()) {
                    continue;
                }
                JsonObject o = layer.getAsJsonObject();
                out = applyTraits(o, out, mob);
                if (!captured.isEmpty() && o.has("variants") && o.get("variants").isJsonArray()) {
                    for (JsonElement variant : o.getAsJsonArray("variants")) {
                        if (variant.isJsonObject() && matches(variant.getAsJsonObject().getAsJsonObject("if"), captured)) {
                            out = applyTraits(variant.getAsJsonObject(), out, mob);
                        }
                    }
                }
            }
        }
        return out;
    }

    /** An object's "traits" list applied to what came before (nothing if it has none, or it will not read). */
    private static List<TraitList.Resolved> applyTraits(JsonObject o, List<TraitList.Resolved> before, ResolvedMob mob) {
        if (!o.has("traits")) {
            return before;
        }
        TraitList list = TraitList.CODEC.parse(JsonOps.INSTANCE, o.get("traits")).result().orElse(null);
        return list == null ? before : list.applyTo(before, mob.entity());
    }

    /**
     * Every source of a build's minion traits, one list each (docs/PARTS-AND-TRAITS.md section 6.4): the torso's, each
     * fitted piece's for the slot it is (a rabbit's hind leg its "leg.hind" traits), and the organ's "minion" list, each
     * with what the variants its mob's captured traits match add. A piece whose mob has no rig gives nothing. Pure: data
     * in, lists out.
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
                out.add(traits(store.resolve(piece.entity(), piece.baby()), piece.traits(), key));
            }
        }
        build.organ().ifPresent(organ -> {
            ResolvedMob mob = store.resolve(organ.entity(), organ.baby());
            if (mob.organs().containsKey(organ.organ())) {
                // with what its variants add for the mob it came out of (a charged creeper's sac)
                out.add(mob.organMinion(organ.organ(), organ.traits()));
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
