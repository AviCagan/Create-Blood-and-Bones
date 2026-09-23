package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassLook;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.ExtraPart;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drawing one bone of a rig: the vanilla model part it came from, in the look the mob wore, with child
 * bones hidden and attached parts along. Shared by the limb cells in the world and carried pieces.
 */
public final class CarcassModels {
    private static final Map<ModelLayerLocation, Optional<ModelPart>> ROOTS = new ConcurrentHashMap<>();
    /** What a skinned carcass shows in bloodless mode: pale, with no blood. */
    private static final ResourceLocation FLESH_BLOODLESS = ResourceLocation.fromNamespaceAndPath("bloodandbones", "textures/entity/flesh_bloodless.png");

    private CarcassModels() {
    }

    /** The skin, then each coat, for a bone's own part and everything attached to it. */
    public static void drawBone(Rig rig, Bone bone, ResourceLocation texture, List<CarcassLook.Coat> passes, int rot,
                                PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        if (texture.equals(CarcassLook.FLESH) && com.avicagan.bloodandbones.config.BBClientConfig.bloodless()) {
            texture = FLESH_BLOODLESS;
        }
        drawPass(rig, bone, rig.layer(), texture, rot, poseStack, buffers, packedLight);
        for (CarcassLook.Coat coat : passes) {
            int color = coat.tint() == -1 ? rot : FastColor.ARGB32.multiply(coat.tint() | 0xFF000000, rot);
            drawPass(rig, bone, coat.layer(), coat.texture(), color, poseStack, buffers, packedLight);
        }
    }

