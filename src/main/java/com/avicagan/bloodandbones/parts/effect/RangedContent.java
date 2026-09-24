package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.BloodAndBones;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * What the Ranged group adds to the game besides its effect types: the Bleeding mob effect and its drips, the two
 * blocks its effects leave behind (a temporary web, a lava crust), and our seven actions in vanilla's registry of
 * enchantment entity effects (docs/PARTS-AND-TRAITS.md sections 5.5 and 5.7), so datapack enchantments can use them too.
 * Loaded from {@link RangedEffects#registerContent}.
 */
public final class RangedContent {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BloodAndBones.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, BloodAndBones.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends EnchantmentEntityEffect>> ACTIONS =
            DeferredRegister.create(Registries.ENCHANTMENT_ENTITY_EFFECT_TYPE, BloodAndBones.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, BloodAndBones.MOD_ID);

    /**
     * On a projectile one of our effects fired: marks it ours (kept through a save), holding the damage it does in place of
     * its own, or -1 to leave its own.
     */
    public static final Supplier<AttachmentType<Float>> SHOT = ATTACHMENTS.register("trait_shot",
            () -> AttachmentType.builder(() -> -1.0F).serialize(Codec.FLOAT).build());

    /** Bleeding: half a heart every two seconds, dripping (bloodless mode: Leaking, with grey sparks). */
    public static final DeferredHolder<MobEffect, BleedingMobEffect> BLEEDING = MOB_EFFECTS.register("bleeding", BleedingMobEffect::new);
    /** The damage Bleeding does ("bled out"). Its file is data/bloodandbones/damage_type/bleeding.json. */
    public static final ResourceKey<DamageType> BLEEDING_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, BloodAndBones.asResource("bleeding"));

    /** A drop running off a bleeding creature; a client in bloodless mode shows a grey spark instead. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLEEDING_DRIP = PARTICLES.register("bleeding_drip", () -> new SimpleParticleType(false));
    /** A grey spark: what leaks out of something with no blood, and what a drip is in bloodless mode. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LEAK_SPARK = PARTICLES.register("leak_spark", () -> new SimpleParticleType(false));

    /** A web the web action spins: sticky as a cobweb, gone again after its seconds are up. No item; nothing drops. */
    public static final BlockEntry<TemporaryWebBlock> TEMPORARY_WEB = BloodAndBones.REGISTRATE
            .block("temporary_web", TemporaryWebBlock::new)
            .properties(p -> p.mapColor(MapColor.WOOL)
                    .sound(SoundType.COBWEB)
                    .forceSolidOn()
                    .noCollission()
                    .noOcclusion()
                    .strength(1.0F)
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY)
                    .isValidSpawn((state, level, pos, type) -> false))
            .blockstate((c, p) -> {
                var web = p.models().withExistingParent("temporary_web", p.mcLoc("block/cross")).texture("cross", p.mcLoc("block/cobweb")).renderType("cutout");
                p.getVariantBuilder(c.get()).forAllStates(state -> ConfiguredModel.builder().modelFile(web).build());
            })
            .loot(NonNullBiConsumer.noop())
            // Create moves a block with no collision only if tagged so, as it does cobwebs (rule 5: it rides contraptions)
            .tag(BlockTags.SWORD_EFFICIENT, com.simibubi.create.AllTags.AllBlockTags.MOVABLE_EMPTY_COLLIDER.tag)
            .lang("Temporary Web")
            .register();

    /**
     * Lava crusted over under a lava wader's feet: firm to walk on, it ages as frosted ice does, glowing hotter, and melts
     * back to lava. No item; nothing drops.
     */
    public static final BlockEntry<CooledCrustBlock> COOLED_CRUST = BloodAndBones.REGISTRATE
            .block("cooled_crust", CooledCrustBlock::new)
            .properties(p -> p.mapColor(MapColor.NETHER)
                    .sound(SoundType.BASALT)
                    .strength(0.5F)
                    .noLootTable()
                    .lightLevel(state -> state.getValue(CooledCrustBlock.AGE) >= 2 ? 3 : 0)
                    .emissiveRendering((state, level, pos) -> state.getValue(CooledCrustBlock.AGE) >= 2)
                    .isValidSpawn((state, level, pos, type) -> false))
            .blockstate((c, p) -> {
                // dark while fresh, glowing through its cracks as it gets ready to melt
                var fresh = p.models().cubeAll("cooled_crust", p.mcLoc("block/smooth_basalt"));
                var hot = p.models().cubeAll("cooled_crust_hot", p.mcLoc("block/magma"));
                p.getVariantBuilder(c.get()).forAllStates(state -> ConfiguredModel.builder()
                        .modelFile(state.getValue(CooledCrustBlock.AGE) >= 2 ? hot : fresh).build());
            })
            .loot(NonNullBiConsumer.noop())
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .lang("Cooled Crust")
            .register();

    static {
        ACTIONS.register("launch", () -> LaunchAction.CODEC);
        ACTIONS.register("pull", () -> PullAction.CODEC);
        ACTIONS.register("web", () -> WebAction.CODEC);
        ACTIONS.register("bleed", () -> BleedAction.CODEC);
        ACTIONS.register("steal_item", () -> StealItemAction.CODEC);
        ACTIONS.register("blink_target", () -> BlinkTargetAction.CODEC);
        ACTIONS.register("ink_cloud", () -> InkCloudAction.CODEC);
    }

    private RangedContent() {
    }

    static void register(IEventBus modBus) {
        MOB_EFFECTS.register(modBus);
        PARTICLES.register(modBus);
        ACTIONS.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
