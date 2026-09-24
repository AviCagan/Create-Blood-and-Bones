package com.avicagan.bloodandbones.mixin;

import com.avicagan.bloodandbones.parts.effect.FlagEffect;
import com.avicagan.bloodandbones.parts.effect.MotionEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The inverted_healing flag (docs/PARTS-AND-TRAITS.md section 5.6) works as the undead do: instant health hurts and
 * instant damage heals. Vanilla asks this one method wherever those two act, drunk, splashed, from a cloud or an arrow;
 * {@code MobEffectEvent.Applicable} would see only the few added as ordinary effects, since potions apply them directly.
 */
@Mixin(LivingEntity.class)
public abstract class InvertedHealingMixin {
    @Inject(method = "isInvertedHealAndHarm", at = @At("HEAD"), cancellable = true)
    private void bloodandbones$invertedHealing(CallbackInfoReturnable<Boolean> cir) {
        if (MotionEffects.flag((LivingEntity) (Object) this, FlagEffect.INVERTED_HEALING) > 0) {
            cir.setReturnValue(true);
        }
    }
}
