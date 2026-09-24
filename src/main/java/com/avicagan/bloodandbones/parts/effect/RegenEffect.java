package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.LevelBasedValue;

import java.util.List;
import java.util.Optional;

/**
 * Healing (docs/PARTS-AND-TRAITS.md section 5.4, regen): {@code amount} health each time it comes up (a tick entry every
 * {@code interval}, or once for an activate). A flesh minion pays for it out of its blood, 5 mB a heart as its own
 * mending does, and only while it has more than a tenth left; a brass minion never heals itself (the brief: a brass
 * sheet or a cradle mends it). It may also clear ailments ({@code cure}: "harmful", "all", or effect ids) and then give
 * an effect ({@code mob_effect}), so one ability can be honey: poison cured, then Regeneration II.
 */
public record RegenEffect(LevelBasedValue amount, List<String> cure, Optional<MobEffectInstance> mobEffect) implements TraitEffect.Effect {
    public static final MapCodec<RegenEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.optionalFieldOf("amount", LevelBasedValue.constant(0.0F)).forGetter(RegenEffect::amount),
            Codec.STRING.listOf().optionalFieldOf("cure", List.of()).forGetter(RegenEffect::cure),
            MobEffectInstance.CODEC.optionalFieldOf("mob_effect").forGetter(RegenEffect::mobEffect)
    ).apply(i, RegenEffect::new));

    /** Below this share of its blood a flesh minion stops mending itself. */
    public static final float BLOOD_FLOOR = 0.1F;

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    @Override
    public void run(TraitContext ctx) {
        LivingEntity host = ctx.host();
        if (!host.isAlive() || host instanceof MinionEntity brass && brass.cybernetic()) {
            return;
        }
        float heal = Math.min(ctx.scaled(amount), host.getMaxHealth() - host.getHealth());
        if (heal > 0.0F && host instanceof MinionEntity minion) {
            // flesh knits on its blood, never down to its last tenth
            float spare = minion.power() - minion.stats().reservoir() * BLOOD_FLOOR;
            heal = Math.min(heal, spare / MinionEntity.REGEN_COST);
            if (heal <= 0.0F || !minion.usePower(heal * MinionEntity.REGEN_COST)) {
                heal = 0.0F;
            }
        }
        if (heal > 0.0F) {
            host.heal(heal);
            // flesh knitting back together: a soft wet squelch
            ctx.level().playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.SLIME_SQUISH_SMALL, host.getSoundSource(), 0.3F,
                    0.6F + ctx.random().nextFloat() * 0.2F);
        }
        cure(host, cure);
        mobEffect.ifPresent(effect -> host.addEffect(new MobEffectInstance(effect)));
    }

    /** Clear what a cure names from the host: "harmful" (every harmful effect), "all", or effect ids. */
    static void cure(LivingEntity host, List<String> cure) {
        for (String what : cure) {
            switch (what) {
                case "all" -> host.removeAllEffects();
                case "harmful" -> {
                    for (MobEffectInstance active : List.copyOf(host.getActiveEffects())) {
                        if (active.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                            host.removeEffect(active.getEffect());
                        }
                    }
                }
                default -> {
                    ResourceLocation id = ResourceLocation.tryParse(what);
                    if (id != null) {
                        net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(id).ifPresent(host::removeEffect);
                    }
                }
            }
        }
    }
}
