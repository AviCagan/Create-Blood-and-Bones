package com.avicagan.bloodandbones.mixin;

import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Armour is not drawn over a player's limb that is gone: a chestplate's sleeve over a missing arm, a legging's or a boot's
 * leg over a missing leg. Vanilla shows the armour model's parts for each slot just before drawing that piece; this hides
 * the missing limbs' parts right after, so the rest of the piece is drawn as ever.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;setPartVisibility(Lnet/minecraft/client/model/HumanoidModel;Lnet/minecraft/world/entity/EquipmentSlot;)V",
                    shift = At.Shift.AFTER))
    private void bloodandbones$noLimbNoArmour(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity, EquipmentSlot slot, int packedLight,
                                             HumanoidModel<?> model, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                             float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!BodyEffects.shows(entity, BodyPart.LEFT_ARM)) {
            model.leftArm.visible = false;
        }
        if (!BodyEffects.shows(entity, BodyPart.RIGHT_ARM)) {
            model.rightArm.visible = false;
        }
        if (!BodyEffects.shows(entity, BodyPart.LEFT_LEG)) {
            model.leftLeg.visible = false;
        }
        if (!BodyEffects.shows(entity, BodyPart.RIGHT_LEG)) {
            model.rightLeg.visible = false;
        }
    }
}
