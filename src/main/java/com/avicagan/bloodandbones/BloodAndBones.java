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
        REGISTRATE.setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));

        BBCreativeTabs.register();
        BBBlocks.register();
        BBItems.register();
        BBBlockEntities.register();
        BBLang.register();

        modContainer.registerConfig(ModConfig.Type.CLIENT, BBClientConfig.SPEC);
        NeoForge.EVENT_BUS.register(CarcassEvents.class);
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            // the rig exporter reads client-only model classes; a dedicated server must never link it
            modEventBus.addListener(BBDatagen::gatherData);
        }
        modEventBus.addListener(BBGameTests::register);
        modEventBus.addListener(BBNetwork::register);
        com.avicagan.bloodandbones.registry.BBDataComponents.COMPONENTS.register(modEventBus);
        com.avicagan.bloodandbones.registry.BBParticles.PARTICLES.register(modEventBus);

        LOGGER.info("Create: Blood & Bones loaded");
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
