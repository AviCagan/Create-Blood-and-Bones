package com.avicagan.bloodandbones.cooking;

import com.avicagan.bloodandbones.carcass.Blood;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.registry.BBTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.List;

/** The piece on a butcher's table: held like a piece in a jar until a Cleaver takes it apart. */
public class ButcherTableBlockEntity extends SpecimenJarBlockEntity {
    public ButcherTableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * One chop: the piece comes apart into what butchering it gives, dropped on the table top, with the
     * wet sound and spray of a cut. The cleaver comes away bloody from a mob that bleeds.
     *
     * @return false when there is nothing on the table
     */
    public boolean chop(ServerLevel level, ItemStack cleaver) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(specimen());
        if (piece == null) {
            return false;
        }
        List<ItemStack> yields = CarcassButchery.pieceYields(level, piece);
        take();
        BlockPos pos = getBlockPos();
        Vector3d top = new Vector3d(pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5);
        for (ItemStack stack : yields) {
            ItemEntity item = new ItemEntity(level, top.x, top.y, top.z, stack);
            item.setDeltaMovement(level.random.triangle(0.0, 0.08), 0.15, level.random.triangle(0.0, 0.08));
            level.addFreshEntity(item);
        }
        level.playSound(null, top.x, top.y, top.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.BLOCKS, 0.9F, 0.7F);
        boolean bleeds = BuiltInRegistries.ENTITY_TYPE.getOptional(piece.entity()).map(type -> !type.is(BBTags.BLOODLESS)).orElse(true);
        if (bleeds) {
            Blood.burst(level, top, 10);
            Blood.bloody(cleaver, level);
        }
        return true;
    }
}
