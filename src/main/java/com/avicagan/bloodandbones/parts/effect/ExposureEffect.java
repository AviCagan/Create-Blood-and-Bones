package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * What its surroundings do to a body that cannot stand them, the drawbacks' harm (docs/PARTS-AND-TRAITS.md section 5.10:
 * sun_cursed, water_hurts, heat_hurts): {@code damage} of {@code damage_type} and {@code ignite} seconds alight, each
 * time a tick entry comes up. Where and when is the entry's condition (open sky by day, rain or water, a hot biome).
 * A player's helmet does not keep the sun off (the set's own is part of what curses it); a minion's does, as a zombie's
 * does, wearing a little each time (section 8.1: "it burns by day unless it wears a helmet"). Brass never suffers: sheathed,
 * a brass minion never suffers its flesh parts' weaknesses (section 6.6, flesh's alone). The spec reaches these through the vanilla adapter's ignite and
 * damage_entity (the Ranged group's); this small type lets the drawbacks work without it.
 */
public record ExposureEffect(LevelBasedValue damage, ResourceKey<DamageType> damageType, float ignite) implements TraitEffect.Effect {
    public static final MapCodec<ExposureEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.optionalFieldOf("damage", LevelBasedValue.constant(0.0F)).forGetter(ExposureEffect::damage),
            ResourceKey.codec(Registries.DAMAGE_TYPE).optionalFieldOf("damage_type", DamageTypes.GENERIC).forGetter(ExposureEffect::damageType),
            Codec.FLOAT.optionalFieldOf("ignite", 0.0F).forGetter(ExposureEffect::ignite)
    ).apply(i, ExposureEffect::new));

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    /** A minion's helmet takes the sun for it, and wears for it as a zombie's does; false with nothing on its head. */
    private static boolean shaded(MinionEntity minion) {
        net.minecraft.world.item.ItemStack helmet = minion.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if (helmet.isEmpty()) {
            return false;
        }
        if (helmet.isDamageableItem()) {
            helmet.setDamageValue(helmet.getDamageValue() + minion.getRandom().nextInt(2));
            if (helmet.getDamageValue() >= helmet.getMaxDamage()) {
                minion.onEquippedItemBroken(helmet.getItem(), net.minecraft.world.entity.EquipmentSlot.HEAD);
                minion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
        return true;
    }

    @Override
    public void run(TraitContext ctx) {
        LivingEntity host = ctx.host();
        if (!host.isAlive() || host instanceof MinionEntity minion && minion.cybernetic()) {
            return;
        }
        if (ignite > 0.0F && !(host instanceof MinionEntity minion && shaded(minion))) {
            host.igniteForSeconds(ignite);
        }
        float hurt = ctx.scaled(damage);
        if (hurt > 0.0F) {
            host.hurt(host.damageSources().source(damageType), hurt);
        }
    }
}
