package com.avicagan.bloodandbones.cooking;

import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.foundation.block.IBE;
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
import net.minecraft.world.phys.shapes.VoxelShape;

/** A glass jar of cloudy preserving fluid. Put a carcass piece in to keep it on show; empty hand takes it out. */
public class SpecimenJarBlock extends Block implements IBE<SpecimenJarBlockEntity> {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 15, 13);

    public SpecimenJarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty() && !stack.is(BBItems.CARCASS_PIECE.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        return onBlockEntityUseItemOn(level, pos, be -> {
            if (stack.isEmpty()) {
                if (be.specimen().isEmpty()) {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
                player.getInventory().placeItemBackInInventory(be.take());
                return ItemInteractionResult.SUCCESS;
            }
            if (be.put(stack.copyWithCount(1))) {
                stack.shrink(1);
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        });
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SpecimenJarBlockEntity jar && !jar.specimen().isEmpty()) {
            Block.popResource(level, pos, jar.take());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public Class<SpecimenJarBlockEntity> getBlockEntityClass() {
        return SpecimenJarBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SpecimenJarBlockEntity> getBlockEntityType() {
        return BBBlockEntities.SPECIMEN_JAR.get();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
