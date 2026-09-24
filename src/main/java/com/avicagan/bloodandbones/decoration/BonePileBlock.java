package com.avicagan.bloodandbones.decoration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A heap of bones in layers, as snow lies: use a Bone Pile on one to add a layer, up to a full block. Each
 * layer drops two bones. You sink into it as into snow, so a thin scatter can be walked through.
 */
public class BonePileBlock extends Block {
    public static final int MAX_LAYERS = 8;
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    /** Bones each layer drops: the two a layer is made of. */
    public static final int BONES_PER_LAYER = 2;
    private static final VoxelShape[] SHAPES = new VoxelShape[MAX_LAYERS + 1];

    static {
        for (int layers = 0; layers <= MAX_LAYERS; layers++) {
            SHAPES[layers] = Block.box(0, 0, 0, 16, layers * 2, 16);
        }
    }

    public BonePileBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    /** A layer lower than it looks, as snow: feet sink into the top of the heap. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS) - 1];
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.LAND && state.getValue(LAYERS) < 5;
    }

    /** On anything with a solid top, or on a full heap. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(this)) {
            return below.getValue(LAYERS) == MAX_LAYERS;
        }
        return Block.isFaceFull(below.getCollisionShape(level, pos.below()), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    /** Another Bone Pile used on the top of a heap (or its top layer) adds to it, as snow does. */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        int layers = state.getValue(LAYERS);
        if (!context.getItemInHand().is(asItem()) || layers >= MAX_LAYERS) {
            return false;
        }
        return !context.replacingClickedOnBlock() || context.getClickedFace() == Direction.UP;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState here = context.getLevel().getBlockState(context.getClickedPos());
        if (here.is(this)) {
            return here.setValue(LAYERS, Math.min(MAX_LAYERS, here.getValue(LAYERS) + 1));
        }
        return defaultBlockState();
    }
}
