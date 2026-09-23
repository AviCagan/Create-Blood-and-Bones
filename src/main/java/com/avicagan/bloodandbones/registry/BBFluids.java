package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import com.simibubi.create.AllFluids;
import com.simibubi.create.AllTags.AllFluidTags;
import com.tterrag.registrate.builders.FluidBuilder;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.entry.FluidEntry;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.loaders.DynamicFluidContainerModelBuilder;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector3f;

/**
 * Blood and Soul Blood, registered the way Create registers honey/chocolate (AllFluids) and
 * Create: Dragons Plus registers its dye fluids: one greyscale texture pair, tinted per fluid.
 *
 * <p>Both fluids get a source + flowing fluid, a LiquidBlock and a plain {@link BucketItem}. A plain
 * BucketItem (exact class) is what NeoForge attaches FluidBucketWrapper to, and what Create's spout
 * and item drain accept, so pipes, tanks, basins, spouts, drains and create:filling all work.
 *
 * <p>Always use {@code BLOOD.getSource()} (not {@code BLOOD.get()}, which is the flowing fluid) for
 * FluidStacks and recipes.
 */
public class BBFluids {
    /** Greyscale vanilla water textures, already on the block atlas; tinted per fluid below. */
    private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");

    public static final int BLOOD_RGB = 0x7F0A0A;      // dark red
    public static final int SOUL_BLOOD_RGB = 0x167A74; // dark teal

