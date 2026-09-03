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
        event.enqueueWork(() -> ItemProperties.register(BBItems.MEAT_HOOK.get(), BloodAndBones.asResource("dragging"),
                // the hook is in the carcass, not in the hand, while its holder drags something
                (stack, level, entity, seed) -> entity != null && ClientDragState.all().containsKey(entity.getUUID()) ? 1.0F : 0.0F));
    }

    @SubscribeEvent
    public static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(BBParticles.BLOOD_DROP.get(), BloodDropParticle.Provider::new);
    }
}
