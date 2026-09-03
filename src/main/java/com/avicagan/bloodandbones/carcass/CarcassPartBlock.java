package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.registry.BBBlockEntities;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * One cell of a carcass limb inside a Sable sub-level.
 * <p>
 * Sable bakes physics colliders per block state only, so the limb's box has to live in the state: the box
 * fills {@code [0, size]} pixels from the block's minimum corner on each axis. Limbs longer than 16 pixels are
 * split into several cells along that axis, each hugging the same corner, so together they form one box.
 */
public class CarcassPartBlock extends Block implements EntityBlock, BlockSubLevelCollisionShape {
    public static final IntegerProperty SIZE_X = IntegerProperty.create("size_x", 1, 16);
    public static final IntegerProperty SIZE_Y = IntegerProperty.create("size_y", 1, 16);
    public static final IntegerProperty SIZE_Z = IntegerProperty.create("size_z", 1, 16);

    private static final VoxelShape[] SHAPES = new VoxelShape[16 * 16 * 16];

    public CarcassPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SIZE_X, 16).setValue(SIZE_Y, 16).setValue(SIZE_Z, 16));
    }

    public static int sizeX(BlockState state) {
        return state.getValue(SIZE_X);
    }

    public static int sizeY(BlockState state) {
        return state.getValue(SIZE_Y);
    }

    public static int sizeZ(BlockState state) {
        return state.getValue(SIZE_Z);
    }

    public static BlockState stateFor(Block block, int sizeX, int sizeY, int sizeZ) {
        return block.defaultBlockState()
                .setValue(SIZE_X, clamp(sizeX))
                .setValue(SIZE_Y, clamp(sizeY))
                .setValue(SIZE_Z, clamp(sizeZ));
    }

    private static int clamp(int size) {
        return Math.max(1, Math.min(16, size));
    }

    public static VoxelShape shape(BlockState state) {
        int sx = state.getValue(SIZE_X);
        int sy = state.getValue(SIZE_Y);
        int sz = state.getValue(SIZE_Z);
        int index = (sx - 1) * 256 + (sy - 1) * 16 + (sz - 1);
        VoxelShape shape = SHAPES[index];
        if (shape == null) {
            shape = Block.box(0, 0, 0, sx, sy, sz);
            SHAPES[index] = shape;
        }
        return shape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIZE_X, SIZE_Y, SIZE_Z);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter blockGetter, BlockState state) {
        return shape(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CarcassPartBlockEntity(BBBlockEntities.CARCASS_PART.get(), pos, state);
    }

    /** A punch on a resting carcass wakes it: it unfolds and the struck limb gets a nudge. */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof CarcassPartBlockEntity be) || be.carcassId() == null) {
            return;
        }
        CarcassSavedData.Carcass carcass = CarcassSavedData.get(serverLevel).carcass(be.carcassId());
        if (carcass == null || !carcass.resting) {
            return;
        }
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        CarcassRest.disturb(serverLevel, carcass, be.bone(), new org.joml.Vector3d(look.x, look.y + 0.3, look.z), 1.2);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(BBItems.CLEAVER.get())) {
            if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof CarcassPartBlockEntity be && be.carcassId() != null) {
                CarcassSavedData.Carcass carcass = CarcassSavedData.get(serverLevel).carcass(be.carcassId());
                dev.ryanhcode.sable.sublevel.SubLevel subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, pos);
                org.joml.Vector3d hitWorld = null;
                if (subLevel != null) {
                    net.minecraft.world.phys.Vec3 hit = hitResult.getLocation();
                    hitWorld = subLevel.logicalPose().transformPosition(new org.joml.Vector3d(hit.x, hit.y, hit.z), new org.joml.Vector3d());
                }
                if (carcass != null && CarcassButchery.cut(serverLevel, player, carcass, be.bone(), hitWorld)) {
                    player.getCooldowns().addCooldown(stack.getItem(), 12);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!stack.is(BBItems.MEAT_HOOK.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel serverLevel) {
            CarcassDrag.toggle(serverLevel, player, pos, hitResult.getLocation());
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return false;
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return false;
    }
}
