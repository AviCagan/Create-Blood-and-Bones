package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;


/** A carried piece of carcass looks like the piece: the actual limb, in the look the animal wore. */
public class CarcassPieceItemRenderer extends BlockEntityWithoutLevelRenderer {
    public CarcassPieceItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
        Rig rig = piece == null ? null : RigManager.clientRig(piece.entity(), piece.baby()).orElse(null);
        Bone bone = rig == null ? null : rig.bone(piece.bone()).orElse(null);
        if (bone == null) {
            // a piece with nothing in it (the creative tab): the plain icon
            BakedModel icon = Minecraft.getInstance().getModelManager().getModel(BBClientModels.CARCASS_PIECE_ICON);
            Minecraft.getInstance().getItemRenderer().renderModelLists(icon, stack, light, overlay, poseStack, buffers.getBuffer(Sheets.cutoutBlockSheet()));
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        CarcassModels.drawPiece(piece, rig, bone, 0.9F, -1, poseStack, buffers, light);
        poseStack.popPose();
    }
}
