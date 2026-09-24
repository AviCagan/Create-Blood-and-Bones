package com.avicagan.bloodandbones.parts;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A list of traits in one layer of data. A plain list adds to what the layers below gave (the same trait
 * counting once, at its highest level); {"add": [...], "remove": [ids]} edits instead, and "replace": true
 * throws away what was inherited first.
 */
public record TraitList(List<TraitRef> add, List<ResourceLocation> remove, boolean replace) {
    public static final TraitList EMPTY = new TraitList(List.of(), List.of(), false);

    private static final Codec<TraitList> OBJECT = RecordCodecBuilder.<TraitList>create(i -> i.group(
            TraitRef.CODEC.listOf().optionalFieldOf("add", List.of()).forGetter(TraitList::add),
            ResourceLocation.CODEC.listOf().optionalFieldOf("remove", List.of()).forGetter(TraitList::remove),
            Codec.BOOL.optionalFieldOf("replace", false).forGetter(TraitList::replace)
    ).apply(i, TraitList::new)).validate(t -> t.add.isEmpty() && t.remove.isEmpty() && !t.replace
            ? DataResult.error(() -> "an edit needs add, remove or replace") : DataResult.success(t));

    public static final Codec<TraitList> CODEC = Codec.either(TraitRef.CODEC.listOf(), OBJECT).xmap(
            e -> e.map(list -> new TraitList(list, List.of(), false), t -> t),
            t -> t.remove.isEmpty() && !t.replace ? Either.left(t.add) : Either.right(t));

    /** A trait resolved for one mob: its id and its level there. */
    public record Resolved(ResourceLocation id, int level) {
    }

    /** Apply this edit to what the layers below gave, for this mob. */
    public List<Resolved> applyTo(List<Resolved> inherited, ResourceLocation entity) {
        Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
        if (!replace) {
            for (Resolved r : inherited) {
                out.merge(r.id(), r.level(), Math::max);
            }
        }
        remove.forEach(out::remove);
        for (TraitRef ref : add) {
            out.merge(ref.id(), ref.level().resolve(entity), Math::max);
        }
        List<Resolved> list = new ArrayList<>();
        out.forEach((id, level) -> list.add(new Resolved(id, level)));
        return List.copyOf(list);
    }

    /** Two resolved lists as one: the same trait once, at its highest level. */
    public static List<Resolved> union(List<Resolved> a, List<Resolved> b) {
        Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
        for (Resolved r : a) {
            out.merge(r.id(), r.level(), Math::max);
        }
        for (Resolved r : b) {
            out.merge(r.id(), r.level(), Math::max);
        }
        List<Resolved> list = new ArrayList<>();
        out.forEach((id, level) -> list.add(new Resolved(id, level)));
        return List.copyOf(list);
    }
}
