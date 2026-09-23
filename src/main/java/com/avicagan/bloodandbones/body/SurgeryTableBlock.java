package com.avicagan.bloodandbones.body;

import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Surgery Table. Lay a blade, an implant or a severed limb on it; right-click it with an empty hand to
 * lie on it and choose what to do to which part; sneak and right-click with an empty hand to take back what
 * lies on it.
 */
public class SurgeryTableBlock extends Block implements IBE<SurgeryTableBlockEntity> {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 10, 0, 16, 15, 16),
            Block.box(1, 0, 1, 3, 10, 3), Block.box(13, 0, 1, 15, 10, 3), Block.box(1, 0, 13, 3, 10, 15), Block.box(13, 0, 13, 15, 10, 15));

    public SurgeryTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !Surgery.accepts(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof SurgeryTableBlockEntity table) || !table.item().isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && table.put(stack)) {
            stack.shrink(1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SurgeryTableBlockEntity table)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            if (table.item().isEmpty()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                player.getInventory().placeItemBackInInventory(table.take());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (lieDown((ServerLevel) level, pos, player) && player instanceof ServerPlayer server) {
            PacketDistributor.sendToPlayer(server, new Surgery.OpenPayload(pos));
        }
        return InteractionResult.CONSUME;
    }

    /** Put a patient on the table, unless someone else is on it. */
    public static boolean lieDown(ServerLevel level, BlockPos pos, net.minecraft.world.entity.Entity patient) {
        SurgerySeatEntity seat = level.getEntitiesOfClass(SurgerySeatEntity.class, new AABB(pos)).stream().findFirst().orElse(null);
        if (seat != null && seat.isVehicle() && !seat.hasPassenger(patient)) {
            if (patient instanceof Player player) {
                player.displayClientMessage(Component.translatable("bloodandbones.surgery.occupied"), true);
            }
            return false;
        }
        if (seat == null) {
            seat = SurgerySeatEntity.at(com.avicagan.bloodandbones.registry.BBEntities.SURGERY_SEAT.get(), level, pos);
            level.addFreshEntity(seat);
        }
        return patient.getVehicle() == seat || patient.startRiding(seat, true);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SurgeryTableBlockEntity table && !table.item().isEmpty()) {
            Block.popResource(level, pos, table.take());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public Class<SurgeryTableBlockEntity> getBlockEntityClass() {
        return SurgeryTableBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SurgeryTableBlockEntity> getBlockEntityType() {
        return BBBlockEntities.SURGERY_TABLE.get();
    }
}
