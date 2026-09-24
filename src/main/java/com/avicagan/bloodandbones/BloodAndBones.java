package com.avicagan.bloodandbones;

import com.avicagan.bloodandbones.config.BBClientConfig;
import com.avicagan.bloodandbones.datagen.BBDatagen;
import com.avicagan.bloodandbones.event.CarcassEvents;
import com.avicagan.bloodandbones.gametest.BBGameTests;
import com.avicagan.bloodandbones.network.BBNetwork;
import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBCreativeTabs;
import com.avicagan.bloodandbones.registry.BBItems;
import com.avicagan.bloodandbones.registry.BBLang;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(BloodAndBones.MOD_ID)
public class BloodAndBones {
    public static final String MOD_ID = "bloodandbones";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID);

    public BloodAndBones(IEventBus modEventBus, ModContainer modContainer) {
        REGISTRATE.registerEventListeners(modEventBus);
        REGISTRATE.setTooltipModifierFactory(item -> new com.avicagan.bloodandbones.item.BBDescriptionModifier(item, FontHelper.Palette.STANDARD_CREATE)
                .andThen(com.simibubi.create.foundation.item.TooltipModifier.mapNull(com.simibubi.create.foundation.item.KineticStats.create(item))));

        BBCreativeTabs.register();
        BBBlocks.register();
        BBItems.register();
        com.avicagan.bloodandbones.registry.BBFluids.register();
        BBBlockEntities.register();
        com.avicagan.bloodandbones.registry.BBEntities.register();
        BBLang.register();
        com.avicagan.bloodandbones.registry.BBGameRules.register();
        com.avicagan.bloodandbones.registry.BBMovementChecks.register();

        modContainer.registerConfig(ModConfig.Type.CLIENT, BBClientConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, com.avicagan.bloodandbones.config.BBServerConfig.SPEC);
        NeoForge.EVENT_BUS.register(CarcassEvents.class);
        NeoForge.EVENT_BUS.register(com.avicagan.bloodandbones.carcass.trolley.TrolleyEvents.class);
        NeoForge.EVENT_BUS.register(com.avicagan.bloodandbones.decoration.GutChainHanging.class);
        NeoForge.EVENT_BUS.register(com.avicagan.bloodandbones.body.BodyEffects.class);
        NeoForge.EVENT_BUS.register(com.avicagan.bloodandbones.body.Necrosis.class);
        NeoForge.EVENT_BUS.register(com.avicagan.bloodandbones.cyber.Throttle.class);
        NeoForge.EVENT_BUS.register(com.avicagan.bloodandbones.cyber.ModuleActions.class);
        NeoForge.EVENT_BUS.register(com.avicagan.bloodandbones.cyber.SetBonus.class);
        NeoForge.EVENT_BUS.register(com.avicagan.bloodandbones.parts.TraitEvents.class);
        // the four groups of trait effects, each with its own handlers (docs/ARCHITECTURE-PROPOSAL.md section 15.8)
        NeoForge.EVENT_BUS.register(com.avicagan.bloodandbones.parts.effect.MotionEffects.class);
        NeoForge.EVENT_BUS.register(com.avicagan.bloodandbones.parts.effect.RangedEffects.class);
        NeoForge.EVENT_BUS.register(com.avicagan.bloodandbones.parts.effect.SocialEffects.class);
        NeoForge.EVENT_BUS.register(com.avicagan.bloodandbones.parts.effect.UpkeepEffects.class);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> {
            com.avicagan.bloodandbones.cyber.Coupler.clear();
            com.avicagan.bloodandbones.minion.BloodTroughBlockEntity.clear();
            com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity.clear();
        });
        // an Analytical Lens reads machines as Create's goggles do
        com.simibubi.create.content.equipment.goggles.GogglesItem.addIsWearingPredicate(player ->
                com.avicagan.bloodandbones.cyber.Modules.has(player, com.avicagan.bloodandbones.cyber.Module.ANALYTICAL_LENS));
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            // the rig exporter reads client-only model classes; a dedicated server must never link it
            modEventBus.addListener(BBDatagen::gatherData);
            com.avicagan.bloodandbones.client.DevShowcase.init();
            com.avicagan.bloodandbones.client.BBClientSetup.registerConfigScreen(modContainer);
            com.avicagan.bloodandbones.client.BBClientSetup.initEffects(modEventBus);
        }
        modEventBus.addListener(BBGameTests::register);
        modEventBus.addListener(BBNetwork::register);
        modEventBus.addListener(BBBlockEntities::registerCapabilities);
        com.avicagan.bloodandbones.registry.BBDataComponents.COMPONENTS.register(modEventBus);
        com.avicagan.bloodandbones.registry.BBParticles.PARTICLES.register(modEventBus);
        com.avicagan.bloodandbones.registry.BBItemAttributes.register(modEventBus);
        com.avicagan.bloodandbones.registry.BBSounds.register(modEventBus);
        com.avicagan.bloodandbones.body.BBAttachments.register(modEventBus);
        com.avicagan.bloodandbones.backtank.BBArmorMaterials.register(modEventBus);
        com.avicagan.bloodandbones.registry.BBRecipes.register(modEventBus);
        com.avicagan.bloodandbones.parts.TraitEffects.register(modEventBus);
        com.avicagan.bloodandbones.parts.TraitConditions.register(modEventBus);
        com.avicagan.bloodandbones.parts.effect.MotionEffects.registerContent(modEventBus);
        com.avicagan.bloodandbones.parts.effect.RangedEffects.registerContent(modEventBus);
        com.avicagan.bloodandbones.parts.effect.SocialEffects.registerContent(modEventBus);
        com.avicagan.bloodandbones.parts.effect.UpkeepEffects.registerContent(modEventBus);
        com.avicagan.bloodandbones.registry.BBAttributes.register(modEventBus);
        com.avicagan.bloodandbones.minion.MinionSerializers.register(modEventBus);
        // a changed trait strength or list of switched-off effect types reaches every creature's traits
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == com.avicagan.bloodandbones.config.BBServerConfig.SPEC) {
                com.avicagan.bloodandbones.parts.PartsData.SERVER.invalidate();
            }
        });
        modEventBus.addListener((net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent event) -> {
            event.register(com.avicagan.bloodandbones.body.Vent.EFFECTS);
            event.register(com.avicagan.bloodandbones.parts.Hides.SOURCES);
        });

        LOGGER.info("Create: Blood & Bones loaded");
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
