package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.config.BBServerConfig;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * web {seconds} (docs/PARTS-AND-TRAITS.md section 5.5): spins a temporary web where it lands (round a creature's feet), if
 * that space is empty or only grass and the like (or holds a web of ours with less time left), never in water. It wears
 * away after its seconds (at most 15), leaving air. A player's web always goes; any other creature's honours mobGriefing
 * as vanilla's Weaving does, and a minion's goes over grass and the like (which it takes away with it) only where the
 * server's {@code minion_block_damage} allows it to break blocks too.
 */
public record WebAction(LevelBasedValue seconds) implements EnchantmentEntityEffect {
    public static final MapCodec<WebAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.fieldOf("seconds").forGetter(WebAction::seconds)
    ).apply(i, WebAction::new));

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        BlockPos pos = BlockPos.containing(origin);
        BlockState here = level.getBlockState(pos);
        int life = Math.round(seconds.calculate(enchantmentLevel));
        // an older web of ours is spun again, if this one lasts longer
        boolean respun = here.is(RangedContent.TEMPORARY_WEB.get()) && here.getValue(TemporaryWebBlock.LIFE) < Math.min(life, TemporaryWebBlock.MAX_LIFE);
        if (life <= 0 || !level.isInWorldBounds(pos) || !here.getFluidState().isEmpty() || !(respun || here.isAir() || here.canBeReplaced())) {
            return;
        }
        Entity owner = item.owner();
        if (owner != null && !(owner instanceof Player) && (!EventHooks.canEntityGrief(level, owner)
                || owner instanceof MinionEntity && !respun && !here.isAir() && !BBServerConfig.minionBlockDamage())) {
            return;
        }
        level.setBlockAndUpdate(pos, RangedContent.TEMPORARY_WEB.get().lasting(life));
        // a sticky splat of silk
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                12, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 0.8F, 1.3F);
        level.playSound(null, pos, SoundEvents.SPIDER_AMBIENT, SoundSource.BLOCKS, 0.3F, 1.8F);
    }

    @Override
    public MapCodec<WebAction> codec() {
        return CODEC;
    }
}
