package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Models that are not an item's own inventory model: the bloodied hook drawn stuck in a carcass. */
@EventBusSubscriber(modid = BloodAndBones.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BBClientModels {
    public static final ModelResourceLocation MEAT_HOOK_BLOODY = ModelResourceLocation.standalone(BloodAndBones.asResource("item/meat_hook_bloody"));

    private BBClientModels() {
    }

    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        event.register(MEAT_HOOK_BLOODY);
    }
}
