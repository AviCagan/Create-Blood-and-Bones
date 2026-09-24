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

/**
 * A worn Fluid Backtank drawn on the wearer's back as its block, as Create draws its own backtank; one strapped
 * to a carcass chestplate sits on the chestplate's back.
 */
public class FluidBacktankLayer<T extends net.minecraft.world.entity.LivingEntity, M extends net.minecraft.client.model.HumanoidModel<T>> extends RenderLayer<T, M> {
    public FluidBacktankLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ms, MultiBufferSource buffers, int light, T player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack worn = FluidBacktankItem.wornBy(player);
        com.avicagan.bloodandbones.backtank.BacktankTier tier = FluidBacktankItem.tier(worn);
        if (tier == null || player.getPose() == Pose.SLEEPING || player.isInvisible()) {
            return;
        }
        BlockState state = BBBlocks.FLUID_BACKTANK.getDefaultState()
                .setValue(FluidBacktankBlock.TIER, tier)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        // strapped to a chestplate, it sits a pixel further out, on the armour (drawn a pixel proud of the body)
        float out = worn.getItem() instanceof FluidBacktankItem ? 0.0F : 1.0F / 16.0F;
        ms.pushPose();
        getParentModel().body.translateAndRotate(ms);
        // the model's y points down and its z out of the back; the straps (block z 12 to 13) lie on the back
        ms.translate(-0.5F, 12.0F / 16.0F, 15.0F / 16.0F + out);
        ms.scale(1.0F, -1.0F, -1.0F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, ms, buffers, light, OverlayTexture.NO_OVERLAY,
                net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
        ms.popPose();
    }
}
