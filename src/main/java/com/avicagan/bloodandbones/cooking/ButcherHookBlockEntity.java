package com.avicagan.bloodandbones.cooking;

import com.avicagan.bloodandbones.carcass.Blood;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBParticles;
import com.avicagan.bloodandbones.registry.BBTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/**
 * The piece on a butcher's hook: held just like a piece in a Specimen Jar, except that a fresh one still
 * has blood in it, and drips it on the floor below until it has run dry (about two minutes).
 */
public class ButcherHookBlockEntity extends SpecimenJarBlockEntity {
    /** A carcass piece only. */
    @Override
    protected boolean accepts(net.minecraft.world.item.ItemStack stack) {
        return com.avicagan.bloodandbones.item.CarcassPieceItem.piece(stack) != null;
    }

    /** Seconds a freshly cut piece takes to drip dry. */
    public static final int DRIP_SECONDS = 120;

    public ButcherHookBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Where the hung piece's lower end is: out from the wall under the hook's tip (see ButcherHookRenderer). */
    private Vector3d dripPoint() {
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        BlockPos pos = getBlockPos();
        return new Vector3d(pos.getX() + 0.5 + facing.getStepX() * 0.28, pos.getY() + 0.05, pos.getZ() + 0.5 + facing.getStepZ() * 0.28);
    }

    /** Whether the piece on the hook still has blood to drip. */
    public boolean dripping() {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(specimen());
        return piece != null && piece.blood() > 0.0F && piece.bloodMax() > 0.0F
                && BuiltInRegistries.ENTITY_TYPE.getOptional(piece.entity()).map(type -> !type.is(BBTags.BLOODLESS)).orElse(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || !dripping()) {
            return;
        }
        Vector3d at = dripPoint();
        if (level.isClientSide) {
            if (level.random.nextFloat() < 0.15F) {
                level.addParticle((Blood.soul(CarcassPieceItem.piece(specimen()).entity()) ? BBParticles.SOUL_BLOOD_DROP : BBParticles.BLOOD_DROP).get(), at.x + (level.random.nextDouble() - 0.5) * 0.15, at.y,
                        at.z + (level.random.nextDouble() - 0.5) * 0.15, 0.0, -0.05, 0.0);
            }
            return;
        }
        long time = level.getGameTime();
        if (time % 20 != 0) {
            return;
        }
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(specimen());
        float left = Math.max(0.0F, piece.blood() - piece.bloodMax() / DRIP_SECONDS);
        specimen().set(BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(piece.entity(), piece.bone(), piece.texture(), piece.coats(),
                piece.freshness(), piece.skinned(), piece.traits(), left, piece.bloodMax(), piece.decay(), piece.baby()));
        if (time % 60 == 0) {
            // from under the hook's own block, which the drops fall out of
            Blood.stain((ServerLevel) level, new Vector3d(at.x, at.y - 0.5, at.z), 1, Blood.soul(piece.entity()));
        }
        setChanged();
        if (left <= 0.0F) {
            // clients only need to know when it stops: until then they drip on what they were sent
            sendData();
        }
    }
}
