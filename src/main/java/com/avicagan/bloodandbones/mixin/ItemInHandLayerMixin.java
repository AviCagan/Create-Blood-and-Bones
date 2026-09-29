package com.avicagan.bloodandbones.mixin;

import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In third person, nothing is held by a player's arm that is gone (the brief's slot system: an empty arm holds nothing).
 * Vanilla draws each hand's item from this one method, for players and every humanoid mob; first person is
 * BodyRendering's {@code RenderHandEvent}.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void bloodandbones$noHandNoItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack poseStack,
                                           MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!BodyEffects.shows(entity, BodyPart.arm(arm))) {
            ci.cancel();
        }
    }
}
