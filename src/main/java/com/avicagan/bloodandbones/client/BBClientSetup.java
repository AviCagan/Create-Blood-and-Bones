package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.registry.BBItems;
import com.avicagan.bloodandbones.registry.BBParticles;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = BloodAndBones.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BBClientSetup {
    private BBClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        net.createmod.ponder.foundation.PonderIndex.addPlugin(new com.avicagan.bloodandbones.client.ponder.BBPonderPlugin());
        event.enqueueWork(() -> ItemProperties.register(BBItems.MEAT_HOOK.get(), BloodAndBones.asResource("dragging"),
                // the hook is in the carcass, not in the hand, while its holder drags something
                (stack, level, entity, seed) -> entity != null && ClientDragState.all().containsKey(entity.getUUID()) ? 1.0F : 0.0F));
    }

    @SubscribeEvent
    public static void onClientExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        CarcassPieceItemRenderer renderer = new CarcassPieceItemRenderer();
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }
        }, BBItems.CARCASS_PIECE.get());
    }

    /** Blood stains darken as they dry: wet red, drying brown-red, dried near black. */
    @SubscribeEvent
    public static void onBlockColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> switch (state.getValue(com.avicagan.bloodandbones.bleeding.BloodStainBlock.AGE)) {
            case 0 -> 0xFFFFFF;
            case 1 -> 0xA0806C;
            default -> 0x6A5448;
        }, com.avicagan.bloodandbones.registry.BBBlocks.BLOOD_STAIN.get());
    }

    /** In bloodless mode blood stains draw nothing (they still exist, and wash away the same). */
    @SubscribeEvent
    public static void onModifyBakingResult(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) {
        for (net.minecraft.world.level.block.state.BlockState state : com.avicagan.bloodandbones.registry.BBBlocks.BLOOD_STAIN.get().getStateDefinition().getPossibleStates()) {
            event.getModels().computeIfPresent(net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state), (key, model) -> new BloodlessHidden(model));
        }
    }

    /** Changing bloodless mode redraws the world, so stains appear or go without a restart. */
    @SubscribeEvent
    public static void onConfigReload(net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == com.avicagan.bloodandbones.config.BBClientConfig.SPEC) {
            com.avicagan.bloodandbones.config.BBClientConfig.redraw();
        }
    }

    private static final class BloodlessHidden extends net.neoforged.neoforge.client.model.BakedModelWrapper<net.minecraft.client.resources.model.BakedModel> {
        BloodlessHidden(net.minecraft.client.resources.model.BakedModel model) {
            super(model);
        }

        @Override
        public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(@org.jetbrains.annotations.Nullable net.minecraft.world.level.block.state.BlockState state,
                                                                                          @org.jetbrains.annotations.Nullable net.minecraft.core.Direction side,
                                                                                          net.minecraft.util.RandomSource rand) {
            return com.avicagan.bloodandbones.config.BBClientConfig.bloodless() ? java.util.List.of() : super.getQuads(state, side, rand);
        }

        @Override
        public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(@org.jetbrains.annotations.Nullable net.minecraft.world.level.block.state.BlockState state,
                                                                                          @org.jetbrains.annotations.Nullable net.minecraft.core.Direction side,
                                                                                          net.minecraft.util.RandomSource rand,
                                                                                          net.neoforged.neoforge.client.model.data.ModelData data,
                                                                                          @org.jetbrains.annotations.Nullable net.minecraft.client.renderer.RenderType renderType) {
            return com.avicagan.bloodandbones.config.BBClientConfig.bloodless() ? java.util.List.of() : super.getQuads(state, side, rand, data, renderType);
        }
    }

    @SubscribeEvent
    public static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(BBParticles.BLOOD_DROP.get(), BloodDropParticle.Provider::new);
    }
}
