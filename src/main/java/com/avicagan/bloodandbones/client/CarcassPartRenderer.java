package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Draws one limb: the vanilla model part the bone came from, wearing the mob's texture, placed so the
 * part's box lines up with the physics box of the block cells.
 */
public class CarcassPartRenderer implements BlockEntityRenderer<CarcassPartBlockEntity> {
    private final EntityModelSet modelSet;
    private final Map<ModelLayerLocation, Optional<ModelPart>> roots = new HashMap<>();

    public CarcassPartRenderer(BlockEntityRendererProvider.Context context) {
        this.modelSet = context.getModelSet();
    }

    @Override
    public void render(CarcassPartBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!be.isRoot()) {
            return;
        }
        ModelPart part = resolve(be);
        if (part == null) {
            return;
        }
        Vector3f min = be.boxMin();
        int color = rotColor(be.freshness());
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(be.texture()));
        poseStack.pushPose();
        // The block cell's minimum corner is the box's minimum corner, and the part's own origin sits
        // at -boxMin from there (in pixels).
        poseStack.translate(-min.x / 16.0F, -min.y / 16.0F, -min.z / 16.0F);
        drawPart(part, poseStack, buffer, packedLight, color);
        // resting form: the other limbs, posed relative to this bone's frame
        for (CarcassPartBlockEntity.MergedPart mergedPart : be.merged()) {
            ModelPart other = resolve(be, mergedPart.partPath());
            if (other == null) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(mergedPart.position().x, mergedPart.position().y, mergedPart.position().z);
            poseStack.mulPose(mergedPart.orientation());
            drawPart(other, poseStack, buffer, packedLight, color);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void drawPart(ModelPart part, PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer buffer, int packedLight, int color) {
        PartPose saved = part.storePose();
        part.loadPose(PartPose.ZERO);
        part.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, color);
        part.loadPose(saved);
    }

    /** Fresh meat is untinted; as it rots it greys and greens. */
    private static int rotColor(float freshness) {
        float f = Math.max(0.0F, Math.min(1.0F, freshness));
        float r = 1.0F - 0.45F * (1.0F - f);
        float g = 1.0F - 0.30F * (1.0F - f);
        float b = 1.0F - 0.50F * (1.0F - f);
        return net.minecraft.util.FastColor.ARGB32.colorFromFloat(1.0F, r, g, b);
    }

    @Nullable
    private ModelPart resolve(CarcassPartBlockEntity be) {
        return resolve(be, be.partPath());
    }

    @Nullable
    private ModelPart resolve(CarcassPartBlockEntity be, String partPath) {
        ModelLayerLocation layer = new ModelLayerLocation(be.model(), be.layer());
        Optional<ModelPart> root = roots.computeIfAbsent(layer, l -> {
            try {
                return Optional.of(modelSet.bakeLayer(l));
            } catch (Exception e) {
                BloodAndBones.LOGGER.warn("No model layer {} for carcass rendering", l);
                return Optional.empty();
            }
        });
        if (root.isEmpty()) {
            return null;
        }
        ModelPart part = root.get();
        for (String segment : partPath.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            if (!part.hasChild(segment)) {
                return null;
            }
            part = part.getChild(segment);
        }
        return part;
    }

    @Override
    public AABB getRenderBoundingBox(CarcassPartBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        Vector3f size = be.boxSize();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + Math.max(1.0, size.x / 16.0), pos.getY() + Math.max(1.0, size.y / 16.0), pos.getZ() + Math.max(1.0, size.z / 16.0));
    }
}
