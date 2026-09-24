package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.enchantment.LevelBasedValue;

import java.util.List;

/**
 * A flag (docs/PARTS-AND-TRAITS.md section 5.6): something the host can simply do, at a strength, read where it
 * matters instead of run ({@link MotionEffects#flag}). Only passive entries count. The flags a client predicts
 * (climb, glide, bounce, powder_snow) ignore their entry's condition on both sides, since a condition can only be
 * checked on the server and the two sides must agree or the player is pulled back; the others honour it.
 *
 * @param flag     one of the closed list: climb, glide, bounce, powder_snow, ender_mask, piglin_neutral,
 *                 silent_steps, quick_draw, inverted_healing, trample, lava_walk
 * @param strength how strongly, at the trait's level (climb: 1 clings, 2 climbs; quick_draw: extra draw ticks a tick)
 */
public record FlagEffect(String flag, LevelBasedValue strength) implements TraitEffect.Effect {
    public static final String CLIMB = "climb";
    public static final String GLIDE = "glide";
    public static final String BOUNCE = "bounce";
    public static final String POWDER_SNOW = "powder_snow";
    public static final String ENDER_MASK = "ender_mask";
    public static final String PIGLIN_NEUTRAL = "piglin_neutral";
    public static final String SILENT_STEPS = "silent_steps";
    public static final String QUICK_DRAW = "quick_draw";
    public static final String INVERTED_HEALING = "inverted_healing";
    public static final String TRAMPLE = "trample";
    public static final String LAVA_WALK = "lava_walk";
    /** The whole closed list. */
    public static final List<String> FLAGS = List.of(CLIMB, GLIDE, BOUNCE, POWDER_SNOW, ENDER_MASK, PIGLIN_NEUTRAL, SILENT_STEPS, QUICK_DRAW,
            INVERTED_HEALING, TRAMPLE, LAVA_WALK);
    /** The flags whose movement the client works out for itself, so both sides read them the same way. */
    public static final List<String> PREDICTED = List.of(CLIMB, GLIDE, BOUNCE, POWDER_SNOW);

    private static final Codec<String> NAME = Codec.STRING.validate(name -> FLAGS.contains(name) ? DataResult.success(name)
            : DataResult.error(() -> "Unknown flag " + name + ", not one of " + FLAGS));

    public static final MapCodec<FlagEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NAME.fieldOf("flag").forGetter(FlagEffect::flag),
            LevelBasedValue.CODEC.optionalFieldOf("strength", LevelBasedValue.constant(1.0F)).forGetter(FlagEffect::strength)
    ).apply(i, FlagEffect::new));

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }
}
