package com.avicagan.bloodandbones.parts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * What a piece of carcass armour is made of (docs/PARTS-AND-TRAITS.md section 7): its body's mob, the mob of
 * a chestplate's shoulders or a pair of leggings' hips if other, the hide fitted, the tier. The numbers are
 * looked up from these when worn.
 */
public record CarcassArmour(String piece, ResourceLocation body, boolean baby, Optional<ResourceLocation> shoulders,
                            Optional<ResourceLocation> hips, Optional<ResourceLocation> hide, int tier) {
    public static final Codec<CarcassArmour> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("piece").forGetter(CarcassArmour::piece),
            ResourceLocation.CODEC.fieldOf("body").forGetter(CarcassArmour::body),
            Codec.BOOL.optionalFieldOf("baby", false).forGetter(CarcassArmour::baby),
            ResourceLocation.CODEC.optionalFieldOf("shoulders").forGetter(CarcassArmour::shoulders),
            ResourceLocation.CODEC.optionalFieldOf("hips").forGetter(CarcassArmour::hips),
            ResourceLocation.CODEC.optionalFieldOf("hide").forGetter(CarcassArmour::hide),
            Codec.INT.optionalFieldOf("tier", 0).forGetter(CarcassArmour::tier)
    ).apply(i, CarcassArmour::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CarcassArmour> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** The slot of the body's mob whose traits this piece takes. */
    public String bodySlot() {
        return switch (piece) {
            case "helmet" -> "head";
            case "chestplate" -> "torso";
            default -> "leg";
        };
    }

    /** The armour traits of this piece, for its mob and its extra parts, at their levels. */
    public List<TraitList.Resolved> traits(PartsData.Store store) {
        List<TraitList.Resolved> out = store.resolve(body, baby).armourTraits(bodySlot(), piece);
        if (shoulders.isPresent()) {
            out = TraitList.union(out, store.resolve(shoulders.get(), false).armourTraits("arm", "shoulders"));
        }
        if (hips.isPresent()) {
            out = TraitList.union(out, store.resolve(hips.get(), false).armourTraits("tail", "hips"));
        }
        if (hide.isPresent()) {
            out = TraitList.union(out, store.resolve(hide.get(), false).hide());
        }
        return out;
    }

    /** Whether everything in it came from this one mob (for a full set). */
    public boolean pure(ResourceLocation mob) {
        return body.equals(mob) && shoulders.map(mob::equals).orElse(true) && hips.map(mob::equals).orElse(true)
                && hide.map(mob::equals).orElse(true);
    }
}
