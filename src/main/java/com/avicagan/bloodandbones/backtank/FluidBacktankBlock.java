package com.avicagan.bloodandbones.backtank;

import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** A Fluid Backtank set down on the ground, of any tier. Broken, it drops the backtank with its fluid. */
public class FluidBacktankBlock extends Block implements IBE<FluidBacktankBlockEntity> {
    public static final EnumProperty<BacktankTier> TIER = EnumProperty.create("tier", BacktankTier.class);
    private static final VoxelShape SHAPE = Block.box(3, 0, 4, 13, 14, 12);
    /** Broken by a player in creative: nothing drops. */
    private static final ThreadLocal<Boolean> CREATIVE = ThreadLocal.withInitial(() -> false);

    public FluidBacktankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TIER, BacktankTier.COPPER).setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIER, HorizontalDirectionalBlock.FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BacktankTier tier = context.getItemInHand().getItem() instanceof FluidBacktankItem item ? item.tier() : BacktankTier.COPPER;
        return defaultBlockState().setValue(TIER, tier).setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        withBlockEntityDo(level, pos, be -> be.setFluid(FluidBacktankItem.fluid(stack)));
    }

    /** The backtank this block is, with the fluid in it. */
    public static ItemStack asItem(BlockState state, @Nullable FluidBacktankBlockEntity be) {
        ItemStack stack = new ItemStack(com.avicagan.bloodandbones.registry.BBItems.backtank(state.getValue(TIER)));
        if (be != null) {
            FluidBacktankItem.setFluid(stack, be.tank().getFluid());
        }
        return stack;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return asItem(state, null);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        CREATIVE.set(player.isCreative());
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            if (!CREATIVE.get() && level.getBlockEntity(pos) instanceof FluidBacktankBlockEntity be) {
                Block.popResource(level, pos, asItem(state, be));
            }
            CREATIVE.set(false);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    public Class<FluidBacktankBlockEntity> getBlockEntityClass() {
        return FluidBacktankBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FluidBacktankBlockEntity> getBlockEntityType() {
        return BBBlockEntities.FLUID_BACKTANK.get();
    }
}
