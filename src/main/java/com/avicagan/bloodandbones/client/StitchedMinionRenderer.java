package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.minion.MinionBody;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A minion as it was built: the pieces of carcass stitched together on the Surgery Table, each in its own mob's
 * look ({@link StitchedBody}). It walks the way its legs do, and lies on its side when it has run out of blood.
 */
public class StitchedMinionRenderer extends EntityRenderer<MinionEntity> {
    private static final ResourceLocation NONE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public StitchedMinionRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.5F;
    }

    @Override
    public void render(MinionEntity minion, float yaw, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light) {
        MinionBuild build = minion.build().orElse(null);
        if (build == null) {
            return;
        }
        MinionBody.Layout layout = StitchedBody.layout(build);
        shadowRadius = Math.max(0.25F, layout.width() * 0.5F);
        float bodyYaw = Mth.rotLerp(partialTicks, minion.yBodyRotO, minion.yBodyRot);
        float headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTicks, minion.yHeadRotO, minion.yHeadRot) - bodyYaw);
        float speed = minion.walkAnimation.speed(partialTicks);
        StitchedBody.Motion motion = new StitchedBody.Motion(minion.walkAnimation.position(partialTicks), speed, headYaw,
                Mth.lerp(partialTicks, minion.xRotO, minion.getXRot()), minion.stats().mode(), minion.tickCount + partialTicks);
        boolean lying = minion.poweredDown() || minion.deathTime > 0;
        // hurt, it flashes red as any mob does
        int tint = minion.hurtTime > 0 || minion.deathTime > 0 ? 0xFFFF9999 : -1;
        ms.pushPose();
        ms.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        // entity models are drawn upside down, their ground at 24 pixels (as LivingEntityRenderer does)
        ms.scale(-1.0F, -1.0F, 1.0F);
        ms.translate(0.0F, -1.501F, 0.0F);
        // a glowing minion (the glow trait) shines whatever the dark round it
        StitchedBody.draw(layout, motion, lying, tint, ms, buffers, com.avicagan.bloodandbones.client.effect.SocialClient.minionLight(minion, light));
        ms.popPose();
        if (minion.isSaddled() && !lying) {
            // a plain saddle thrown over its back
            org.joml.Vector3f at = StitchedBody.saddlePoint(layout);
            ms.pushPose();
            ms.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
            ms.translate(at.x, at.y + 0.02F, at.z);
            ms.mulPose(Axis.XP.rotationDegrees(90.0F));
            ms.scale(0.75F, 0.75F, 0.75F);
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE),
                    net.minecraft.world.item.ItemDisplayContext.FIXED, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, ms, buffers, minion.level(), 0);
            ms.popPose();
        }
        super.render(minion, yaw, partialTicks, ms, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MinionEntity minion) {
        return NONE;
    }
}
