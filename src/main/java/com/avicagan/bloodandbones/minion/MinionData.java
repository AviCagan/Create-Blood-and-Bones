package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.parts.PartSlots;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.ResolvedMob;
import com.avicagan.bloodandbones.parts.SlotInfo;
import com.avicagan.bloodandbones.parts.TraitList;
import com.avicagan.bloodandbones.parts.effect.FlagEffect;
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
     * match (docs/PARTS-AND-TRAITS.md section 4.2) come before that layer's own value, so a villager's head takes its
     * profession's disposition. A variant is {"if": {"trait": "profession", "equals": "nitwit"}, "disposition": "dim"} ("in": [...]
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

    /** A list of ids in a part's minion object, for one piece (its variants first): a head's senses. */
    public static List<ResourceLocation> ids(ResolvedMob mob, Map<String, String> traits, String key, String field) {
        List<ResourceLocation> out = new ArrayList<>();
        field(mob, traits, key, field).filter(JsonElement::isJsonArray).ifPresent(a -> a.getAsJsonArray().forEach(e -> out.add(ResourceLocation.parse(e.getAsString()))));
        return out;
    }

    /**
     * A part's knacks for one particular piece (docs/NEXT.md 1.6): how much better or worse than usual it makes a minion at
     * each task, from its "knacks" map ({"bloodandbones:surgeon": 1.5}). They merge key by key as trait lists do: the general
     * key's layers first ("leg", then "leg.front"), archetype to the mob's own file, each layer's own map and then each of its
     * variants the piece's captured traits match, in order, a later one changing only the tasks it names (a family inherits
     * its archetype's knacks and can change one). Old data's "jobs" list, where a map has no "knacks", reads as knacks
     * ({@link #oldJobs}). Tasks not named count as 1.
     */
    public static Map<ResourceLocation, Float> knacks(ResolvedMob mob, Map<String, String> captured, String key) {
        List<String> keys = new ArrayList<>();
        int dot = key.indexOf('.');
        if (dot > 0) {
            keys.add(key.substring(0, dot));
        }
        keys.add(key);
        Map<ResourceLocation, Float> out = new java.util.LinkedHashMap<>();
        for (String k : keys) {
            for (JsonElement layer : mob.minion().getOrDefault(k, List.of())) {
                if (!layer.isJsonObject()) {
                    continue;
                }
                JsonObject o = layer.getAsJsonObject();
                out.putAll(ownKnacks(o));
                if (!captured.isEmpty() && o.has("variants") && o.get("variants").isJsonArray()) {
                    for (JsonElement variant : o.getAsJsonArray("variants")) {
                        if (variant.isJsonObject() && matches(variant.getAsJsonObject().getAsJsonObject("if"), captured)) {
                            out.putAll(ownKnacks(variant.getAsJsonObject()));
                        }
                    }
                }
            }
        }
        return out;
    }

    /** One map's knacks: its "knacks", else its old "jobs" read as knacks, else none. */
    private static Map<ResourceLocation, Float> ownKnacks(JsonObject o) {
        if (o.has("knacks") && o.get("knacks").isJsonObject()) {
            Map<ResourceLocation, Float> out = new java.util.LinkedHashMap<>();
            o.getAsJsonObject("knacks").entrySet().forEach(e -> out.put(ResourceLocation.parse(e.getKey()), e.getValue().getAsFloat()));
            return out;
        }
        return o.has("jobs") ? oldJobs(o.get("jobs")) : Map.of();
    }

    /**
     * An old datapack's "jobs" list read as knacks (docs/NEXT.md 1.8): the first job 1.5, the rest 1.25; companion is
     * dropped (anything can keep company), a bodyguard is a guard and a scavenger a courier, a task named twice keeping its
     * first. (The file is logged once as it loads: {@code MobGroup.OLD_JOBS}.)
     */
    public static Map<ResourceLocation, Float> oldJobs(JsonElement jobs) {
        Map<ResourceLocation, Float> out = new java.util.LinkedHashMap<>();
        if (!jobs.isJsonArray()) {
            return out;
        }
        boolean first = true;
        for (JsonElement e : jobs.getAsJsonArray()) {
            ResourceLocation job = ResourceLocation.tryParse(e.getAsString());
            if (job == null) {
                continue;
            }
            float knack = first ? 1.5F : 1.25F;
            first = false;
            String path = job.getNamespace().equals(com.avicagan.bloodandbones.BloodAndBones.MOD_ID) ? job.getPath() : "";
            switch (path) {
                case "companion" -> {
                    continue;
                }
                case "bodyguard" -> job = MinionTask.GUARD.id;
                case "scavenger" -> job = MinionTask.COURIER.id;
                default -> {
                }
            }
            out.putIfAbsent(job, knack);
        }
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
     * What legs let a minion do only when at least half its fitted legs can (docs/PARTS-AND-TRAITS.md section 6.4,
     * capabilities): climb walls (spider legs), walk on lava (strider legs). A leg's trait carrying one of these flags
     * counts only then, so one strider leg under a cow gives it nothing; the same flag from anything else (its organ) does.
     */
    public static final List<String> LEG_CAPABILITIES = List.of(FlagEffect.CLIMB, FlagEffect.LAVA_WALK);

    /**
     * Every source of a build's minion traits, one list each (docs/PARTS-AND-TRAITS.md section 6.4): the torso's, each
     * fitted piece's for the slot it is (a rabbit's hind leg its "leg.hind" traits), and the organ's "minion" list, each
     * with what the variants its mob's captured traits match add. A piece whose mob has no rig gives nothing. The legs'
     * capabilities are left off unless at least half the legs share them ({@link #LEG_CAPABILITIES}). Pure: data in,
     * lists out.
     */
    public static List<List<TraitList.Resolved>> traits(PartsData.Store store, MinionBuild build) {
        List<List<TraitList.Resolved>> out = new ArrayList<>();
        List<PieceRef> pieces = new ArrayList<>();
        pieces.add(build.torso());
        build.parts().forEach(f -> pieces.add(f.piece()));
        List<Integer> legs = new ArrayList<>();
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
                if (slot.slot() == com.avicagan.bloodandbones.parts.PartSlot.LEG) {
                    legs.add(out.size());
                }
                out.add(traits(store.resolve(piece.entity(), piece.baby()), piece.traits(), key));
            }
        }
        for (String capability : LEG_CAPABILITIES) {
            long with = legs.stream().filter(i -> out.get(i).stream().anyMatch(t -> carries(store, t, capability))).count();
            if (with * 2 < legs.size()) {
                for (int i : legs) {
                    out.set(i, out.get(i).stream().filter(t -> !carries(store, t, capability)).toList());
                }
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

    /**
     * The pieces whose hide a flesh build keeps (docs/PARTS-AND-TRAITS.md section 6.6): the first piece of each different mob
     * among its pieces fitted with the hide on, torso first, up to three; none on brass.
     */
    public static List<PieceRef> hidePieces(MinionBuild build) {
        if (build.cybernetic()) {
            return List.of();
        }
        List<PieceRef> out = new ArrayList<>();
        List<PieceRef> pieces = new ArrayList<>();
        pieces.add(build.torso());
        build.parts().forEach(f -> pieces.add(f.piece()));
        for (PieceRef piece : pieces) {
            if (!piece.skinned() && out.stream().noneMatch(p -> p.entity().equals(piece.entity())) && out.size() < MinionEntity.HIDES) {
                out.add(piece);
            }
        }
        return out;
    }

    /**
     * The traits a build gives a minion and their levels, worked out as its {@code ActiveTraits} are, with no world: its
     * parts' and organ's ({@link #traits(PartsData.Store, MinionBuild)}) and, on flesh, the hides it keeps; the same trait
     * from several places counts once at its highest level, or summed if it says so, held to its most. Traits that are not
     * for minions, or not loaded, are left out.
     */
    public static Map<ResourceLocation, Integer> levels(PartsData.Store store, MinionBuild build) {
        Map<ResourceLocation, Integer> levels = new java.util.LinkedHashMap<>();
        List<List<TraitList.Resolved>> sources = new ArrayList<>(traits(store, build));
        for (PieceRef hide : hidePieces(build)) {
            sources.add(store.resolve(hide.entity(), false).hide(hide.traits()));
        }
        for (List<TraitList.Resolved> source : sources) {
            for (TraitList.Resolved t : source) {
                com.avicagan.bloodandbones.parts.Trait trait = store.trait(t.id());
                if (trait != null && trait.sums()) {
                    levels.merge(t.id(), t.level(), Integer::sum);
                } else {
                    levels.merge(t.id(), t.level(), Math::max);
                }
            }
        }
        Map<ResourceLocation, Integer> out = new java.util.LinkedHashMap<>();
        levels.forEach((id, level) -> {
            com.avicagan.bloodandbones.parts.Trait trait = store.trait(id);
            if (trait != null && trait.contexts().contains(com.avicagan.bloodandbones.parts.ActiveTraits.MINION)) {
                out.put(id, Math.min(level, Math.max(1, trait.maxLevel())));
            }
        });
        return out;
    }

    /** One effect of a build's traits, with the trait and level it comes at and its entry in the trait. */
    public record Found<T>(ResourceLocation trait, int level, com.avicagan.bloodandbones.parts.TraitEffect facet, T effect) {
        /** Always on (passive, with no condition): what a stat worked out with no world can count. */
        public boolean always() {
            return facet.trigger() == com.avicagan.bloodandbones.parts.Trigger.PASSIVE && facet.requirements().isEmpty();
        }
    }

    /**
     * Every effect of this type a build's traits give a minion ({@link #levels}), those that work on a minion and that the
     * server has not turned off. Pure: for the stats worked out with no world.
     */
    public static <T extends com.avicagan.bloodandbones.parts.TraitEffect.Effect> List<Found<T>> effects(PartsData.Store store, MinionBuild build, Class<T> type) {
        return effects(store, levels(store, build), type);
    }

    /** The same from a build's trait levels, worked out once. */
    public static <T extends com.avicagan.bloodandbones.parts.TraitEffect.Effect> List<Found<T>> effects(PartsData.Store store, Map<ResourceLocation, Integer> levels,
                                                                                                        Class<T> type) {
        List<Found<T>> out = new ArrayList<>();
        levels.forEach((id, level) -> {
            com.avicagan.bloodandbones.parts.Trait trait = store.trait(id);
            for (com.avicagan.bloodandbones.parts.TraitEffect facet : trait.effects()) {
                if (type.isInstance(facet.effect()) && facet.appliesIn(com.avicagan.bloodandbones.parts.ActiveTraits.MINION)
                        && com.avicagan.bloodandbones.parts.TraitEffects.enabled(facet.effect())) {
                    out.add(new Found<>(id, level, facet, type.cast(facet.effect())));
                }
            }
        });
        return out;
    }

    /**
     * A value of one of the host's attributes, from this base, with what these trait effects always do to that attribute (the
     * passive ones with no condition), each times the trait strength, taken as vanilla's {@code AttributeInstance} takes
     * modifiers: the added values, then the shares of the base, then the shares of the whole. Its caps are the caller's.
     */
    public static double attributed(double base, Holder<Attribute> attribute, List<Found<com.avicagan.bloodandbones.parts.TraitEffects.AttributeEffect>> effects) {
        double add = 0.0;
        double ofBase = 0.0;
        double ofTotal = 1.0;
        for (Found<com.avicagan.bloodandbones.parts.TraitEffects.AttributeEffect> found : effects) {
            if (!found.always() || !found.effect().attribute().is(attribute)) {
                continue;
            }
            double amount = found.effect().amount().calculate(found.level()) * com.avicagan.bloodandbones.parts.TraitEffects.strength();
            switch (found.effect().operation()) {
                case ADD_VALUE -> add += amount;
                case ADD_MULTIPLIED_BASE -> ofBase += amount;
                case ADD_MULTIPLIED_TOTAL -> ofTotal *= 1.0 + amount;
            }
        }
        double value = base + add;
        return (value + value * ofBase) * ofTotal;
    }

    /** Whether this trait carries a passive flag of this name (lava_walk, climb...). */
    private static boolean carries(PartsData.Store store, TraitList.Resolved resolved, String flag) {
        com.avicagan.bloodandbones.parts.Trait trait = store.trait(resolved.id());
        return trait != null && trait.effects().stream().anyMatch(facet -> facet.trigger() == com.avicagan.bloodandbones.parts.Trigger.PASSIVE
                && facet.effect() instanceof FlagEffect flagEffect && flagEffect.flag().equals(flag));
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
