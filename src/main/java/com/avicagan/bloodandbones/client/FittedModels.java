package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drawing a generic body (a mob with no rig file, GenericRig) as much like the mob as the client can make it. Its model is
 * read here, on the client, where the game keeps it: the layer registered under the mob's own id ("examplemod:beast",
 * layer "main", as the game's own mobs and most mods name theirs), or else the model its renderer draws with. Each bone
 * wears the first model part found with a name it lists, the part and what hangs under it (but other bones' parts)
 * stretched to fill the bone's box, in the skin its renderer gives it. A bone with no such part, or a mob whose model is
 * not made of parts the game can read, is a box in its skin.
 */
public final class FittedModels {
    /** The model's root, by mob: its own layer, or its renderer's model. Empty when neither can be read. */
    private static final Map<ResourceLocation, Optional<ModelPart>> ROOTS = new ConcurrentHashMap<>();
    /** The skin its renderer draws a plain one of it in, by mob. */
    private static final Map<ResourceLocation, Optional<ResourceLocation>> SKINS = new ConcurrentHashMap<>();
    /** What each bone wears, by rig and bone: the part, how it is stretched, and which parts under it are other bones. */
    private static final Map<Bone, Optional<Fit>> FITS = new java.util.WeakHashMap<>();

    private record Fit(ModelPart part, Quaternionf turn, Vector3f min, Vector3f max, List<ModelPart> others) {
    }

    private FittedModels() {
    }

    /** Forget what was read (the models reload with resource packs). */
    public static void clear() {
        ROOTS.clear();
        SKINS.clear();
        synchronized (FITS) {
            FITS.clear();
        }
    }

    /** The skin to draw a generic body in: the one its renderer gives a plain one of it, else what the server guessed. */
    public static ResourceLocation skin(Rig rig, ResourceLocation guessed) {
        return SKINS.computeIfAbsent(rig.entity(), id -> {
            try {
                Entity plain = plain(id);
                if (plain != null) {
                    @SuppressWarnings("unchecked")
                    EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(plain);
                    return Optional.ofNullable(renderer.getTextureLocation(plain));
                }
            } catch (RuntimeException e) {
                BloodAndBones.LOGGER.warn("Cannot ask {}'s renderer for its skin: {}", id, e.toString());
            }
            return Optional.empty();
        }).orElse(guessed);
    }

    /** A plain one of the mob, never added to the world, to ask its renderer about; null if one cannot be made. */
    @Nullable
    private static Entity plain(ResourceLocation id) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (type.isEmpty() || Minecraft.getInstance().level == null) {
            return null;
        }
        try {
            return type.get().create(Minecraft.getInstance().level);
        } catch (RuntimeException e) {
            BloodAndBones.LOGGER.warn("Cannot make a plain {} to read its model: {}", id, e.toString());
            return null;
        }
    }

    @Nullable
    private static ModelPart root(ResourceLocation id) {
        return ROOTS.computeIfAbsent(id, key -> {
            ModelLayerLocation layer = new ModelLayerLocation(key, "main");
            try {
                return Optional.of(Minecraft.getInstance().getEntityModels().bakeLayer(layer));
            } catch (RuntimeException e) {
                // no layer under its own name: the model its renderer draws with, if the game can read its parts
            }
            try {
                Entity plain = plain(key);
                if (plain != null && Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(plain) instanceof LivingEntityRenderer<?, ?> living
                        && living.getModel() instanceof HierarchicalModel<?> model) {
                    return Optional.of(model.root());
                }
            } catch (RuntimeException e) {
                BloodAndBones.LOGGER.warn("Cannot read {}'s model: {}", key, e.toString());
            }
            return Optional.empty();
        }).orElse(null);
    }

    /** Draw one bone of a generic body, the pose at the bone's own origin (its pivot), as CarcassModels draws a bone. */
    public static void draw(Rig rig, Bone bone, ResourceLocation texture, int color, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        Optional<Fit> fit;
        synchronized (FITS) {
            fit = FITS.computeIfAbsent(bone, b -> fit(rig, b));
        }
        Vector3f boxMin = bone.boxMin();
        Vector3f boxMax = bone.boxMax();
        if (fit.isEmpty()) {
            box(boxMin, boxMax, poseStack, buffer, packedLight, color);
            return;
        }
        Fit f = fit.get();
        Vector3f size = new Vector3f(f.max()).sub(f.min());
        poseStack.pushPose();
        poseStack.translate(boxMin.x / 16.0F, boxMin.y / 16.0F, boxMin.z / 16.0F);
        poseStack.scale((boxMax.x - boxMin.x) / Math.max(0.01F, size.x), (boxMax.y - boxMin.y) / Math.max(0.01F, size.y),
                (boxMax.z - boxMin.z) / Math.max(0.01F, size.z));
        poseStack.translate(-f.min().x / 16.0F, -f.min().y / 16.0F, -f.min().z / 16.0F);
        poseStack.mulPose(f.turn());
        PartPose saved = f.part().storePose();
        List<ModelPart> hidden = new ArrayList<>();
        for (ModelPart other : f.others()) {
            if (other.visible) {
                other.visible = false;
                hidden.add(other);
            }
        }
        try {
            f.part().loadPose(PartPose.ZERO);
            f.part().render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, color);
        } finally {
            f.part().loadPose(saved);
            hidden.forEach(p -> p.visible = true);
            poseStack.popPose();
        }
    }

    /**
     * The part a bone wears and its bounds as it rests in the model (turned as it is there), with what hangs under it,
     * less other bones' parts; empty if its model has none of the names.
     */
    private static Optional<Fit> fit(Rig rig, Bone bone) {
        ModelPart root = root(rig.entity());
        if (root == null) {
            return Optional.empty();
        }
        ModelPart part = null;
        for (String name : bone.part().split("\\|")) {
            part = find(root, name);
            if (part != null) {
                break;
            }
        }
        if (part == null) {
            return Optional.empty();
        }
        // the other bones' parts, which draw in their own place
        List<ModelPart> others = new ArrayList<>();
        for (Bone other : rig.bones()) {
            if (other == bone) {
                continue;
            }
            for (String name : other.part().split("\\|")) {
                ModelPart found = find(root, name);
                if (found != null) {
                    if (found != part) {
                        others.add(found);
                    }
                    break;
                }
            }
        }
        PartPose rest = part.getInitialPose();
        Quaternionf turn = new Quaternionf().rotationZYX(rest.zRot, rest.yRot, rest.xRot);
        Vector3f min = new Vector3f(Float.MAX_VALUE);
        Vector3f max = new Vector3f(-Float.MAX_VALUE);
        bounds(part, new Matrix4f().rotation(turn), others, min, max);
        if (min.x > max.x) {
            return Optional.empty();
        }
        return Optional.of(new Fit(part, turn, min, max, List.copyOf(others)));
    }

    /** The shallowest part of this name under the root. */
    @Nullable
    private static ModelPart find(ModelPart root, String name) {
        Deque<ModelPart> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            ModelPart at = queue.poll();
            for (Map.Entry<String, ModelPart> child : at.children.entrySet()) {
                if (child.getKey().equals(name)) {
                    return child.getValue();
                }
                queue.add(child.getValue());
            }
        }
        return null;
    }

    /** Every cube's corners, as the part and what hangs under it rest, but other bones' parts. */
    private static void bounds(ModelPart part, Matrix4f at, List<ModelPart> others, Vector3f min, Vector3f max) {
        for (ModelPart.Cube cube : part.cubes) {
            for (int i = 0; i < 8; i++) {
                Vector3f corner = at.transformPosition(new Vector3f((i & 1) == 0 ? cube.minX : cube.maxX, (i & 2) == 0 ? cube.minY : cube.maxY,
                        (i & 4) == 0 ? cube.minZ : cube.maxZ));
                min.min(corner);
                max.max(corner);
            }
        }
        for (ModelPart child : part.children.values()) {
            if (others.contains(child) || !child.visible) {
                continue;
            }
            PartPose pose = child.getInitialPose();
            Matrix4f next = new Matrix4f(at).translate(pose.x, pose.y, pose.z)
                    .rotate(new Quaternionf().rotationZYX(pose.zRot, pose.yRot, pose.xRot));
            bounds(child, next, others, min, max);
        }
    }

    /** A box the bone's size, in a patch of the skin (most skins keep the body's colour about there). */
    private static void box(Vector3f min, Vector3f max, PoseStack poseStack, VertexConsumer buffer, int light, int color) {
        PoseStack.Pose pose = poseStack.last();
        float x0 = min.x / 16.0F, y0 = min.y / 16.0F, z0 = min.z / 16.0F;
        float x1 = max.x / 16.0F, y1 = max.y / 16.0F, z1 = max.z / 16.0F;
        float u0 = 0.3F, u1 = 0.45F, v0 = 0.55F, v1 = 0.7F;
        float[][][] faces = {
                {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {0, 0, -1}},
                {{x1, y0, z1}, {x0, y0, z1}, {x0, y1, z1}, {x1, y1, z1}, {0, 0, 1}},
                {{x0, y0, z1}, {x0, y0, z0}, {x0, y1, z0}, {x0, y1, z1}, {-1, 0, 0}},
                {{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}, {1, 0, 0}},
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}, {0, -1, 0}},
                {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}, {0, 1, 0}}};
        float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (float[][] face : faces) {
            for (int i = 0; i < 4; i++) {
                buffer.addVertex(pose, face[i][0], face[i][1], face[i][2]).setColor(color).setUv(uv[i][0], uv[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, face[4][0], face[4][1], face[4][2]);
            }
        }
    }
}
