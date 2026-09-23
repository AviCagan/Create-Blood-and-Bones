package com.avicagan.bloodandbones.carcass.trolley;

import com.avicagan.bloodandbones.client.DragRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.UUID;

/** Client: draws the trolley (TODO: a partial model on the chain) and a chain down to the carcass's neck. */
public class ShackleTrolleyRenderer extends EntityRenderer<ShackleTrolleyEntity> {
    public ShackleTrolleyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ShackleTrolleyEntity trolley, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        super.render(trolley, yaw, partialTick, poseStack, buffers, light);
        UUID id = trolley.clientSubLevel().orElse(null);
        if (id == null || !(trolley.level() instanceof ClientLevel level)) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = container == null ? null : container.getSubLevel(id);
        if (subLevel == null || subLevel.isRemoved()) {
            return;
        }
        Pose3dc pose = ((LevelPoseProviderExtension) level).sable$getPose(subLevel);
        Vector3d neck = pose.transformPosition(trolley.clientAnchorPlot(), new Vector3d());
        Vec3 origin = trolley.getPosition(partialTick);
        poseStack.pushPose();
        poseStack.translate(-origin.x, -origin.y, -origin.z); // DragRenderer draws in world coordinates
        DragRenderer.drawChainSegment(poseStack, buffers, origin, new Vec3(neck.x, neck.y, neck.z));
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(ShackleTrolleyEntity trolley, Frustum frustum, double x, double y, double z) {
        return true; // the chain reaches down to the carcass, outside the tiny hitbox
    }

    @Override
    public ResourceLocation getTextureLocation(ShackleTrolleyEntity trolley) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
