package com.avicagan.bloodandbones.parts.effect;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

/**
 * blink_target {range} (docs/PARTS-AND-TRAITS.md section 5.5): the creature blinks somewhere random within range, as an
 * enderman does (sixteen tries at a safe spot), through NeoForge's ender teleport event so other mods can stop it. Bosses
 * stay where they are.
 */
public record BlinkTargetAction(LevelBasedValue range) implements EnchantmentEntityEffect {
    public static final MapCodec<BlinkTargetAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.fieldOf("range").forGetter(BlinkTargetAction::range)
    ).apply(i, BlinkTargetAction::new));

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.getType().is(Tags.EntityTypes.BOSSES)) {
            return;
        }
        double reach = Math.max(1.0, range.calculate(enchantmentLevel));
        Vec3 from = living.position();
        for (int i = 0; i < 16; i++) {
            double x = living.getX() + (living.getRandom().nextDouble() - 0.5) * 2.0 * reach;
            double y = living.getY() + (living.getRandom().nextInt((int) reach + 1) - (int) (reach / 2.0));
            double z = living.getZ() + (living.getRandom().nextDouble() - 0.5) * 2.0 * reach;
            EntityTeleportEvent.EnderEntity event = EventHooks.onEnderTeleport(living, x, y, z);
            if (event.isCanceled()) {
                return;
            }
            if (living.isPassenger()) {
                living.stopRiding();
            }
            if (living.randomTeleport(event.getTargetX(), event.getTargetY(), event.getTargetZ(), true)) {
                level.gameEvent(GameEvent.TELEPORT, from, GameEvent.Context.of(living));
                level.playSound(null, from.x, from.y, from.z, SoundEvents.CHORUS_FRUIT_TELEPORT, living.getSoundSource(), 1.0F, 0.6F);
                level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.SLIME_SQUISH, living.getSoundSource(), 0.6F, 0.5F);
                living.resetFallDistance();
                return;
            }
        }
    }

    @Override
    public MapCodec<BlinkTargetAction> codec() {
        return CODEC;
    }
}
