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
 * The Surgery Table. Lay a blade, an implant or a part on it; right-click it with an empty hand to lie on it
 * and choose what to do to which part of you; sneak and right-click with an empty hand to take back what lies
 * on it. With someone else on it, an empty hand opens the same screen for them. A mob on a lead is laid on it
 * by right-clicking with an empty hand while leading it. A carcass piece laid on it gives up its organs to a
 * Cleaver, one a cut.
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
        if (stack.isEmpty() || !Surgery.accepts(stack) || !(level.getBlockEntity(pos) instanceof SurgeryTableBlockEntity table)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (Surgery.isBlade(stack) && table.item().is(com.avicagan.bloodandbones.registry.BBItems.CARCASS_PIECE.get())) {
            if (!level.isClientSide) {
                Surgery.harvest((ServerLevel) level, player, table, stack);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!table.item().isEmpty()) {
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
        ServerLevel server = (ServerLevel) level;
        net.minecraft.world.entity.LivingEntity patient = Surgery.patientAt(level, pos);
        if (patient != null && patient != player) {
            // someone else on the table: work on them
            if (player instanceof ServerPlayer surgeon && Surgery.mayOperate(player, patient, pos)) {
                PacketDistributor.sendToPlayer(surgeon, new Surgery.OpenPayload(pos, patient.getId()));
            }
            return InteractionResult.CONSUME;
        }
        net.minecraft.world.entity.Mob led = ledBy(server, player);
        if (patient == null && led != null) {
            led.dropLeash(true, !player.hasInfiniteMaterials());
            lieDown(server, pos, led);
            return InteractionResult.CONSUME;
        }
        if (lieDown(server, pos, player) && player instanceof ServerPlayer self) {
            PacketDistributor.sendToPlayer(self, new Surgery.OpenPayload(pos, player.getId()));
        }
        return InteractionResult.CONSUME;
    }

    /** A mob this player is leading on a lead, near enough to lay on the table. */
    @org.jetbrains.annotations.Nullable
    private static net.minecraft.world.entity.Mob ledBy(ServerLevel level, Player player) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(10.0),
                mob -> mob.getLeashHolder() == player).stream().findFirst().orElse(null);
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
