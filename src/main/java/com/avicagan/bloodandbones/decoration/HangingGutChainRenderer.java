package com.avicagan.bloodandbones.decoration;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.config.BBClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Client: the hanging string drawn like the placed Gut Chain, two crossed strips of its texture four pixels
 * wide, bending at each joint; plain cord in bloodless mode, as the block is.
 */
public class HangingGutChainRenderer extends EntityRenderer<HangingGutChainEntity> {
    private static final ResourceLocation GUTS = BloodAndBones.asResource("block/gut_chain");
    private static final ResourceLocation CORD = BloodAndBones.asResource("block/gut_chain_clean");
    /** Strip width, in blocks: four pixels, as the block model. */
    private static final float WIDTH = 4 / 16.0F;

    public HangingGutChainRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(HangingGutChainEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
        Vec3[] joints = entity.drawnJoints(partialTick);
        if (joints == null) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(BBClientConfig.bloodless() ? CORD : GUTS);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        Vec3 origin = entity.getPosition(partialTick);
        int[] lights = new int[joints.length];
        for (int i = 0; i < joints.length; i++) {
            lights[i] = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(joints[i]));
        }
        // each joint's two sideways directions, from the string's direction through it, so strips meet up
        Vec3[] across = new Vec3[joints.length];
        Vec3[] crossing = new Vec3[joints.length];
        for (int i = 0; i < joints.length; i++) {
            Vec3 along = joints[Math.min(i + 1, joints.length - 1)].subtract(joints[Math.max(i - 1, 0)]);
            along = along.lengthSqr() < 1.0e-8 ? new Vec3(0, -1, 0) : along.normalize();
            // diagonal to the world, as the block model's strips are turned 45 degrees
            Vec3 side = along.cross(new Vec3(1, 0, -1));
            if (side.lengthSqr() < 1.0e-6) {
                side = along.cross(new Vec3(0, 0, 1));
            }
            across[i] = side.normalize().scale(WIDTH / 2.0F);
            crossing[i] = along.cross(across[i].normalize()).normalize().scale(WIDTH / 2.0F);
        }
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i + 1 < joints.length; i++) {
            Vec3 a = joints[i].subtract(origin);
            Vec3 b = joints[i + 1].subtract(origin);
            // one link is the texture's full height; half a link, half of it
            float v0 = (i % HangingGutChainEntity.JOINTS_PER_LINK) * 16.0F / HangingGutChainEntity.JOINTS_PER_LINK;
            float v1 = v0 + 16.0F / HangingGutChainEntity.JOINTS_PER_LINK;
            strip(buffer, pose, sprite, a, b, across[i], across[i + 1], 0, 4, v0, v1, lights[i], lights[i + 1]);
            strip(buffer, pose, sprite, a, b, crossing[i], crossing[i + 1], 4, 8, v0, v1, lights[i], lights[i + 1]);
        }
    }

    /** A strip from a to b, as wide as twice the side vectors, textured from the sprite's pixel box. */
    private static void strip(VertexConsumer buffer, PoseStack.Pose pose, TextureAtlasSprite sprite, Vec3 a, Vec3 b, Vec3 sideA, Vec3 sideB,
                              float u0, float u1, float v0, float v1, int lightA, int lightB) {
        Vec3 normal = sideA.cross(b.subtract(a));
        normal = normal.lengthSqr() < 1.0e-8 ? new Vec3(0, 0, 1) : normal.normalize();
        vertex(buffer, pose, a.subtract(sideA), sprite.getU(u0 / 16.0F), sprite.getV(v0 / 16.0F), lightA, normal);
        vertex(buffer, pose, a.add(sideA), sprite.getU(u1 / 16.0F), sprite.getV(v0 / 16.0F), lightA, normal);
        vertex(buffer, pose, b.add(sideB), sprite.getU(u1 / 16.0F), sprite.getV(v1 / 16.0F), lightB, normal);
        vertex(buffer, pose, b.subtract(sideB), sprite.getU(u0 / 16.0F), sprite.getV(v1 / 16.0F), lightB, normal);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 at, float u, float v, int light, Vec3 normal) {
        buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(-1).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    /** Culled by where the string swings, not its straight-down box: on a fast chain it trails well behind its top. */
    @Override
    public boolean shouldRender(HangingGutChainEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.cullingBox());
    }

    @Override
    public ResourceLocation getTextureLocation(HangingGutChainEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
