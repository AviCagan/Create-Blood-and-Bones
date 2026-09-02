package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.carcass.ShackleHookBlock;
import com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Draws the short chain from the hook's tip to the limb hanging on it. */
public class ShackleHookRenderer implements BlockEntityRenderer<ShackleHookBlockEntity> {
    public ShackleHookRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ShackleHookBlockEntity hook, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!hook.isOccupied() || !(hook.getLevel() instanceof ClientLevel level) || hook.hookedSubLevel() == null) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = container == null ? null : container.getSubLevel(hook.hookedSubLevel());
        if (subLevel == null || subLevel.isRemoved()) {
            return;
        }
        Pose3dc pose = ((LevelPoseProviderExtension) level).sable$getPose(subLevel);
        Vector3d limb = pose.transformPosition(hook.hookedAnchor(), new Vector3d());
        BlockPos pos = hook.getBlockPos();
        Vec3 tip = ShackleHookBlock.tip(pos, hook.getBlockState());
        if (tip.distanceToSqr(limb.x, limb.y, limb.z) < 0.3 * 0.3) {
            return; // the neck sits on the hook itself; no chain to draw
        }
        poseStack.pushPose();
        poseStack.translate(-pos.getX(), -pos.getY(), -pos.getZ());
        DragRenderer.drawChainSegment(poseStack, buffers, tip, new Vec3(limb.x, limb.y, limb.z));
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ShackleHookBlockEntity hook) {
        return new AABB(hook.getBlockPos()).inflate(4.0);
    }
}
