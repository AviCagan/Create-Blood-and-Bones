package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.parts.TraitConditions;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.LevelBasedValue;

import java.util.List;

/**
 * Turning a blow aside (docs/PARTS-AND-TRAITS.md section 5.4, deflect), read where it lands
 * ({@link MotionEffects#onProjectileImpact}, {@link MotionEffects#onIncomingDamage}) rather than run. Its own
 * {@code chance} comes up once for each projectile or blow; its entry's cooldown starts when it works.
 *
 * @param chance      the share turned aside, at the trait's level (evasive: a tenth a level)
 * @param projectiles which projectiles, each an entity id or a "#tag"; none named for any
 * @param mode        "reflect" (sent back the way it came, now the host's), "dodge" (the host twists aside and it
 *                    flies on) or "pass" (it goes straight through as if the host were not there)
 * @param arc         "any", or only what comes at the host's "front"
 * @param against     "projectile", "melee" (a creature's own blow: always dodged) or "both"
 */
public record DeflectEffect(LevelBasedValue chance, List<TraitConditions.IdOrTag<EntityType<?>>> projectiles, String mode, String arc,
                            String against) implements TraitEffect.Effect {
    public static final MapCodec<DeflectEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.optionalFieldOf("chance", LevelBasedValue.constant(1.0F)).forGetter(DeflectEffect::chance),
            TraitConditions.IdOrTag.<EntityType<?>>codec(Registries.ENTITY_TYPE).listOf().optionalFieldOf("projectiles", List.of())
                    .forGetter(DeflectEffect::projectiles),
            Codec.STRING.optionalFieldOf("mode", "reflect").forGetter(DeflectEffect::mode),
            Codec.STRING.optionalFieldOf("arc", "any").forGetter(DeflectEffect::arc),
            Codec.STRING.optionalFieldOf("against", "projectile").forGetter(DeflectEffect::against)
    ).apply(i, DeflectEffect::new));

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    public boolean turnsProjectiles() {
        return !"melee".equals(against);
    }

    public boolean turnsMelee() {
        return "melee".equals(against) || "both".equals(against);
    }

    /** Whether this projectile is one it turns. */
    public boolean turns(Projectile projectile) {
        if (projectiles.isEmpty()) {
            return true;
        }
        for (TraitConditions.IdOrTag<EntityType<?>> want : projectiles) {
            if (want.matches(projectile.getType().builtInRegistryHolder())) {
                return true;
            }
        }
        return false;
    }
}