    /** c:blood, the common tag other mods look blood up by. */
    public static final TagKey<Fluid> BLOOD_TAG = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "blood"));
    public static final TagKey<Item> BLOOD_BUCKETS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "buckets/blood"));

    /** Empties a bucket into the world like vanilla's water/lava dispenser behaviour (modded buckets have none). */
    private static final DefaultDispenseItemBehavior DISPENSE_BUCKET = new DefaultDispenseItemBehavior() {
        private final DefaultDispenseItemBehavior fallback = new DefaultDispenseItemBehavior();

        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            DispensibleContainerItem bucket = (DispensibleContainerItem) stack.getItem();
            BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            Level level = source.level();
            if (bucket.emptyContents(null, level, pos, null, stack)) {
                bucket.checkExtraContent(null, level, stack, pos);
                return consumeWithRemainder(source, stack, new ItemStack(Items.BUCKET));
            }
            return fallback.dispense(source, stack);
        }
    };

    public static final FluidEntry<BaseFlowingFluid.Flowing> BLOOD = BloodAndBones.REGISTRATE
            .fluid("blood", STILL, FLOW, TintedFluidType.create(BLOOD_RGB, () -> 1f / 16f))
            .lang("Blood")
            .properties(p -> p
                    .density(1060)
                    .viscosity(3000)
                    .canExtinguish(true)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY))
            .fluidProperties(p -> p
                    .levelDecreasePerBlock(2)
                    .tickRate(15)
                    .slopeFindDistance(3)
                    .explosionResistance(100f))
            // bottomless/deny: a hose pulley never treats a blood lake as infinite, even with bottomlessFluidMode ALLOW_ALL (CEI does this for experience)
            .tag(BLOOD_TAG, AllFluidTags.BOTTOMLESS_DENY.tag)
            // bucket() needs a source to exist already; Create does the same (AllFluids "TODO: remove when Registrate fixes FluidBuilder")
            .source(BaseFlowingFluid.Source::new)
            .block()
            .properties(p -> p.mapColor(MapColor.COLOR_RED))
            .lang("Blood")
            .build()
            .bucket()
            .lang("Blood Bucket")
            .tag(Tags.Items.BUCKETS, BLOOD_BUCKETS)
            .model(BBFluids::dynamicBucketModel)
            .color(() -> DynamicFluidContainerModel.Colors::new)
            .onRegister(BBFluids::registerDispenseBehavior)
            .build()
            .register();

    public static final FluidEntry<BaseFlowingFluid.Flowing> SOUL_BLOOD = BloodAndBones.REGISTRATE
            .fluid("soul_blood", STILL, FLOW, TintedFluidType.create(SOUL_BLOOD_RGB, () -> 1f / 8f))
            .lang("Soul Blood")
            .properties(p -> p
                    .density(1100)
                    .viscosity(4000)
                    .lightLevel(10) // soul-fire brightness: lights the block, tanks, pipes and the bucket
                    .rarity(Rarity.UNCOMMON)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY))
            .fluidProperties(p -> p
                    .levelDecreasePerBlock(2)
                    .tickRate(20)
                    .slopeFindDistance(3)
                    .explosionResistance(100f))
            .tag(AllFluidTags.BOTTOMLESS_DENY.tag)
            .source(BaseFlowingFluid.Source::new)
            .block()
            .properties(p -> p.mapColor(MapColor.WARPED_WART_BLOCK))
            .lang("Soul Blood")
            .build()
            .bucket()
            .lang("Soul Blood Bucket")
            .properties(p -> p.rarity(Rarity.UNCOMMON))
            .tag(Tags.Items.BUCKETS)
            .model(BBFluids::dynamicBucketModel)
            .color(() -> DynamicFluidContainerModel.Colors::new)
            .onRegister(BBFluids::registerDispenseBehavior)
            .build()
            .register();

    /*
     * FluidEntry.getBlock() is always empty for Registrate fluids: the entry is named "flowing_<name>" and its
     * sibling-block lookup misses the "<name>" block. Keep typed handles to the liquid blocks instead.
     */
    public static final BlockEntry<LiquidBlock> BLOOD_BLOCK = BlockEntry.cast(BloodAndBones.REGISTRATE.get("blood", Registries.BLOCK));
    public static final BlockEntry<LiquidBlock> SOUL_BLOOD_BLOCK = BlockEntry.cast(BloodAndBones.REGISTRATE.get("soul_blood", Registries.BLOCK));

    /** The still blood fluid, typed plainly (getSource() is generic, which confuses overloads). */
    public static Fluid blood() {
        return BLOOD.getSource();
    }

    public static Fluid soulBlood() {
        return SOUL_BLOOD.getSource();
    }

    public static void register() {
        BloodAndBones.REGISTRATE.addRawLang("tag.fluid.c.blood", "Blood");
        BloodAndBones.REGISTRATE.addRawLang("tag.item.c.buckets.blood", "Blood Buckets");
    }

    /** NeoForge's dynamic bucket: vanilla bucket + the fluid's still texture through a mask, tinted on tint index 1. */
    private static void dynamicBucketModel(com.tterrag.registrate.providers.DataGenContext<Item, BucketItem> ctx,
                                           com.tterrag.registrate.providers.RegistrateItemModelProvider prov) {
        prov.getBuilder(ctx.getName())
                // unchecked: datagen's ExistingFileHelper does not see the neoforge namespace
                .parent(new ModelFile.UncheckedModelFile(ResourceLocation.fromNamespaceAndPath("neoforge", "item/bucket")))
                .customLoader(DynamicFluidContainerModelBuilder::begin)
                .fluid(ctx.getEntry().content)
                .end();
    }

    private static void registerDispenseBehavior(BucketItem bucket) {
        DispenserBlock.registerBehavior(bucket, DISPENSE_BUCKET);
    }

    /**
     * Create's AllFluids.TintedFluidType with a real tint: opaque ARGB for FluidStacks (tanks, pipes, basins,
     * JEI, the bucket), alpha stripped for the placed block (Create/CDP trick for the solid render layer),
     * plus a coloured, short underwater fog.
     */
    public static class TintedFluidType extends AllFluids.TintedFluidType {
        private final int argb;
        private final Vector3f fogColor;
        private final Supplier<Float> fogDistance;

        private TintedFluidType(Properties properties, ResourceLocation still, ResourceLocation flow, int rgb,
                                Supplier<Float> fogDistance) {
            super(properties, still, flow);
            this.argb = 0xFF000000 | rgb;
            this.fogColor = new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f);
            this.fogDistance = fogDistance;
        }

        public static FluidBuilder.FluidTypeFactory create(int rgb, Supplier<Float> fogDistance) {
            return (p, still, flow) -> new TintedFluidType(p, still, flow, rgb, fogDistance);
        }

        @Override
        protected int getTintColor(FluidStack stack) {
            return argb;
        }

        @Override
        protected int getTintColor(FluidState state, BlockAndTintGetter level, BlockPos pos) {
            return argb & 0x00FFFFFF;
        }

        @Override
        protected Vector3f getCustomFogColor() {
            return fogColor;
        }

        @Override
        protected float getFogDistanceModifier() {
            return fogDistance.get();
        }
    }
}
