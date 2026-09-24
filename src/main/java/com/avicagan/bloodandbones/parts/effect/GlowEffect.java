package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Glow (docs/PARTS-AND-TRAITS.md section 5.4, type 30): the host shines in the dark, as a glow squid does. Drawn only,
 * by the client: carcass armour gets a glowing layer on the piece the trait comes from (all of them, from a full set),
 * and a minion is drawn at full brightness.
 *
 * @param colour the glow's colour, as "#rrggbb" or a number
 */
public record GlowEffect(int colour) implements TraitEffect.Effect {
    private static final Codec<Integer> HEX = Codec.withAlternative(Codec.INT, Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Not a colour: " + s);
        }
    }, c -> String.format("#%06x", c & 0xFFFFFF)));
    public static final MapCodec<GlowEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            HEX.optionalFieldOf("colour", 0x5FE8C8).forGetter(GlowEffect::colour)
    ).apply(i, GlowEffect::new));

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    /** The first glow working on the host, or null: read on the client, from its own copy of the host's traits. */
    @Nullable
    public static ActiveTraits.Found<GlowEffect> of(LivingEntity host) {
        List<ActiveTraits.Found<GlowEffect>> glows = ActiveTraits.of(host).find(GlowEffect.class);
        return glows.isEmpty() ? null : glows.getFirst();
    }

    /**
     * The pieces of armour that glow, and in what colour: each the trait counts from, or every piece for one from a full
     * set. Empty for a host with none.
     */
    public static Map<EquipmentSlot, Integer> pieces(LivingEntity host) {
        List<ActiveTraits.Found<GlowEffect>> glows = ActiveTraits.of(host).find(GlowEffect.class);
        if (glows.isEmpty()) {
            return Map.of();
        }
        Map<EquipmentSlot, Integer> out = new EnumMap<>(EquipmentSlot.class);
        for (ActiveTraits.Found<GlowEffect> found : glows) {
            if (found.entry().slot() != null) {
                out.putIfAbsent(found.entry().slot(), found.effect().colour());
            } else {
                for (EquipmentSlot slot : ActiveTraits.PIECES) {
                    out.putIfAbsent(slot, found.effect().colour());
                }
            }
        }
        return out;
    }
}
