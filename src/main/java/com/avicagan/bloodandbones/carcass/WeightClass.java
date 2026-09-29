package com.avicagan.bloodandbones.carcass;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * How heavy a kind of carcass is to deal with, as the brief's "weight classes" (data/&lt;ns&gt;/weight_class/&lt;id&gt;.json).
 * A mob's groups name its class ({@code "weight_class"}, the last layer that names one winning, a mob's own file last);
 * a mob none names is put in the smallest class whose {@code up_to} its size fits, so a modded mob has one on day one.
 * The mod's classes, and which vanilla mobs fall in each, are listed in docs/ARCHITECTURE-PROPOSAL.md 15.31.
 *
 * @param upTo           the largest size, in blocks of animal (its rig's weight as flesh), this class takes by size; a class
 *                       with none is only ever named
 * @param drag           the drag penalty (CarcassDrag#penaltyFor, by the mass on the hook) is multiplied by this
 * @param bloodPerWeight blood a fresh carcass holds, mB per block of animal
 * @param buoyancy       what water lifts, as a share of what it weighs, when wholly under: over 1 it floats, under 1 it sinks
 * @param rotTime        ticks from fresh to rotten in a temperate place, for a mob whose groups and rig name none
 */
public record WeightClass(Optional<Float> upTo, float drag, float bloodPerWeight, float buoyancy, int rotTime) {
    /** Today's figures before the classes (15.28): what a mob in no class at all gets. */
    public static final WeightClass NONE = new WeightClass(Optional.empty(), 1.0F, 1000.0F, 1.0F, com.avicagan.bloodandbones.carcass.rig.Rig.DEFAULT_ROT_TIME);

    public static final Codec<WeightClass> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("up_to").forGetter(WeightClass::upTo),
            Codec.floatRange(0.0F, 10.0F).optionalFieldOf("drag", NONE.drag()).forGetter(WeightClass::drag),
            Codec.floatRange(0.0F, 100000.0F).optionalFieldOf("blood_per_weight", NONE.bloodPerWeight()).forGetter(WeightClass::bloodPerWeight),
            Codec.floatRange(0.0F, 10.0F).optionalFieldOf("buoyancy", NONE.buoyancy()).forGetter(WeightClass::buoyancy),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("rot_time", NONE.rotTime()).forGetter(WeightClass::rotTime)
    ).apply(i, WeightClass::new));
}
