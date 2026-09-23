package com.avicagan.bloodandbones.bleeding;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Blood on the ground: a flat splash on top of a solid block. More blood landing on it makes it bigger and
 * fresh again. Left alone it dries darker, then shrinks away; rain or running water washes it off. Nothing
 * collides with it and anything placed there replaces it.
 */
public class BloodStainBlock extends Block {
    public static final MapCodec<BloodStainBlock> CODEC = simpleCodec(BloodStainBlock::new);
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 1, 4);
    /** 0 wet, 1 drying, 2 dried */
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 2);
    /** Soul Blood, from a nether mob: dark teal instead of red. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty SOUL =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("soul");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 0.25, 16);
    /** Chance a random tick moves the stain on a step (dry, shrink, gone): about three minutes a step. */
    private static final int STEP_CHANCE = 3;

    public BloodStainBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SIZE, 1).setValue(AGE, 0).setValue(SOUL, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIZE, AGE, SOUL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.isRainingAt(pos.above()) || level.isRainingAt(pos)) {
            level.removeBlock(pos, false);
            return;
        }
        if (random.nextInt(STEP_CHANCE) != 0) {
            return;
        }
        if (state.getValue(AGE) < 2) {
            level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), Block.UPDATE_CLIENTS);
        } else if (state.getValue(SIZE) > 1) {
            level.setBlock(pos, state.setValue(SIZE, state.getValue(SIZE) - 1), Block.UPDATE_CLIENTS);
        } else {
            level.removeBlock(pos, false);
        }
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type != PathComputationType.WATER;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /**
     * Blood landing at a spot: a new stain, or a bigger, wet again one where there is already a stain.
     *
     * @param amount 1 (a few drops) to 4 (a pool)
     * @return false when the spot cannot take a stain
     */
    public static boolean splash(ServerLevel level, BlockPos pos, BlockState stain, int amount) {
        BlockState here = level.getBlockState(pos);
        if (here.is(stain.getBlock())) {
            int size = Math.min(4, Math.max(here.getValue(SIZE), here.getValue(SIZE) + amount / 2 + (level.random.nextBoolean() ? 1 : 0)));
            BlockState grown = here.setValue(SIZE, size).setValue(AGE, 0);
            if (grown != here) {
                level.setBlock(pos, grown, Block.UPDATE_CLIENTS);
            }
            return true;
        }
        if (!here.isAir() || !stain.canSurvive(level, pos)) {
            return false;
        }
        level.setBlock(pos, stain.setValue(SIZE, Math.max(1, Math.min(4, amount))).setValue(AGE, 0), Block.UPDATE_ALL);
        return true;
    }
}
