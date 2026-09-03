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
 * Client odds and ends for dragging: the chain a Shackle Hook hangs a carcass by, and no block outline
 * on meat. The Meat Hook itself is drawn by the limb it is stuck in (CarcassPartRenderer), and the pull
 * has no visible tether.
 */
@EventBusSubscriber(modid = BloodAndBones.MOD_ID, value = Dist.CLIENT)
public class DragRenderer {
    private static final int LINKS_PER_BLOCK = 4;

    /** Chain between two world points, in a pose stack already at world origin. */
    public static void drawChainSegment(PoseStack poseStack, MultiBufferSource buffers, Vec3 from, Vec3 to) {
        drawChain(poseStack, buffers, null, from, to);
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
     * A punch that hit nothing: check whether it went through a limb drawn on a resting carcass (those
     * limbs have no cells of their own) and tell the server.
     */
    @SubscribeEvent
    public static void onLeftClickEmpty(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickEmpty event) {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        Player player = event.getEntity();
        if (level == null || SubLevelContainer.getContainer(level) == null) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        double best = 6.0;
        java.util.UUID hit = null;
        for (SubLevel subLevel : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (subLevel.isRemoved() || !(subLevel instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel client)) {
                continue;
            }
            for (var holder : subLevel.getPlot().getLoadedChunks()) {
                for (var entry : holder.getChunk().getBlockEntities().entrySet()) {
                    if (!(entry.getValue() instanceof com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity be) || !be.isRoot() || be.merged().isEmpty() || be.carcassId() == null) {
                        continue;
                    }
                    com.avicagan.bloodandbones.carcass.rig.Rig rig = com.avicagan.bloodandbones.carcass.rig.RigManager.clientRig(be.entity()).orElse(null);
                    com.avicagan.bloodandbones.carcass.rig.Bone torsoBone = rig == null ? null : rig.bone(be.bone()).orElse(null);
                    if (torsoBone == null) {
                        continue;
                    }
                    Pose3dc pose = client.renderPose();
                    org.joml.Vector3d torsoOrigin = new org.joml.Vector3d(entry.getKey().getX(), entry.getKey().getY(), entry.getKey().getZ())
                            .sub(torsoBone.boxMin().x / 16.0, torsoBone.boxMin().y / 16.0, torsoBone.boxMin().z / 16.0);
                    for (com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity.MergedPart part : be.merged()) {
                        com.avicagan.bloodandbones.carcass.rig.Bone bone = rig.bone(part.bone()).orElse(null);
                        if (bone == null) {
                            continue;
                        }
                        // a sphere around the limb's box is plenty for a punch
                        org.joml.Vector3d centre = new org.joml.Vector3d(bone.boxMin()).add(bone.boxMax()).mul(0.5 / 16.0);
                        Vector3f turned = part.orientation().transform(new Vector3f((float) centre.x, (float) centre.y, (float) centre.z));
                        centre.set(turned.x, turned.y, turned.z).add(part.position()).add(torsoOrigin);
                        org.joml.Vector3d world = pose.transformPosition(centre, new org.joml.Vector3d());
                        double radius = new org.joml.Vector3d(bone.boxMax()).sub(new org.joml.Vector3d(bone.boxMin())).length() / 32.0 + 0.1;
                        double t = raySphere(eye, dir, world, radius);
                        if (t >= 0 && t < best) {
                            best = t;
                            hit = be.carcassId();
                        }
                    }
                }
            }
        }
        if (hit != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.avicagan.bloodandbones.network.PunchCarcassPayload(hit));
        }
    }

    private static double raySphere(Vec3 origin, Vec3 dir, org.joml.Vector3d centre, double radius) {
        double ox = origin.x - centre.x;
        double oy = origin.y - centre.y;
        double oz = origin.z - centre.z;
        double b = 2.0 * (ox * dir.x + oy * dir.y + oz * dir.z);
        double c = ox * ox + oy * oy + oz * oz - radius * radius;
        double disc = b * b - 4.0 * c;
        if (disc < 0) {
            return -1;
        }
        double t = (-b - Math.sqrt(disc)) / 2.0;
        return t >= 0 ? t : -1;
    }

    /** A carcass is meat, not a block: no cube outline when you look at it. */
    @SubscribeEvent
    public static void onBlockHighlight(net.neoforged.neoforge.client.event.RenderHighlightEvent.Block event) {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null && level.getBlockState(event.getTarget().getBlockPos()).getBlock() instanceof com.avicagan.bloodandbones.carcass.CarcassPartBlock) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDragState.clear();
    }
}
