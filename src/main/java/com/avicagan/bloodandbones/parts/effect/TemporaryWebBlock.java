package com.avicagan.bloodandbones.parts.effect;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;

/**
 * A web spun by the web action (docs/PARTS-AND-TRAITS.md section 5.5): it holds what walks into it as a cobweb does, and
 * wears away on its own as frosted ice does, a second at a time, until it tears. An ordinary block, so it rides
 * contraptions and goes on wearing away once set down again.
 */
public class TemporaryWebBlock extends Block {
    public static final MapCodec<TemporaryWebBlock> CODEC = simpleCodec(TemporaryWebBlock::new);
    /** Seconds left before it tears. */
    public static final IntegerProperty LIFE = IntegerProperty.create("life", 1, 15);
    public static final int MAX_LIFE = 15;

    public TemporaryWebBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIFE, 3));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIFE);
    }

    /** A web lasting this many seconds (1 to 15). */
    public BlockState lasting(int seconds) {
        return defaultBlockState().setValue(LIFE, Math.max(1, Math.min(MAX_LIFE, seconds)));
    }

    /** Held fast as in a cobweb (less so with Weaving). */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        Vec3 slowed = entity instanceof LivingEntity living && living.hasEffect(MobEffects.WEAVING) ? new Vec3(0.5, 0.25, 0.5) : new Vec3(0.25, 0.05, 0.25);
        entity.makeStuckInBlock(state, slowed);
    }

    /** Every change (spun, a second worn away, set down off a contraption) waits a second for the next. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 20);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int life = state.getValue(LIFE);
        if (life > 1) {
            level.setBlock(pos, state.setValue(LIFE, life - 1), Block.UPDATE_CLIENTS);
        } else {
            // it tears, with the cobweb's own snap and strands
            level.destroyBlock(pos, false);
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }
}
