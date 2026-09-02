package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A hook hanging from a ceiling. Drag a carcass up to it and right-click the hook with the Meat Hook to
 * hang the hooked limb from it; right-click again to let it down. The carcass stays a ragdoll while hanging.
 */
public class ShackleHookBlock extends BaseEntityBlock {
    /** The block face this hook hangs from: UP means it hangs from the ceiling above. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final VoxelShape CEILING = Block.box(6, 4, 6, 10, 16, 10);
    private static final VoxelShape FLOOR = Block.box(6, 0, 6, 10, 12, 10);
    private static final VoxelShape WALL_NS = Block.box(6, 4, 0, 10, 14, 16);
    private static final VoxelShape WALL_EW = Block.box(0, 4, 6, 16, 14, 10);

    public static final com.mojang.serialization.MapCodec<ShackleHookBlock> CODEC = simpleCodec(ShackleHookBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public ShackleHookBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // FACING points at the block this hook is mounted on
        Direction mount = context.getClickedFace().getOpposite();
        return defaultBlockState().setValue(FACING, mount);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction mount = state.getValue(FACING);
        BlockPos support = pos.relative(mount);
        return level.getBlockState(support).isFaceSturdy(level, support, mount.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING) && !state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP -> CEILING;
            case DOWN -> FLOOR;
            case NORTH, SOUTH -> WALL_NS;
            default -> WALL_EW;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Where the carcass hangs from, in world space. */
    public static Vec3 tip(BlockPos pos, BlockState state) {
        return switch (state.getValue(FACING)) {
            case UP -> Vec3.atLowerCornerOf(pos).add(0.5, 0.25, 0.5);
            case DOWN -> Vec3.atLowerCornerOf(pos).add(0.5, 0.75, 0.5);
            default -> Vec3.atLowerCornerOf(pos).add(0.5, 0.3, 0.5);
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShackleHookBlockEntity(BBBlockEntities.SHACKLE_HOOK.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, BBBlockEntities.SHACKLE_HOOK.get(), ShackleHookBlockEntity::tick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(BBItems.MEAT_HOOK.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof ShackleHookBlockEntity hook) {
            hook.toggle(serverLevel, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof ShackleHookBlockEntity hook && hook.isOccupied()) {
            hook.release(serverLevel);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof ShackleHookBlockEntity hook) {
            hook.release(serverLevel);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
