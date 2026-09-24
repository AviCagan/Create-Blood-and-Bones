package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.parts.TraitConditions;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * How far off mobs notice the host (docs/PARTS-AND-TRAITS.md section 5.4, visibility), as a mob's own head worn
 * halves it for its kind; read where they look ({@code LivingVisibilityEvent}, {@link MotionEffects#onVisibility}).
 *
 * @param multiplier the host's visibility times this, at the trait's level (a change the trait strength scales)
 * @param vs         to which lookers, each an entity id or a "#tag"; none named for all
 */
public record VisibilityEffect(LevelBasedValue multiplier, List<TraitConditions.IdOrTag<EntityType<?>>> vs) implements TraitEffect.Effect {
    public static final MapCodec<VisibilityEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.fieldOf("multiplier").forGetter(VisibilityEffect::multiplier),
            TraitConditions.IdOrTag.<EntityType<?>>codec(Registries.ENTITY_TYPE).listOf().optionalFieldOf("vs", List.of()).forGetter(VisibilityEffect::vs)
    ).apply(i, VisibilityEffect::new));

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    /** Whether it works against this looker (any, when nobody is named or nobody is looking in particular). */
    public boolean against(@Nullable Entity looker) {
        if (vs.isEmpty()) {
            return true;
        }
        if (looker == null) {
            return false;
        }
        for (TraitConditions.IdOrTag<EntityType<?>> want : vs) {
            if (want.matches(looker.getType().builtInRegistryHolder())) {
                return true;
            }
        }
        return false;
    }
}
