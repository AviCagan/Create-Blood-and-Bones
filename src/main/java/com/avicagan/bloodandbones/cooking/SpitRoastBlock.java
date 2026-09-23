package com.avicagan.bloodandbones.cooking;

import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Two posts and a spit, set over a fire. A shaft through the spit turns it; a carcass piece skewered on
 * it roasts while it turns and there is heat below. Right-click with a piece to skewer it, with an empty
 * hand to take it off, cooked or not.
 */
public class SpitRoastBlock extends HorizontalAxisKineticBlock implements IBE<SpitRoastBlockEntity> {
    private static final VoxelShape X = Shapes.or(Block.box(0, 0, 6, 2, 10, 10), Block.box(14, 0, 6, 16, 10, 10), Block.box(0, 7, 7, 16, 9, 9));
    private static final VoxelShape Z = Shapes.or(Block.box(6, 0, 0, 10, 10, 2), Block.box(6, 0, 14, 10, 10, 16), Block.box(7, 7, 0, 9, 9, 16));

    public SpitRoastBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HORIZONTAL_AXIS) == Direction.Axis.X ? X : Z;
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
                be.takeOff(player);
                return ItemInteractionResult.SUCCESS;
            }
            if (be.skewer(stack.copyWithCount(1))) {
                stack.shrink(1);
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        });
    }

    @Override
    public Class<SpitRoastBlockEntity> getBlockEntityClass() {
        return SpitRoastBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SpitRoastBlockEntity> getBlockEntityType() {
        return BBBlockEntities.SPIT_ROAST.get();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
