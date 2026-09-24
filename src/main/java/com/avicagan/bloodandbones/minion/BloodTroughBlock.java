package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * The Blood Trough (docs/PARTS-AND-TRAITS.md section 6.7): four buckets of blood, filled by pipes, a Spout or a
 * bucket, that an organic minion walks to and drinks from when its blood runs low. A minion that cannot walk
 * there does not drink: it has to be able to reach it.
 */
public class BloodTroughBlock extends HorizontalDirectionalBlock implements IBE<BloodTroughBlockEntity> {
    public static final com.mojang.serialization.MapCodec<BloodTroughBlock> CODEC = simpleCodec(BloodTroughBlock::new);
    private static final VoxelShape SHAPE = Shapes.join(Block.box(0, 0, 0, 16, 10, 16), Block.box(2, 2, 2, 14, 10, 14), net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);

    public BloodTroughBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** A bucket of blood in, or out into an empty bucket, as Create's tanks do. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof BloodTroughBlockEntity trough && FluidUtil.interactWithFluidHandler(player, hand, trough.tank())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public Class<BloodTroughBlockEntity> getBlockEntityClass() {
        return BloodTroughBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BloodTroughBlockEntity> getBlockEntityType() {
        return BBBlockEntities.BLOOD_TROUGH.get();
    }
}
