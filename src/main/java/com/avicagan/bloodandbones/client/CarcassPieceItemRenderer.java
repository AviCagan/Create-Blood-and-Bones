package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.carcass.CarcassLook;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import java.util.List;

/** A carried piece of carcass looks like the piece: the actual limb, in the look the animal wore. */
public class CarcassPieceItemRenderer extends BlockEntityWithoutLevelRenderer {
    public CarcassPieceItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
        Rig rig = piece == null ? null : RigManager.clientRig(piece.entity()).orElse(null);
        Bone bone = rig == null ? null : rig.bone(piece.bone()).orElse(null);
        if (bone == null) {
            // a piece with nothing in it (the creative tab): the plain icon
            BakedModel icon = Minecraft.getInstance().getModelManager().getModel(BBClientModels.CARCASS_PIECE_ICON);
            Minecraft.getInstance().getItemRenderer().renderModelLists(icon, stack, light, overlay, poseStack, buffers.getBuffer(Sheets.cutoutBlockSheet()));
            return;
        }
        Vector3f min = bone.boxMin();
        Vector3f max = bone.boxMax();
        float largest = Math.max(max.x - min.x, Math.max(max.y - min.y, max.z - min.z)) / 16.0F;
        float fit = 0.9F / Math.max(largest, 0.3F);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(fit, fit, fit);
        // entity models are drawn upside down in their own space; turn the piece the right way up
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.translate(-(min.x + max.x) / 32.0F, -(min.y + max.y) / 32.0F, -(min.z + max.z) / 32.0F);
        List<CarcassLook.Coat> coats = piece.coats().stream().map(c -> new CarcassLook.Coat(c.layer(), c.texture(), c.tint())).toList();
        CarcassModels.drawBone(rig, bone, piece.texture(), coats, CarcassModels.rotColor(piece.freshness()), poseStack, buffers, light);
        poseStack.popPose();
    }
}
