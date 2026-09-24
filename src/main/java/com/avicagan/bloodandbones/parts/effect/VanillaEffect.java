package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.effects.AllOf;
import net.minecraft.world.item.enchantment.effects.DamageItem;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The vanilla adapter (docs/PARTS-AND-TRAITS.md section 5.4, row 16): any of vanilla's enchantment entity effects run as a
 * trait's effect, at the trait's level, as {@code effect.apply(level, traitLevel, new EnchantedItemInUse(piece, slot, host),
 * target, position)}. So all_of, apply_mob_effect, damage_entity, explode, ignite, play_sound, replace_block, replace_disk,
 * run_function, set_block_properties, spawn_particles and summon_entity work, and our seven actions (launch, pull, web,
 * bleed, steal_item, blink_target, ink_cloud; section 5.5). damage_item is refused where the trait is read (it would wear
 * the armour out), even inside all_of.
 * <p>
 * A passive entry runs every half second while its condition holds (a lava wader's crust under its feet).
 *
 * @param target "self": the host, where it stands. "other" (the default; "attacker", "victim" and "target" say the same):
 *               whoever else is in it, the attacker, the victim, a minion's target, or for a player's key the creature
 *               they look at within the entry's range; nothing happens if there is nobody. "look": where the host looks,
 *               the creature or block face within range (a minion's target, if it has one); on a block, the host stands in
 *               as the effect's entity, so this is for effects that act on a place (summon_entity, explode, particles)
 */
public record VanillaEffect(EnchantmentEntityEffect effect, String target) implements TraitEffect.Effect {
    private static final List<String> TARGETS = List.of("self", "other", "look", "attacker", "victim", "target");

    public static final MapCodec<VanillaEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EnchantmentEntityEffect.CODEC.validate(VanillaEffect::allowed).fieldOf("effect").forGetter(VanillaEffect::effect),
            Codec.STRING.validate(t -> TARGETS.contains(t) ? DataResult.success(t) : DataResult.error(() -> "Unknown target " + t + ", not one of " + TARGETS))
                    .optionalFieldOf("target", "other").forGetter(VanillaEffect::target)
    ).apply(i, VanillaEffect::new));

    /** damage_item wears out the item the effect runs on, here a piece of carcass armour: never allowed, nested or not. */
    public static DataResult<EnchantmentEntityEffect> allowed(EnchantmentEntityEffect effect) {
        return wearsItem(effect) ? DataResult.error(() -> "damage_item is not allowed in a trait: it would wear the armour itself out") : DataResult.success(effect);
    }

    private static boolean wearsItem(EnchantmentEntityEffect effect) {
        if (effect instanceof DamageItem) {
            return true;
        }
        if (effect instanceof AllOf.EntityEffects all) {
            for (EnchantmentEntityEffect inner : all.effects()) {
                if (wearsItem(inner)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    @Override
    public void run(TraitContext ctx) {
        LivingEntity host = ctx.host();
        Entity on;
        Vec3 at;
        switch (target) {
            case "self" -> {
                on = host;
                at = host.position();
            }
            case "look" -> {
                LivingEntity seen = ctx.other() != null ? ctx.other() : RangedAim.lookedAt(host, ctx.facet().range());
                on = seen != null ? seen : host;
                at = seen != null ? seen.position() : RangedAim.lookPoint(host, ctx.facet().range());
            }
            default -> {
                on = ctx.other() != null ? ctx.other() : ctx.trigger() == com.avicagan.bloodandbones.parts.Trigger.ACTIVATE
                        ? RangedAim.lookedAt(host, ctx.facet().range()) : null;
                if (on == null || on == host) {
                    return;
                }
                at = on.position();
            }
        }
        effect.apply(ctx.level(), ctx.traitLevel(), RangedAim.itemInUse(ctx), on, at);
    }

    @Override
    public void keepUp(TraitContext ctx) {
        run(ctx);
    }
}
