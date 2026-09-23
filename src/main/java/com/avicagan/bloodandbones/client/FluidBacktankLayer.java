package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.backtank.FluidBacktankBlock;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A worn Fluid Backtank drawn on the wearer's back as its block, as Create draws its own backtank. */
public class FluidBacktankLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public FluidBacktankLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ms, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack worn = FluidBacktankItem.wornBy(player);
        if (worn.isEmpty() || player.getPose() == Pose.SLEEPING || player.isInvisible()) {
            return;
        }
        BlockState state = BBBlocks.FLUID_BACKTANK.getDefaultState()
                .setValue(FluidBacktankBlock.TIER, ((FluidBacktankItem) worn.getItem()).tier())
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        ms.pushPose();
        getParentModel().body.translateAndRotate(ms);
        // the model's y points down and its z out of the back; the straps (block z 12 to 13) lie on the back
        ms.translate(-0.5F, 12.0F / 16.0F, 15.0F / 16.0F);
        ms.scale(1.0F, -1.0F, -1.0F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, ms, buffers, light, OverlayTexture.NO_OVERLAY,
                net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
        ms.popPose();
    }
}
