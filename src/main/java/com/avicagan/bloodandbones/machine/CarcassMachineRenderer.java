package com.avicagan.bloodandbones.machine;

import com.avicagan.bloodandbones.client.BBPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The machines' moving parts, turning with the shaft (Create's own kinetic renderers are the pattern: a partial model per
 * part, turned by the time and the speed): the Mangler's grinders and the Deglover's rollers roll against each other, the
 * Beheader's saw spins, and the Guillotine's blade rises on its rope as it winds and falls when it drops. With them the
 * filter slot, and without Flywheel the half shaft under it.
 */
public class CarcassMachineRenderer extends KineticBlockEntityRenderer<CarcassMachineBlockEntity> {
    /** How much faster than the shaft each part turns. */
    private static final float SAW_GEARING = 4.0F;
    private static final float ROLLER_GEARING = 2.0F;
    private static final float GRINDER_GEARING = 1.5F;
    /** Pixels the Guillotine's blade falls, from the top of its frame to the top of the machine. */
    private static final float BLADE_TRAVEL = 8.0F;

    public CarcassMachineRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRenderOffScreen(CarcassMachineBlockEntity be) {
        return true;
    }

    @Override
    protected void renderSafe(CarcassMachineBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        FilteringRenderer.renderOnBlockEntity(be, partialTicks, ms, buffer, light, overlay);
        BlockState state = be.getBlockState();
        VertexConsumer cutout = buffer.getBuffer(RenderType.cutoutMipped());
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        // degrees the shaft has turned, as Create's own renderers work it out
        float turned = time * be.getSpeed() * 3.0F / 10.0F;
        switch (be.kind()) {
            case MANGLER -> {
                PartialModel grinder = BBPartialModels.pick(BBPartialModels.MANGLER_GRINDER, BBPartialModels.MANGLER_GRINDER_CLEAN);
                // two grinders turning into each other, 7 pixels apart, the gore in the pit showing between them
                spin(CachedBuffers.partial(grinder, state), turned * GRINDER_GEARING, 14.5F, 4.5F, 0.0F, light).renderInto(ms, cutout);
                spin(CachedBuffers.partial(grinder, state), -turned * GRINDER_GEARING, 14.5F, 4.5F, 7.0F, light).renderInto(ms, cutout);
            }
            case DEGLOVER -> {
                PartialModel roller = BBPartialModels.pick(BBPartialModels.DEGLOVER_ROLLER, BBPartialModels.DEGLOVER_ROLLER_CLEAN);
                spin(CachedBuffers.partial(roller, state), turned * ROLLER_GEARING, 18.0F, 4.5F, 0.0F, light).renderInto(ms, cutout);
                spin(CachedBuffers.partial(roller, state), -turned * ROLLER_GEARING, 18.0F, 4.5F, 7.0F, light).renderInto(ms, cutout);
            }
            case BEHEADER -> {
                PartialModel saw = BBPartialModels.pick(BBPartialModels.BEHEADER_SAW, BBPartialModels.BEHEADER_SAW_CLEAN);
                spin(CachedBuffers.partial(saw, state), turned * SAW_GEARING, 18.0F, 8.0F, 0.0F, light).renderInto(ms, cutout);
            }
            case GUILLOTINE -> {
                float drop = be.bladeDrop(partialTicks) * BLADE_TRAVEL / 16.0F;
                PartialModel blade = BBPartialModels.pick(BBPartialModels.GUILLOTINE_BLADE, BBPartialModels.GUILLOTINE_BLADE_CLEAN);
                CachedBuffers.partial(blade, state).translate(0.0F, -drop, 0.0F).light(light).renderInto(ms, cutout);
                // the rope from the weight on the blade up to the drum under the crossbar
                float low = 30.0F / 16.0F - drop;
                float high = 29.0F / 16.0F;
                if (high > low) {
                    CachedBuffers.partial(BBPartialModels.GUILLOTINE_ROPE, state).translate(0.0F, low, 0.0F)
                            .scale(1.0F, (high - low) * 16.0F, 1.0F).light(light).renderInto(ms, cutout);
                }
            }
        }
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
    }

    /** A part turned about a line along X at this height and depth (pixels), moved {@code shift} pixels along Z. */
    private static SuperByteBuffer spin(SuperByteBuffer part, float degrees, float y, float z, float shift, int light) {
        return part.translate(0.0F, 0.0F, shift / 16.0F)
                .translate(0.0F, y / 16.0F, z / 16.0F)
                .rotate((float) Math.toRadians(degrees % 360.0F), Direction.Axis.X)
                .translateBack(0.0F, y / 16.0F, z / 16.0F)
                .light(light);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(CarcassMachineBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, Direction.DOWN);
    }
}
