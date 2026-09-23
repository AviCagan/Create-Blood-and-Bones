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

    /** Mods → Blood & Bones → Config: NeoForge's own settings screen for the client and server settings. */
    public static void registerConfigScreen(net.neoforged.fml.ModContainer container) {
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                net.neoforged.neoforge.client.gui.ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        net.createmod.ponder.foundation.PonderIndex.addPlugin(new com.avicagan.bloodandbones.client.ponder.BBPonderPlugin());
        event.enqueueWork(() -> {
            ItemProperties.register(BBItems.MEAT_HOOK.get(), BloodAndBones.asResource("dragging"),
                    // the hook is in the carcass, not in the hand, while its holder drags something
                    (stack, level, entity, seed) -> entity != null && ClientDragState.all().containsKey(entity.getUUID()) ? 1.0F : 0.0F);
            // blades show bloody for a while after drawing blood; never in bloodless mode
            for (net.minecraft.world.item.Item blade : java.util.List.of(BBItems.CLEAVER.get(), BBItems.BLOOD_STEEL_CLEAVER.get(), BBItems.FLENSING_KNIFE.get())) {
                ItemProperties.register(blade, BloodAndBones.asResource("bloody"), (stack, level, entity, seed) -> {
                    Long at = stack.get(com.avicagan.bloodandbones.registry.BBDataComponents.BLOODIED_AT.get());
                    net.minecraft.world.level.Level world = level != null ? level : entity != null ? entity.level() : net.minecraft.client.Minecraft.getInstance().level;
                    if (at == null || world == null || com.avicagan.bloodandbones.config.BBClientConfig.bloodless()) {
                        return 0.0F;
                    }
                    long age = world.getGameTime() - at;
                    return age >= 0 && age < com.avicagan.bloodandbones.carcass.Blood.BLOODY_TICKS ? 1.0F : 0.0F;
                });
            }
        });
    }

    /** Implants drawn on players, both arm shapes. */
    @SubscribeEvent
    public static void onAddLayers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer) {
                renderer.addLayer(new BodyRendering.ImplantLayer(renderer));
            }
        }
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
        event.register((state, level, pos, tint) -> {
            // Soul Blood dries darker too, but greyer rather than brown
            boolean soul = state.getValue(com.avicagan.bloodandbones.bleeding.BloodStainBlock.SOUL);
            return switch (state.getValue(com.avicagan.bloodandbones.bleeding.BloodStainBlock.AGE)) {
                case 0 -> 0xFFFFFF;
                case 1 -> soul ? 0x8C9A98 : 0xA0806C;
                default -> soul ? 0x5A6462 : 0x6A5448;
            };
        }, com.avicagan.bloodandbones.registry.BBBlocks.BLOOD_STAIN.get());
    }

    /**
     * In bloodless mode blood stains draw nothing (they still exist, and wash away the same), and the
     * machines draw clean casings and blades.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onModifyBakingResult(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) {
        for (net.minecraft.world.level.block.state.BlockState state : com.avicagan.bloodandbones.registry.BBBlocks.BLOOD_STAIN.get().getStateDefinition().getPossibleStates()) {
            event.getModels().computeIfPresent(net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state), (key, model) -> new BloodlessHidden(model));
        }
        // the hooks' red points come out clean, on the wall, on the track and in the hand
        for (var hook : java.util.List.of(com.avicagan.bloodandbones.registry.BBBlocks.SHACKLE_HOOK, com.avicagan.bloodandbones.registry.BBBlocks.BUTCHER_HOOK)) {
            for (net.minecraft.world.level.block.state.BlockState state : hook.get().getStateDefinition().getPossibleStates()) {
                event.getModels().computeIfPresent(net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state),
                        (key, model) -> new BloodlessSwap(model, BloodlessSwap.HOOK));
            }
            event.getModels().computeIfPresent(net.minecraft.client.resources.model.ModelResourceLocation.inventory(hook.getId()),
                    (key, model) -> new BloodlessSwap(model, BloodlessSwap.HOOK));
        }
        for (net.minecraft.world.level.block.state.BlockState state : com.avicagan.bloodandbones.registry.BBBlocks.BUTCHER_TABLE.get().getStateDefinition().getPossibleStates()) {
            event.getModels().computeIfPresent(net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state),
                    (key, model) -> new BloodlessSwap(model, BloodlessSwap.TABLE));
        }
        event.getModels().computeIfPresent(net.minecraft.client.resources.model.ModelResourceLocation.inventory(com.avicagan.bloodandbones.registry.BBBlocks.BUTCHER_TABLE.getId()),
                (key, model) -> new BloodlessSwap(model, BloodlessSwap.TABLE));
        for (net.minecraft.world.level.block.state.BlockState state : com.avicagan.bloodandbones.registry.BBBlocks.SURGERY_TABLE.get().getStateDefinition().getPossibleStates()) {
            event.getModels().computeIfPresent(net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state),
                    (key, model) -> new BloodlessSwap(model, BloodlessSwap.SURGERY));
        }
        event.getModels().computeIfPresent(net.minecraft.client.resources.model.ModelResourceLocation.inventory(com.avicagan.bloodandbones.registry.BBBlocks.SURGERY_TABLE.getId()),
                (key, model) -> new BloodlessSwap(model, BloodlessSwap.SURGERY));
        for (net.minecraft.world.level.block.state.BlockState state : com.avicagan.bloodandbones.registry.BBBlocks.GUT_CHAIN.get().getStateDefinition().getPossibleStates()) {
            event.getModels().computeIfPresent(net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state),
                    (key, model) -> new BloodlessSwap(model, BloodlessSwap.GUTS));
        }
        event.getModels().computeIfPresent(net.minecraft.client.resources.model.ModelResourceLocation.inventory(com.avicagan.bloodandbones.registry.BBBlocks.GUT_CHAIN.getId()),
                (key, model) -> new BloodlessSwap(model, BloodlessSwap.GUTS));
        // the machines' bloody casings and blades come out clean, in the world and in the hand; so does the
        // Bloody Casing, wrapped outside Create's connected textures (hence the lowest priority) so its
        // joined-up edges are swapped too
        for (var machine : java.util.List.of(com.avicagan.bloodandbones.registry.BBBlocks.MANGLER, com.avicagan.bloodandbones.registry.BBBlocks.GUILLOTINE,
                com.avicagan.bloodandbones.registry.BBBlocks.BEHEADER, com.avicagan.bloodandbones.registry.BBBlocks.DEGLOVER,
                com.avicagan.bloodandbones.registry.BBBlocks.BLOODY_CASING)) {
            for (net.minecraft.world.level.block.state.BlockState state : machine.get().getStateDefinition().getPossibleStates()) {
                event.getModels().computeIfPresent(net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state),
                        (key, model) -> new BloodlessSwap(model, BloodlessSwap.MACHINES));
            }
            event.getModels().computeIfPresent(net.minecraft.client.resources.model.ModelResourceLocation.inventory(machine.getId()),
                    (key, model) -> new BloodlessSwap(model, BloodlessSwap.MACHINES));
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
        event.registerSpriteSet(BBParticles.SOUL_BLOOD_DROP.get(), BloodDropParticle.Provider::new);
        event.registerSpriteSet(BBParticles.FLY.get(), FlyParticle.Provider::new);
        event.registerSpriteSet(BBParticles.GIB.get(), GibParticle.Provider::new);
    }
}
