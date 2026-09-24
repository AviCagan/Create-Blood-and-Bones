package com.avicagan.bloodandbones.parts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;

/**
 * What a piece of carcass armour is made of (docs/PARTS-AND-TRAITS.md section 7): its body's mob, the mob of
 * a chestplate's shoulders or a pair of leggings' hips if other, the hide and the organ fitted, the tier. The
 * numbers are looked up from these when worn.
 */
public record CarcassArmour(String piece, ResourceLocation body, boolean baby, Optional<ResourceLocation> shoulders,
                            Optional<ResourceLocation> hips, Optional<Hide> hide, Optional<Organ> organ, int tier) {
    /**
     * A hide fitted over the piece: the mob it came from (none for a plain raw hide) and which item it was,
     * to give it back when another replaces it.
     */
    public record Hide(Optional<ResourceLocation> entity, Item item) {
        public static final Codec<Hide> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("entity").forGetter(Hide::entity),
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Hide::item)
        ).apply(i, Hide::new));
    }

    /** An organ fitted into the piece: which organ (bloodandbones:heart...), and the mob it was cut out of. */
    public record Organ(ResourceLocation organ, ResourceLocation entity, boolean baby) {
        public static final Codec<Organ> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("organ").forGetter(Organ::organ),
                ResourceLocation.CODEC.fieldOf("entity").forGetter(Organ::entity),
                Codec.BOOL.optionalFieldOf("baby", false).forGetter(Organ::baby)
        ).apply(i, Organ::new));
    }

    public static final Codec<CarcassArmour> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("piece").forGetter(CarcassArmour::piece),
            ResourceLocation.CODEC.fieldOf("body").forGetter(CarcassArmour::body),
            Codec.BOOL.optionalFieldOf("baby", false).forGetter(CarcassArmour::baby),
            ResourceLocation.CODEC.optionalFieldOf("shoulders").forGetter(CarcassArmour::shoulders),
            ResourceLocation.CODEC.optionalFieldOf("hips").forGetter(CarcassArmour::hips),
            Hide.CODEC.optionalFieldOf("hide").forGetter(CarcassArmour::hide),
            Organ.CODEC.optionalFieldOf("organ").forGetter(CarcassArmour::organ),
            Codec.INT.optionalFieldOf("tier", 0).forGetter(CarcassArmour::tier)
    ).apply(i, CarcassArmour::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CarcassArmour> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** A new piece of one mob, nothing fitted. */
    public static CarcassArmour of(String piece, ResourceLocation body, boolean baby) {
        return new CarcassArmour(piece, body, baby, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 0);
    }

    public CarcassArmour withHide(Optional<Hide> hide) {
        return new CarcassArmour(piece, body, baby, shoulders, hips, hide, organ, tier);
    }

    public CarcassArmour withOrgan(Optional<Organ> organ) {
        return new CarcassArmour(piece, body, baby, shoulders, hips, hide, organ, tier);
    }

    public CarcassArmour withTier(int tier) {
        return new CarcassArmour(piece, body, baby, shoulders, hips, hide, organ, tier);
    }

    /** How many hides of one mob cover this piece: one a helmet or boots, two leggings, three a chestplate. */
    public int hidesNeeded() {
        return switch (piece) {
            case "chestplate" -> 3;
            case "leggings" -> 2;
            default -> 1;
        };
    }

    /** The slot of the body's mob whose traits this piece takes. */
    public String bodySlot() {
        return switch (piece) {
            case "helmet" -> "head";
            case "chestplate" -> "torso";
            default -> "leg";
        };
    }

    /** The armour traits of this piece, for its mob and its extra parts, its hide and its organ, at their levels. */
    public List<TraitList.Resolved> traits(PartsData.Store store) {
        List<TraitList.Resolved> out = store.resolve(body, baby).armourTraits(bodySlot(), piece);
        if (shoulders.isPresent()) {
            out = TraitList.union(out, store.resolve(shoulders.get(), false).armourTraits("arm", "shoulders"));
        }
        if (hips.isPresent()) {
            out = TraitList.union(out, store.resolve(hips.get(), false).armourTraits("tail", "hips"));
        }
        if (hide.isPresent() && hide.get().entity().isPresent()) {
            out = TraitList.union(out, store.resolve(hide.get().entity().get(), false).hide());
        }
        if (organ.isPresent()) {
            // the organ's armour traits for its mob; a mob whose data names none gives nothing, but it still fits
            ResolvedMob.Organ traits = store.resolve(organ.get().entity(), organ.get().baby()).organs().get(organ.get().organ());
            if (traits != null) {
                out = TraitList.union(out, traits.armour());
            }
        }
        return out;
    }

    /** Whether everything in it came from this one mob (for a full set). A plain raw hide comes from no mob, so it does not count. */
    public boolean pure(ResourceLocation mob) {
        return body.equals(mob) && shoulders.map(mob::equals).orElse(true) && hips.map(mob::equals).orElse(true)
                && hide.flatMap(Hide::entity).map(mob::equals).orElse(true) && organ.map(o -> o.entity().equals(mob)).orElse(true);
    }
}
