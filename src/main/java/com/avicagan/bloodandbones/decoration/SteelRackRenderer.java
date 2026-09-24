package com.avicagan.bloodandbones.decoration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;

/** What stands on a steel rack's shelves, each thing facing out of its open front. */
public class SteelRackRenderer extends SafeBlockEntityRenderer<SteelRackBlockEntity> {
    /** How big each thing is drawn: a shelf is six pixels high. */
    private static final float SIZE = 0.36F;

    public SteelRackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(SteelRackBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        Direction facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        for (int slot = 0; slot < SteelRackBlockEntity.SLOTS; slot++) {
            ItemStack stack = be.item(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Vec3 at = SteelRackBlock.slotCentre(facing, slot);
            ms.pushPose();
            ms.translate(at.x, at.y, at.z);
            // as an item frame turns its item; a little askew, each place its own way
            ms.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot() + (Math.floorMod(be.getBlockPos().hashCode() + slot * 7, 3) - 1) * 12.0F));
            ShownItem.standing(stack, SIZE, be.getLevel(), ms, buffer, light);
            ms.popPose();
        }
    }
}
