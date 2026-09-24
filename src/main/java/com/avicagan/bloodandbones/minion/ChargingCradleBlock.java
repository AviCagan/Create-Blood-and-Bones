package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Charging Cradle (docs/PARTS-AND-TRAITS.md section 6.7): brass minions' Blood Trough. Driven by a shaft from
 * below, it swaps a full Soul Canister into any brass minion beside it that is running low or has powered down, and
 * keeps the empty for re-filling at a Spout; stocked with brass sheets, it mends one docked there. Funnels, hoppers
 * and chutes fill and empty it; by hand, a canister or sheet goes in and an empty hand takes the empties out.
 */
public class ChargingCradleBlock extends KineticBlock implements IBE<ChargingCradleBlockEntity> {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 10, 16);

    public ChargingCradleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        return onBlockEntityUseItemOn(level, pos, cradle -> cradle.use(player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<ChargingCradleBlockEntity> getBlockEntityClass() {
        return ChargingCradleBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ChargingCradleBlockEntity> getBlockEntityType() {
        return BBBlockEntities.CHARGING_CRADLE.get();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
