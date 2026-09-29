package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * A knack for one task from a trait (docs/NEXT.md 1.6), in the minion context: an organ or a hide makes a minion better (or
 * worse) at one task, as a head's knacks do. The sniffer's Olfactory Bulb makes a better Digger. A passive entry counts,
 * multiplied into the build's knack for that task ({@code MinionStats}), its excess over 1 scaled by the server's trait
 * strength; it does nothing on armour. Read where the fitness is worked out, as {@code AttributeEffect}s are read where the
 * attributes are.
 *
 * @param task       the task's id ("bloodandbones:digger")
 * @param multiplier what the knack for it is multiplied by, at the trait's level
 */
public record TaskKnackEffect(ResourceLocation task, LevelBasedValue multiplier) implements TraitEffect.Effect {
    public static final MapCodec<TaskKnackEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("task").forGetter(TaskKnackEffect::task),
            LevelBasedValue.CODEC.fieldOf("multiplier").forGetter(TaskKnackEffect::multiplier)
    ).apply(i, TaskKnackEffect::new));

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    /** Its multiplier at this level, the trait strength applied to what it adds or takes away. */
    public float at(int level) {
        return Math.max(0.0F, UpkeepEffects.strengthened(multiplier.calculate(level)));
    }
}
