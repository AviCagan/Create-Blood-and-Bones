package com.avicagan.bloodandbones.cooking;

import com.avicagan.bloodandbones.item.CleaverItem;
import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A steel butcher's table. Lay a carried carcass piece on it; chop it with a Cleaver and it comes apart
 * into what butchering it gives; an empty hand takes it back.
 */
public class ButcherTableBlock extends Block implements IBE<ButcherTableBlockEntity> {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 12, 0, 16, 16, 16),
            Block.box(1, 0, 1, 3, 12, 3), Block.box(13, 0, 1, 15, 12, 3), Block.box(1, 0, 13, 3, 12, 15), Block.box(13, 0, 13, 15, 12, 15));

    public ButcherTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        boolean cleaver = stack.getItem() instanceof CleaverItem;
        if (!stack.isEmpty() && !cleaver && !stack.is(BBItems.CARCASS_PIECE.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        boolean empty = !(level.getBlockEntity(pos) instanceof ButcherTableBlockEntity table) || table.specimen().isEmpty();
        if ((stack.isEmpty() || cleaver) && empty) {
            // nothing on the table to take or chop: let the other hand have its turn
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        return onBlockEntityUseItemOn(level, pos, be -> {
            if (cleaver) {
                if (player.getCooldowns().isOnCooldown(stack.getItem()) || !be.chop((ServerLevel) level, stack)) {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
                player.getCooldowns().addCooldown(stack.getItem(), 12);
                return ItemInteractionResult.SUCCESS;
            }
            if (stack.isEmpty()) {
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
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ButcherTableBlockEntity table && !table.specimen().isEmpty()) {
            Block.popResource(level, pos, table.take());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public Class<ButcherTableBlockEntity> getBlockEntityClass() {
        return ButcherTableBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ButcherTableBlockEntity> getBlockEntityType() {
        return BBBlockEntities.BUTCHER_TABLE.get();
    }
}
