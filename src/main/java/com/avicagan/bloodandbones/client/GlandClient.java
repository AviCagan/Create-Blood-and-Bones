package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.config.BBClientConfig;
import com.avicagan.bloodandbones.parts.CarcassArmour;
import com.avicagan.bloodandbones.parts.OrganKind;
import com.avicagan.bloodandbones.parts.Organs;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.registry.BBCreativeTabs;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * How a Gland looks: its organ file's shape (a sac, a bulb, a core...) tinted with its colour, wet and veined on top;
 * in bloodless mode a machined core with the organ's colour glowing through its window. The creative tab shows one of
 * each organ the data names, as the first mob holding it gives it.
 */
@EventBusSubscriber(modid = BloodAndBones.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class GlandClient {
    private GlandClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(BBItems.GLAND.get(), BloodAndBones.asResource("look"), (stack, level, entity, seed) -> kind(stack).lookIndex());
            ItemProperties.register(BBItems.GLAND.get(), BloodAndBones.asResource("bloodless"), (stack, level, entity, seed) -> BBClientConfig.bloodless() ? 1.0F : 0.0F);
        });
    }

    /** Its flesh (layer 0) or, bloodless, the glow in its window (layer 0 there too) takes the organ's colour; the rest is as drawn. */
    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? 0xFF000000 | kind(stack).tint() : -1, BBItems.GLAND.get());
    }

    /** One Gland of each organ, after the rest of the mod's items. */
    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(BBCreativeTabs.MAIN.getKey())) {
            return;
        }
        PartsData.Store store = PartsData.CLIENT;
        for (ResourceLocation organ : store.organs().keySet()) {
            if (store.organ(organ).item() == BBItems.GLAND.get()) {
                ItemStack example = Organs.example(store, organ);
                if (!example.isEmpty()) {
                    event.accept(example);
                }
            }
        }
    }

    private static OrganKind kind(ItemStack stack) {
        CarcassArmour.Organ organ = Organs.of(stack, PartsData.CLIENT);
        return organ == null ? Organs.kind(PartsData.CLIENT, BloodAndBones.asResource("gland")) : Organs.kind(PartsData.CLIENT, organ.organ());
    }
}