    /** Two frames of maggots, swapped a few times a second so they squirm. */
    private static final ResourceLocation[] MAGGOTS = {
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "textures/entity/maggots_0.png"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "textures/entity/maggots_1.png")};
    /** Freshness below which maggots crawl over the meat. */
    public static final float MAGGOTS_BELOW = 0.15F;

    /** Maggots over a bone of a carcass nearly or wholly rotten (not a bloodless mob's, not in bloodless mode). */
    public static void drawMaggots(Rig rig, Bone bone, float freshness, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        if (freshness >= MAGGOTS_BELOW || com.avicagan.bloodandbones.config.BBClientConfig.bloodless()) {
            return;
        }
        boolean bloodless = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(rig.entity())
                .map(type -> type.is(com.avicagan.bloodandbones.registry.BBTags.BLOODLESS)).orElse(false);
        if (bloodless) {
            return;
        }
        ResourceLocation frame = MAGGOTS[(int) ((net.minecraft.Util.getMillis() / 300L) % 2L)];
        drawPass(rig, bone, rig.layer(), frame, 0xFFFFFFFF, poseStack, buffers, packedLight);
    }

    private static void drawPass(Rig rig, Bone bone, String layer, ResourceLocation texture, int color, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        ModelLayerLocation location = layerOf(rig, layer);
        ModelPart part = resolve(location, bone.part());
        if (part == null) {
            return;
        }
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        List<ModelPart> hidden = new ArrayList<>();
        for (String path : bone.hide()) {
            ModelPart child = descend(part, path);
            if (child != null && child.visible) {
                child.visible = false;
                hidden.add(child);
            }
        }
        try {
            drawPart(part, poseStack, buffer, packedLight, color, new org.joml.Vector3f(bone.scale()).mul(rig.scale()));
        } finally {
            for (ModelPart child : hidden) {
                child.visible = true;
            }
        }
        for (ExtraPart extra : bone.extras()) {
            ModelPart other = resolve(location, extra.part());
            if (other == null) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(extra.offset().x / 16.0F, extra.offset().y / 16.0F, extra.offset().z / 16.0F);
            poseStack.mulPose(extra.rotation());
            drawPart(other, poseStack, buffer, packedLight, color, new org.joml.Vector3f(bone.scale()).mul(rig.scale()));
            poseStack.popPose();
        }
    }

    /** A coat's layer is one of the rig model's own ("fur"), or another model's in full ("minecraft:llama#decor"). */
    private static ModelLayerLocation layerOf(Rig rig, String layer) {
        int hash = layer.indexOf('#');
        if (hash > 0) {
            ResourceLocation model = ResourceLocation.tryParse(layer.substring(0, hash));
            if (model != null) {
                return new ModelLayerLocation(model, layer.substring(hash + 1));
            }
        }
        return new ModelLayerLocation(rig.model(), layer);
    }

    private static void drawPart(ModelPart part, PoseStack poseStack, VertexConsumer buffer, int packedLight, int color, org.joml.Vector3f scale) {
        PartPose saved = part.storePose();
        part.loadPose(PartPose.ZERO);
        poseStack.pushPose();
        poseStack.scale(scale.x, scale.y, scale.z);
        try {
            part.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, color);
        } finally {
            poseStack.popPose();
            part.loadPose(saved);
        }
    }

    /**
     * A single carried piece, centred on the pose's origin and scaled so its longest side is {@code size}
     * blocks, the right way up. {@code tint} (ARGB, -1 for none) is laid over the rot colour: cooking browns it.
     */
    public static void drawPiece(com.avicagan.bloodandbones.item.CarcassPieceItem.Piece piece, Rig rig, Bone bone, float size, int tint,
                                 PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        org.joml.Vector3f min = bone.boxMin();
        org.joml.Vector3f max = bone.boxMax();
        float largest = Math.max(max.x - min.x, Math.max(max.y - min.y, max.z - min.z)) / 16.0F;
        float fit = size / Math.max(largest, 0.3F);
        poseStack.pushPose();
        poseStack.scale(fit, fit, fit);
        // entity models are drawn upside down in their own space; turn the piece the right way up
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0F));
        poseStack.translate(-(min.x + max.x) / 32.0F, -(min.y + max.y) / 32.0F, -(min.z + max.z) / 32.0F);
        List<CarcassLook.Coat> coats = piece.coats().stream().map(c -> new CarcassLook.Coat(c.layer(), c.texture(), c.tint())).toList();
        int color = rotColor(piece.freshness());
        if (tint != -1) {
            color = FastColor.ARGB32.multiply(color, tint);
        }
        drawBone(rig, bone, piece.texture(), coats, color, poseStack, buffers, packedLight);
        drawMaggots(rig, bone, piece.freshness(), poseStack, buffers, packedLight);
        // a piece was cut from its parent, and anything that hung off it is gone: every end is a wound
        // (unless the mob has no blood: a skeleton's cut ends are dry)
        List<String> cuts = new ArrayList<>();
        boolean bloody = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(piece.entity())
                .map(type -> !type.is(com.avicagan.bloodandbones.registry.BBTags.BLOODLESS)).orElse(true);
        bone.parent().ifPresent(parent -> cuts.add(parent + ">" + bone.name()));
        for (Bone other : rig.bones()) {
            if (other.parent().filter(bone.name()::equals).isPresent()) {
                cuts.add(bone.name() + ">" + other.name());
            }
        }
        if (bloody) {
            WoundCaps.draw(rig, bone, cuts, color, poseStack, buffers, packedLight);
        }
        poseStack.popPose();
    }

    /** Fresh meat is untinted; as it rots it greys and greens. */
    public static int rotColor(float freshness) {
        float f = Math.max(0.0F, Math.min(1.0F, freshness));
        float r = 1.0F - 0.45F * (1.0F - f);
        float g = 1.0F - 0.30F * (1.0F - f);
        float b = 1.0F - 0.50F * (1.0F - f);
        return FastColor.ARGB32.colorFromFloat(1.0F, r, g, b);
    }

    @Nullable
    private static ModelPart resolve(ModelLayerLocation layer, String partPath) {
        Optional<ModelPart> root = ROOTS.computeIfAbsent(layer, l -> {
            try {
                return Optional.of(Minecraft.getInstance().getEntityModels().bakeLayer(l));
            } catch (Exception e) {
                BloodAndBones.LOGGER.warn("No model layer {} for carcass rendering", l);
                return Optional.empty();
            }
        });
        return root.map(r -> descend(r, partPath)).orElse(null);
    }

    @Nullable
    static ModelPart descend(ModelPart part, String partPath) {
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
}
