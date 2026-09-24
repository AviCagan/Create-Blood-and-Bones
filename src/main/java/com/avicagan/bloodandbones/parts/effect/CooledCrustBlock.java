package com.avicagan.bloodandbones.parts.effect;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Lava crusted over, as a lava wader's feet leave it (vanilla's replace_disk; docs/PARTS-AND-TRAITS.md section 5.7): frosted
 * ice for lava. It ages a step every second or two, glowing through its cracks from the third, and melts back to lava; a
 * crust left with fewer than two others beside it melts at once, and one broken melts too. An ordinary block, so it rides
 * contraptions and goes on ageing once set down again.
 */
public class CooledCrustBlock extends Block {
    public static final MapCodec<CooledCrustBlock> CODEC = simpleCodec(CooledCrustBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final int MAX_AGE = 3;

    public CooledCrustBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    /** Fresh crust holds two or three seconds before it starts to go; after that a step every second or two. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, state.getValue(AGE) == 0 ? Mth.nextInt(level.getRandom(), 40, 60) : Mth.nextInt(level.getRandom(), 20, 40));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age < MAX_AGE) {
            level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
        } else {
            melt(level, pos);
        }
    }

    /** A crust next door melted: this one goes too if it is left with fewer than two beside it. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && neighborBlock.defaultBlockState().is(this) && fewerNeighboursThan(level, pos, 2)) {
            melt(level, pos);
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    /** Broken, it gives way to the lava under it, as ice gives way to water. */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
    }

    /** Back to lava, with a pop and a spit of embers. */
    public static void melt(Level level, BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
        level.neighborChanged(pos, Blocks.LAVA, pos);
        level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.5F, 0.7F + level.getRandom().nextFloat() * 0.3F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.3, 0.0, 0.3, 0.0);
        }
    }

    private boolean fewerNeighboursThan(BlockGetter level, BlockPos pos, int needed) {
        int found = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            at.setWithOffset(pos, direction);
            if (level.getBlockState(at).is(this) && ++found >= needed) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }
}
