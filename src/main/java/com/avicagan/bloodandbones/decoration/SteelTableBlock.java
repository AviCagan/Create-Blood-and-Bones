package com.avicagan.bloodandbones.decoration;

import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * A steel morgue table. Tables side by side join into one run: one top, a raised lip only round the outside,
 * and legs only at the run's outer corners, where it ends or turns. Holds one item, any item, laid on the top.
 */
public class SteelTableBlock extends Block implements IBE<SteelTableBlockEntity> {
    public static final BooleanProperty NORTH = CrossCollisionBlock.NORTH;
    public static final BooleanProperty EAST = CrossCollisionBlock.EAST;
    public static final BooleanProperty SOUTH = CrossCollisionBlock.SOUTH;
    public static final BooleanProperty WEST = CrossCollisionBlock.WEST;
    /** Height of the top the item lies on, in pixels. */
    public static final float TOP = 15.0F;

    private static final VoxelShape PLATE = Block.box(0, 13, 0, 16, 15, 16);
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public SteelTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
        for (BlockState state : stateDefinition.getPossibleStates()) {
            shapes.put(state, makeShape(state));
        }
    }

    public static BooleanProperty side(Direction direction) {
        return PipeBlock.PROPERTY_BY_DIRECTION.get(direction);
    }

    /**
     * Whether a leg stands in the corner between two sides: only where neither side joins another table,
     * so a straight run has legs at its two ends and a turn has one on its outside corner.
     */
    public static boolean leg(BlockState state, Direction a, Direction b) {
        return !state.getValue(side(a)) && !state.getValue(side(b));
    }

    private static VoxelShape makeShape(BlockState state) {
        VoxelShape shape = PLATE;
        if (!state.getValue(NORTH)) {
            shape = Shapes.or(shape, Block.box(0, 15, 0, 16, 16, 1));
        }
        if (!state.getValue(SOUTH)) {
            shape = Shapes.or(shape, Block.box(0, 15, 15, 16, 16, 16));
        }
        if (!state.getValue(WEST)) {
            shape = Shapes.or(shape, Block.box(0, 15, 0, 1, 16, 16));
        }
        if (!state.getValue(EAST)) {
            shape = Shapes.or(shape, Block.box(15, 15, 0, 16, 16, 16));
        }
        if (leg(state, Direction.NORTH, Direction.WEST)) {
            shape = Shapes.or(shape, Block.box(1, 0, 1, 3, 13, 3));
        }
        if (leg(state, Direction.NORTH, Direction.EAST)) {
            shape = Shapes.or(shape, Block.box(13, 0, 1, 15, 13, 3));
        }
        if (leg(state, Direction.SOUTH, Direction.WEST)) {
            shape = Shapes.or(shape, Block.box(1, 0, 13, 3, 13, 15));
        }
        if (leg(state, Direction.SOUTH, Direction.EAST)) {
            shape = Shapes.or(shape, Block.box(13, 0, 13, 15, 13, 15));
        }
        return shape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            state = state.setValue(side(direction), joins(context.getLevel().getBlockState(context.getClickedPos().relative(direction))));
        }
        return state;
    }

    private boolean joins(BlockState neighbour) {
        return neighbour.is(this);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return direction.getAxis().isHorizontal() ? state.setValue(side(direction), joins(neighbour)) : state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
    }

    /** Turned on a contraption or by a structure block, the joins turn with it (as a fence's do). */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState out = state;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            out = out.setValue(side(rotation.rotate(direction)), state.getValue(side(direction)));
        }
        return out;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState out = state;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            out = out.setValue(side(mirror.mirror(direction)), state.getValue(side(direction)));
        }
        return out;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        SteelTableBlockEntity table = level.getBlockEntity(pos) instanceof SteelTableBlockEntity be ? be : null;
        if (table == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.isEmpty()) {
            if (table.specimen().isEmpty()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                player.getInventory().placeItemBackInInventory(table.take());
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // things are laid on the top; clicking a side places blocks as usual, so a run can be built out
        if (hit.getDirection() != Direction.UP || !table.specimen().isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && table.put(stack.copyWithCount(1))) {
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SteelTableBlockEntity table && !table.specimen().isEmpty()) {
            Block.popResource(level, pos, table.take());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public Class<SteelTableBlockEntity> getBlockEntityClass() {
        return SteelTableBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SteelTableBlockEntity> getBlockEntityType() {
        return BBBlockEntities.STEEL_TABLE.get();
    }
}
