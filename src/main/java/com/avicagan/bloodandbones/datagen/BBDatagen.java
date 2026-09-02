package com.avicagan.bloodandbones.datagen;

import net.minecraft.data.DataGenerator;
import net.neoforged.neoforge.data.event.GatherDataEvent;

public class BBDatagen {
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        generator.addProvider(event.includeServer(), new RigExportProvider(generator.getPackOutput()));
        generator.addProvider(event.includeServer(), new PhysicsPropertiesProvider(generator.getPackOutput()));
    }
}
