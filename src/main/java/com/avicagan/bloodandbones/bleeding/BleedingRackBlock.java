package com.avicagan.bloodandbones.bleeding;

import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.blockEntity.ComparatorUtil;
import com.simibubi.create.foundation.fluid.FluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A half-height tray that catches what drips from a carcass hung above it. Pipes pull the fluid out of its sides
 * and bottom; a bucket or bottle takes it out by hand. The fluid itself is drawn by BleedingRackRenderer.
 */
public class BleedingRackBlock extends Block implements IBE<BleedingRackBlockEntity>, IWrenchable {
    /** Floor 2 px thick, walls 1 px thick up to 8 px. The fluid fills the 14x14 inside from y = 2 to 8 px. */
    public static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(0, 2, 0, 16, 8, 1),
            Block.box(0, 2, 15, 16, 8, 16),
            Block.box(0, 2, 1, 1, 8, 15),
            Block.box(15, 2, 1, 16, 8, 15));

    public BleedingRackBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return onBlockEntityUseItemOn(level, pos, be -> FluidHelper.tryFillItemFromBE(level, player, hand, stack, be)
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return ComparatorUtil.levelOfSmartFluidTank(level, pos);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public Class<BleedingRackBlockEntity> getBlockEntityClass() {
        return BleedingRackBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BleedingRackBlockEntity> getBlockEntityType() {
        return BBBlockEntities.BLEEDING_RACK.get();
    }
}
