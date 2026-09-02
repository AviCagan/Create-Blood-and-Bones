package com.avicagan.bloodandbones;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(BloodAndBones.MOD_ID)
public class BloodAndBones {
    public static final String MOD_ID = "bloodandbones";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BloodAndBones(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        LOGGER.info("Create: Blood & Bones loading");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Create: Blood & Bones common setup");
    }
}
