package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.config.BBClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Raw wounds where a carcass has been cut apart: on the stump a limb came off, and on the cut end of the
 * limb itself. A wound covers the face of the limb's box nearest its joint, just outside it (the limb's
 * side) or just inside the space it left (the stump's side). Drawn in the bone's own frame, in model
 * pixels, like the bone. Nothing is drawn in bloodless mode.
 */
public final class WoundCaps {
    private static final ResourceLocation WOUND = ResourceLocation.fromNamespaceAndPath("bloodandbones", "textures/entity/wound.png");
    /** How far off the face a wound sits, in model pixels, so it does not flicker against the box. */
    private static final float LIFT = 0.06F;

    private WoundCaps() {
    }

    /** Every wound on {@code bone}: its own cut end, and the stumps of limbs cut from it. */
    public static void draw(Rig rig, Bone bone, List<String> cuts, int color, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        if (cuts.isEmpty() || BBClientConfig.bloodless()) {
            return;
        }
        for (String cut : cuts) {
            int split = cut.indexOf('>');
            if (split <= 0) {
                continue;
            }
            String parent = cut.substring(0, split);
            String child = cut.substring(split + 1);
            if (child.equals(bone.name())) {
                cap(bone, rig.bone(parent).orElse(null), false, color, poseStack, buffers, packedLight);
            } else if (parent.equals(bone.name())) {
                Bone limb = rig.bone(child).orElse(null);
                if (limb == null) {
                    continue;
                }
                // the lost limb's frame inside this bone's frame, at their rest pose
                Quaternionf inverse = new Quaternionf(bone.rotation()).invert();
                Vector3f at = inverse.transform(new Vector3f(limb.offset()).sub(bone.offset()));
                poseStack.pushPose();
                poseStack.translate(at.x / 16.0F, at.y / 16.0F, at.z / 16.0F);
                poseStack.mulPose(new Quaternionf(inverse).mul(limb.rotation()));
                cap(limb, bone, true, color, poseStack, buffers, packedLight);
                poseStack.popPose();
            }
        }
    }

    /**
     * One wound over the face of {@code limb}'s box where it was cut: for a long limb, the end its pivot is
     * at; for a blocky part, the face pointing most toward the parent's pivot (if the two pivots coincide,
     * the face nearest its own pivot). Drawn in the limb's frame: outside the box for the limb's own cut end,
     * inside it for the stump.
     */
    private static void cap(Bone limb, @org.jetbrains.annotations.Nullable Bone parent, boolean stump, int color, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        Vector3f min = limb.boxMin();
        Vector3f max = limb.boxMax();
        Vector3f size = new Vector3f(max).sub(min);
        int axis = 0;
        boolean high = false;
        // a long limb (a leg, an arm, a tail) is cut across at the end its pivot is at
        int longest = size.x >= size.y && size.x >= size.z ? 0 : (size.y >= size.z ? 1 : 2);
        float others = Math.max(size.get((longest + 1) % 3), size.get((longest + 2) % 3));
        Vector3f toParent = parent == null ? new Vector3f()
                : new Quaternionf(limb.rotation()).invert().transform(new Vector3f(parent.offset()).sub(limb.offset()));
        if (size.get(longest) >= 1.5F * others) {
            axis = longest;
            high = Math.abs(max.get(axis)) < Math.abs(min.get(axis));
        } else if (toParent.lengthSquared() > 1.0E-4F) {
            // a blocky part (a head) is cut on the side facing what it hung from
            float best = -Float.MAX_VALUE;
            for (int a = 0; a < 3; a++) {
                float along = toParent.get(a);
                if (along > best) {
                    best = along;
                    axis = a;
                    high = true;
                }
                if (-along > best) {
                    best = -along;
                    axis = a;
                    high = false;
                }
            }
        } else {
            // the face whose plane passes nearest the pivot (the part's origin)
            float best = Float.MAX_VALUE;
            for (int a = 0; a < 3; a++) {
                float lo = Math.abs(min.get(a));
                float hi = Math.abs(max.get(a));
                if (lo < best) {
                    best = lo;
                    axis = a;
                    high = false;
                }
                if (hi < best) {
                    best = hi;
                    axis = a;
                    high = true;
                }
            }
        }
        float outward = high ? 1.0F : -1.0F;
        float plane = (high ? max.get(axis) : min.get(axis)) + outward * (stump ? -LIFT : LIFT);
        int u = (axis + 1) % 3;
        int v = (axis + 2) % 3;
        float[][] corners = {{min.get(u), min.get(v)}, {max.get(u), min.get(v)}, {max.get(u), max.get(v)}, {min.get(u), max.get(v)}};
        float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(WOUND));
        PoseStack.Pose pose = poseStack.last();
        Vector3f normal = new Vector3f();
        normal.setComponent(axis, stump ? -outward : outward);
        for (int i = 0; i < 4; i++) {
            Vector3f p = new Vector3f();
            p.setComponent(axis, plane);
            p.setComponent(u, corners[i][0]);
            p.setComponent(v, corners[i][1]);
            buffer.addVertex(pose, p.x / 16.0F, p.y / 16.0F, p.z / 16.0F)
                    .setColor(color)
                    .setUv(uvs[i][0], uvs[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(pose, normal.x, normal.y, normal.z);
        }
    }
}
