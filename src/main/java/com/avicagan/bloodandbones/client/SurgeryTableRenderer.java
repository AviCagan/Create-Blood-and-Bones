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

/** What lies on the Surgery Table: one thing flat on its top by the patient's side, or a minion being built. */
public class SurgeryTableRenderer extends SafeBlockEntityRenderer<SurgeryTableBlockEntity> {
    public SurgeryTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(SurgeryTableBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        be.build().ifPresent(build -> drawBuild(be, build, ms, buffer, light));
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

    /** The minion so far, on its side on the table, shrunk to fit. */
    private static void drawBuild(SurgeryTableBlockEntity be, com.avicagan.bloodandbones.minion.MinionBuild build, PoseStack ms, MultiBufferSource buffer, int light) {
        com.avicagan.bloodandbones.minion.MinionBody.Layout layout = StitchedBody.layout(build);
        float longest = Math.max(layout.width(), layout.height());
        // a big body is shrunk to lie within the table and a little over its ends, as a carcass would
        float scale = Math.min(0.8F, 1.2F / Math.max(0.1F, longest));
        ms.pushPose();
        ms.translate(0.5F, 1.0F, 0.5F);
        ms.mulPose(Axis.YP.rotationDegrees((be.getBlockPos().hashCode() & 3) * 90.0F));
        ms.scale(scale, scale, scale);
        ms.scale(-1.0F, -1.0F, 1.0F);
        ms.translate(0.0F, -1.501F, 0.0F);
        StitchedBody.draw(layout, StitchedBody.Motion.STILL, true, -1, ms, buffer, light);
        ms.popPose();
    }
}
