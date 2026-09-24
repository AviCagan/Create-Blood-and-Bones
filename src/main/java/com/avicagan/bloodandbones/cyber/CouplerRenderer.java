package com.avicagan.bloodandbones.cyber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The coupler drawn: the half shaft going into the machine (without Flywheel; the visual draws it with), and
 * always a brass rod reaching from there back to the owner's hand, turning with the machine, sliding out
 * over its first few ticks.
 */
public class CouplerRenderer extends KineticBlockEntityRenderer<CouplerBlockEntity> {
    /** Ticks for the shaft to reach out full length. */
    private static final float EXTEND_TICKS = 6.0F;

    public CouplerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(CouplerBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, state.getValue(CouplerBlock.FACING));
    }

    @Override
    protected void renderSafe(CouplerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        if (be.getLevel() == null || !(be.getLevel().getEntity(be.ownerId()) instanceof LivingEntity owner)) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(be.getBlockPos());
        Vec3 hand = com.avicagan.bloodandbones.client.CyberClient.rodStart(owner, partialTicks);
        Vec3 span = hand.subtract(center);
        double length = span.length() * Math.min(1.0F, (be.age + partialTicks) / EXTEND_TICKS);
        if (length < 0.05) {
            return;
        }
        Vector3f dir = span.normalize().toVector3f();
        float angle = getAngleForBe(be, be.getBlockPos(), be.getBlockState().getValue(CouplerBlock.FACING).getAxis());
        BlockState rod = shaft(Direction.Axis.Y);
        SuperByteBuffer model = CachedBuffers.block(KINETIC_BLOCK, rod);
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir));
        ms.mulPose(Axis.YP.rotation(angle));
        // the shaft model is a block tall and centred: stretch it along the reach, a little thinner
        ms.scale(0.55F, (float) length, 0.55F);
        ms.translate(-0.5, 0.0, -0.5);
        model.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
        ms.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CouplerBlockEntity be) {
        return true;
    }
}
