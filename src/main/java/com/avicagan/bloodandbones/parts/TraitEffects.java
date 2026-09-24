package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.BloodAndBones;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

import java.util.List;

/**
 * The effect types traits are made of (docs/PARTS-AND-TRAITS.md section 5.4), in their own registry so
 * other mods can add more in code; data packs only compose them. What each does at run time is in
 * {@link ActiveTraits} and {@link TraitEvents}.
 */
public final class TraitEffects {
    public static final ResourceKey<Registry<MapCodec<? extends TraitEffect.Effect>>> KEY =
            ResourceKey.createRegistryKey(BloodAndBones.asResource("trait_effect_type"));
    public static final Registry<MapCodec<? extends TraitEffect.Effect>> REGISTRY = new RegistryBuilder<>(KEY).create();
    public static final Codec<TraitEffect.Effect> CODEC = REGISTRY.byNameCodec().dispatch("type", TraitEffect.Effect::codec, c -> c);

    private static final DeferredRegister<MapCodec<? extends TraitEffect.Effect>> TYPES = DeferredRegister.create(REGISTRY, BloodAndBones.MOD_ID);

    /** A change to one of the host's attributes, any vanilla or modded one. */
    public record AttributeEffect(Holder<Attribute> attribute, LevelBasedValue amount, AttributeModifier.Operation operation) implements TraitEffect.Effect {
        public static final MapCodec<AttributeEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Attribute.CODEC.fieldOf("attribute").forGetter(AttributeEffect::attribute),
                LevelBasedValue.CODEC.fieldOf("amount").forGetter(AttributeEffect::amount),
                AttributeModifier.Operation.CODEC.optionalFieldOf("operation", AttributeModifier.Operation.ADD_VALUE).forGetter(AttributeEffect::operation)
        ).apply(i, AttributeEffect::new));

        @Override
        public MapCodec<? extends TraitEffect.Effect> codec() {
            return CODEC;
        }
    }

    /**
     * A potion effect: on the host itself, on whoever hurt it, on what it hit, or on everything around it.
     * Passive ones are kept up quietly for as long as the trait applies.
     */
    public record MobEffectEffect(Holder<MobEffect> effect, LevelBasedValue amplifier, int duration, String target, float radius) implements TraitEffect.Effect {
        public static final MapCodec<MobEffectEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                MobEffect.CODEC.fieldOf("effect").forGetter(MobEffectEffect::effect),
                LevelBasedValue.CODEC.optionalFieldOf("amplifier", LevelBasedValue.constant(0)).forGetter(MobEffectEffect::amplifier),
                Codec.INT.optionalFieldOf("duration", 60).forGetter(MobEffectEffect::duration),
                Codec.STRING.optionalFieldOf("target", "self").forGetter(MobEffectEffect::target),
                Codec.FLOAT.optionalFieldOf("radius", 4.0F).forGetter(MobEffectEffect::radius)
        ).apply(i, MobEffectEffect::new));

        @Override
        public MapCodec<? extends TraitEffect.Effect> codec() {
            return CODEC;
        }
    }

    /**
     * Damage taken ("in") or dealt ("out") made larger or smaller, for damage of these types (all if none
     * are named). Reductions from traits multiply together and never go below a fifth.
     */
    public record DamageEffect(String direction, LevelBasedValue multiplier, List<TagKey<DamageType>> damageTags) implements TraitEffect.Effect {
        public static final MapCodec<DamageEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("direction", "in").forGetter(DamageEffect::direction),
                LevelBasedValue.CODEC.fieldOf("multiplier").forGetter(DamageEffect::multiplier),
                TagKey.codec(Registries.DAMAGE_TYPE).listOf().optionalFieldOf("damage_tags", List.of()).forGetter(DamageEffect::damageTags)
        ).apply(i, DamageEffect::new));

        @Override
        public MapCodec<? extends TraitEffect.Effect> codec() {
            return CODEC;
        }
    }

    /** Never given these effects, never hurt by damage of these types, or breathing under water. */
    public record ImmunityEffect(List<Holder<MobEffect>> mobEffects, List<TagKey<DamageType>> damageTags, boolean breathe) implements TraitEffect.Effect {
        public static final MapCodec<ImmunityEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                MobEffect.CODEC.listOf().optionalFieldOf("mob_effects", List.of()).forGetter(ImmunityEffect::mobEffects),
                TagKey.codec(Registries.DAMAGE_TYPE).listOf().optionalFieldOf("damage_tags", List.of()).forGetter(ImmunityEffect::damageTags),
                Codec.BOOL.optionalFieldOf("breathe", false).forGetter(ImmunityEffect::breathe)
        ).apply(i, ImmunityEffect::new));

        @Override
        public MapCodec<? extends TraitEffect.Effect> codec() {
            return CODEC;
        }
    }

    /**
     * What the host can eat, and to what end. So far "graze": crouch and use grass bare-handed to eat it
     * (it turns to dirt) for this much hunger.
     */
    public record DietEffect(String effect, int hunger) implements TraitEffect.Effect {
        public static final MapCodec<DietEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("effect").forGetter(DietEffect::effect),
                Codec.INT.optionalFieldOf("hunger", 1).forGetter(DietEffect::hunger)
        ).apply(i, DietEffect::new));

        @Override
        public MapCodec<? extends TraitEffect.Effect> codec() {
            return CODEC;
        }
    }

    /** How mobs of a kind react to the host: they hunt it, or they flee it. */
    public record ReactionEffect(TagKey<EntityType<?>> entities, String mode, float radius) implements TraitEffect.Effect {
        public static final MapCodec<ReactionEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                TagKey.codec(Registries.ENTITY_TYPE).fieldOf("entities").forGetter(ReactionEffect::entities),
                Codec.STRING.optionalFieldOf("mode", "hunt").forGetter(ReactionEffect::mode),
                Codec.FLOAT.optionalFieldOf("radius", 16.0F).forGetter(ReactionEffect::radius)
        ).apply(i, ReactionEffect::new));

        @Override
        public MapCodec<? extends TraitEffect.Effect> codec() {
            return CODEC;
        }
    }

    static {
        TYPES.register("attribute", () -> AttributeEffect.CODEC);
        TYPES.register("mob_effect", () -> MobEffectEffect.CODEC);
        TYPES.register("damage", () -> DamageEffect.CODEC);
        TYPES.register("immunity", () -> ImmunityEffect.CODEC);
        TYPES.register("diet", () -> DietEffect.CODEC);
        TYPES.register("reaction", () -> ReactionEffect.CODEC);
    }

    private TraitEffects() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((NewRegistryEvent event) -> event.register(REGISTRY));
        TYPES.register(modBus);
    }
}
