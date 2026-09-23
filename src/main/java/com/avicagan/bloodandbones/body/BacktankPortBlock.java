package com.avicagan.bloodandbones.body;

import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import com.mojang.serialization.MapCodec;

/**
 * A port for a worn backtank, like a pump with a direction: pipes connect to its nozzle (the side it
 * faces), and a player with a Port Arm stands next to it.
 */
public class BacktankPortBlock extends DirectionalBlock implements IBE<BacktankPortBlockEntity> {
    public static final MapCodec<BacktankPortBlock> CODEC = simpleCodec(BacktankPortBlock::new);

    public BacktankPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public Class<BacktankPortBlockEntity> getBlockEntityClass() {
        return BacktankPortBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BacktankPortBlockEntity> getBlockEntityType() {
        return BBBlockEntities.BACKTANK_PORT.get();
    }
}
