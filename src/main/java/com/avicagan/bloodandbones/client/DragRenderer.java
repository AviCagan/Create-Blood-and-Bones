package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.registry.BBItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;

/**
 * Draws the Meat Hook stuck in the hooked limb and a chain from it to the dragging player's hand.
 */
@EventBusSubscriber(modid = BloodAndBones.MOD_ID, value = Dist.CLIENT)
public class DragRenderer {
    private static final int LINKS_PER_BLOCK = 4;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Map<UUID, ClientDragState.Drag> drags = ClientDragState.all();
        if (drags.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        for (Map.Entry<UUID, ClientDragState.Drag> entry : drags.entrySet()) {
            Player player = level.getPlayerByUUID(entry.getKey());
            if (player == null) {
                continue;
            }
            SubLevel subLevel = SubLevelContainer.getContainer(level) == null ? null : SubLevelContainer.getContainer(level).getSubLevel(entry.getValue().subLevel());
            if (subLevel == null || subLevel.isRemoved()) {
                continue;
            }
            Pose3dc pose = ((LevelPoseProviderExtension) level).sable$getPose(subLevel);
            Vector3d hook = pose.transformPosition(entry.getValue().anchor(), new Vector3d());
            Vec3 hand = handPosition(player, partialTick);

            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            drawChain(poseStack, buffers, level, hand, new Vec3(hook.x, hook.y, hook.z));
            drawHook(poseStack, buffers, level, pose, hook, hand);
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    /** Roughly where the player's main hand is. */
    private static Vec3 handPosition(Player player, float partialTick) {
        double x = net.minecraft.util.Mth.lerp(partialTick, player.xo, player.getX());
        double y = net.minecraft.util.Mth.lerp(partialTick, player.yo, player.getY());
        double z = net.minecraft.util.Mth.lerp(partialTick, player.zo, player.getZ());
        float yaw = (float) Math.toRadians(net.minecraft.util.Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot));
        // right hand: 0.35 to the body's right, a little forward, at hip height
        double side = 0.35;
        double forward = 0.15;
        return new Vec3(
                x - Math.cos(yaw) * side - Math.sin(yaw) * forward,
                y + 0.9,
                z - Math.sin(yaw) * side + Math.cos(yaw) * forward);
    }

    private static void drawChain(PoseStack poseStack, MultiBufferSource buffers, ClientLevel level, Vec3 from, Vec3 to) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();
        Vec3 delta = to.subtract(from);
        int segments = Math.max(2, (int) (delta.length() * LINKS_PER_BLOCK));
        double sag = Math.min(0.35, delta.length() * 0.12);
        Vec3 previous = from;
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            // a slight droop like a hanging chain
            Vec3 point = from.add(delta.scale(t)).subtract(0.0, Math.sin(t * Math.PI) * sag, 0.0);
            addLine(lines, matrix, poseStack.last(), previous, point, i % 2 == 0 ? 0.35F : 0.5F);
            previous = point;
        }
    }

    private static void addLine(VertexConsumer lines, Matrix4f matrix, PoseStack.Pose pose, Vec3 a, Vec3 b, float shade) {
        Vec3 dir = b.subtract(a).normalize();
        Vector3f normal = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        lines.addVertex(matrix, (float) a.x, (float) a.y, (float) a.z).setColor(shade, shade, shade, 1.0F).setNormal(pose, normal.x, normal.y, normal.z);
        lines.addVertex(matrix, (float) b.x, (float) b.y, (float) b.z).setColor(shade, shade, shade, 1.0F).setNormal(pose, normal.x, normal.y, normal.z);
    }

    /**
     * The Meat Hook model buried in the limb. The model's shank runs up the +Y axis with the bend at
     * model height 1/16 and the eye at the top; rotate +Y onto the chain direction so the eye faces the
     * player's hand and the point sits inside the flesh.
     */
    private static void drawHook(PoseStack poseStack, MultiBufferSource buffers, ClientLevel level, Pose3dc limbPose, Vector3d hook, Vec3 hand) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 toHand = hand.subtract(hook.x, hook.y, hook.z);
        if (toHand.lengthSqr() < 1.0e-4) {
            return;
        }
        Vector3f dir = new Vector3f((float) toHand.x, (float) toHand.y, (float) toHand.z).normalize();
        poseStack.pushPose();
        poseStack.translate(hook.x, hook.y, hook.z);
        // rotate model +Y onto the chain direction
        poseStack.mulPose(new org.joml.Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), dir));
        float scale = 0.55F;
        poseStack.scale(scale, scale, scale);
        // FIXED display puts the model's (8,8,8) at the origin; lift it so the bend (y=1px) is on the anchor
        // and the point (y about 6px, 4px back) is inside the limb
        poseStack.translate(0.0, 7.0 / 16.0, 0.0);
        BlockPos at = BlockPos.containing(hook.x, hook.y, hook.z);
        int light = LightTexture.pack(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at), level.getBrightness(net.minecraft.world.level.LightLayer.SKY, at));
        minecraft.getItemRenderer().renderStatic(new ItemStack(BBItems.MEAT_HOOK.get()), ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, poseStack, buffers, level, 0);
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDragState.clear();
    }
}
