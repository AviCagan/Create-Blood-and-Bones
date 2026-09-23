package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** What lies on the Surgery Table, flat on its top by the patient's side. */
public class SurgeryTableRenderer extends SafeBlockEntityRenderer<SurgeryTableBlockEntity> {
    public SurgeryTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(SurgeryTableBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        ItemStack item = be.item();
        if (item.isEmpty()) {
            return;
        }
        ms.pushPose();
        ms.translate(0.5F, 15.2F / 16.0F, 0.5F);
        ms.mulPose(Axis.YP.rotationDegrees((be.getBlockPos().hashCode() & 3) * 90.0F + 30.0F));
        ms.mulPose(Axis.XP.rotationDegrees(90.0F));
        ms.scale(0.6F, 0.6F, 0.6F);
        Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.FIXED, light, overlay, ms, buffer, be.getLevel(), 0);
        ms.popPose();
    }
}
