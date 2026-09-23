package com.avicagan.bloodandbones.machine;

import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
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
 * The Mangler, Guillotine, Beheader and Deglover: a machine on legs, driven by a shaft from below (the
 * Millstone pattern), that works the carcass parts lying on or hanging over it. Empty-handed use takes
 * what it has made.
 */
public class CarcassMachineBlock extends KineticBlock implements IBE<CarcassMachineBlockEntity> {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public final MachineKind kind;

    public CarcassMachineBlock(Properties properties, MachineKind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        withBlockEntityDo(level, pos, be -> be.giveContentsTo(player));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public Class<CarcassMachineBlockEntity> getBlockEntityClass() {
        return CarcassMachineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CarcassMachineBlockEntity> getBlockEntityType() {
        return BBBlockEntities.CARCASS_MACHINE.get();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
