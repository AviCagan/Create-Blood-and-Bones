package com.avicagan.bloodandbones.decoration;

import com.mojang.serialization.MapCodec;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One segment of a giant rib. Stacked, ribs rise straight; the top of a stack bends in towards its facing;
 * and a rib hung in the air beside another along its facing runs level, as the top of the arch. Two stacks
 * facing each other with level ribs between them make an arch; a row of arches reads as the inside of a
 * ribcage. The shape follows the neighbours; only the facing is chosen, when placed.
 */
public class RibcageArchBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<RibcageArchBlock> CODEC = simpleCodec(RibcageArchBlock::new);
    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

    public enum Shape implements StringRepresentable {
        /** Another rib above: this one rises straight up into it. */
        STRAIGHT,
        /** The top of a stack, or a rib on its own: it bends in towards its facing. */
        CURVE,
        /** Over thin air, beside another rib along its facing: level, the crown of the arch. */
        TOP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    // drawn facing north: the rib stands a pixel back from the middle and bends forward, towards z = 0
    private static final VoxelShaper STRAIGHT_SHAPE = VoxelShaper.forHorizontal(Block.box(6, 0, 7, 10, 16, 11), Direction.NORTH);
    private static final VoxelShaper CURVE_SHAPE = VoxelShaper.forHorizontal(Shapes.or(
            Block.box(6, 0, 7, 10, 6, 11), Block.box(6, 5, 5, 10, 10, 10), Block.box(6, 9, 2, 10, 13, 7), Block.box(6, 11, 0, 10, 15, 3)), Direction.NORTH);
    private static final VoxelShaper TOP_SHAPE = VoxelShaper.forHorizontal(Block.box(6, 11, 0, 10, 15, 16), Direction.NORTH);

    public RibcageArchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHAPE, Shape.CURVE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE);
    }

    /**
     * Facing the one placing it, so a stack built from inside the arch bends towards them; placed against
     * another rib it takes that rib's facing instead, so stacks and spans stay in line.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos against = context.getClickedPos().relative(context.getClickedFace().getOpposite());
        BlockState other = context.getLevel().getBlockState(against);
        Direction facing = other.is(this) ? other.getValue(FACING) : context.getHorizontalDirection().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        return state.setValue(SHAPE, shapeFor(state, context.getLevel(), context.getClickedPos()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(SHAPE, shapeFor(state, level, pos));
    }

    /** What a rib with this facing looks like here, from the blocks around it. */
    public static Shape shapeFor(BlockState state, BlockGetter level, BlockPos pos) {
        if (level.getBlockState(pos.above()).getBlock() instanceof RibcageArchBlock) {
            return Shape.STRAIGHT;
        }
        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        if (below.getBlock() instanceof RibcageArchBlock || below.isFaceSturdy(level, belowPos, Direction.UP)) {
            return Shape.CURVE;
        }
        Direction facing = state.getValue(FACING);
        boolean spanned = level.getBlockState(pos.relative(facing)).getBlock() instanceof RibcageArchBlock
                || level.getBlockState(pos.relative(facing.getOpposite())).getBlock() instanceof RibcageArchBlock;
        return spanned ? Shape.TOP : Shape.CURVE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (state.getValue(SHAPE)) {
            case STRAIGHT -> STRAIGHT_SHAPE.get(facing);
            case CURVE -> CURVE_SHAPE.get(facing);
            case TOP -> TOP_SHAPE.get(facing);
        };
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
