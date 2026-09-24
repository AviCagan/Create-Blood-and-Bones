package com.avicagan.bloodandbones.decoration;

import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A steel rack of two shelves, two places on each, for showing parts. Right-click on its front puts the held
 * item on the place you are looking at; an empty hand takes it back. Facing is the open front, towards
 * whoever placed it.
 */
public class SteelRackBlock extends HorizontalDirectionalBlock implements IBE<SteelRackBlockEntity> {
    public static final MapCodec<SteelRackBlock> CODEC = simpleCodec(SteelRackBlock::new);
    /** Heights of the two shelves' tops, in blocks. */
    public static final float LOWER_SHELF = 2 / 16.0F;
    public static final float UPPER_SHELF = 9 / 16.0F;
    /** How far each place is from the middle, sideways. */
    private static final double SPREAD = 0.24;

    public SteelRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Which place a point in the block (0 to 1 on each axis) is nearest: 0 and 1 the lower shelf, 2 and 3 the
     * upper; the even one on the left as you face the rack's front.
     */
    public static int slotAt(Direction facing, Vec3 local) {
        Direction right = facing.getCounterClockWise();
        double across = right.getStepX() * (local.x - 0.5) + right.getStepZ() * (local.z - 0.5);
        return (local.y >= 0.5 ? 2 : 0) + (across >= 0.0 ? 1 : 0);
    }

    /** Where an item on a place stands: the middle of its spot on the shelf's top, in the block. */
    public static Vec3 slotCentre(Direction facing, int slot) {
        Direction right = facing.getCounterClockWise();
        double across = slot % 2 == 0 ? -SPREAD : SPREAD;
        return new Vec3(0.5 + right.getStepX() * across, slot < 2 ? LOWER_SHELF : UPPER_SHELF, 0.5 + right.getStepZ() * across);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SteelRackBlockEntity rack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        int slot = slotAt(state.getValue(FACING), hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)));
        if (stack.isEmpty()) {
            if (rack.item(slot).isEmpty()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                player.getInventory().placeItemBackInInventory(rack.take(slot));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // things go on from the open front; clicking a side places a held block as usual, so racks can stand in a row
        if (!rack.item(slot).isEmpty() || hit.getDirection() != state.getValue(FACING)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && rack.put(slot, stack.copyWithCount(1))) {
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SteelRackBlockEntity rack) {
            for (int slot = 0; slot < SteelRackBlockEntity.SLOTS; slot++) {
                if (!rack.item(slot).isEmpty()) {
                    Block.popResource(level, pos, rack.take(slot));
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Open shelves: light and sky come through, and it casts no dark shadow on its neighbours. */
    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public Class<SteelRackBlockEntity> getBlockEntityClass() {
        return SteelRackBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SteelRackBlockEntity> getBlockEntityType() {
        return BBBlockEntities.STEEL_RACK.get();
    }
}
