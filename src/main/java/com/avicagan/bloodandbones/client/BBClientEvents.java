package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.config.BBClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Client game events. */
@EventBusSubscriber(modid = BloodAndBones.MOD_ID, value = Dist.CLIENT)
public final class BBClientEvents {
    private BBClientEvents() {
    }

    /** Keep the bloodless rewording in front of the game's language after any reload replaced it. */
    /** Holding use empty-handed with a Vent Arm sprays; the server keeps the pace. */
    @SubscribeEvent
    public static void onVent(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null && mc.screen == null && mc.options.keyUse.isDown() && mc.player.tickCount % 3 == 0
                && com.avicagan.bloodandbones.body.Vent.ready(mc.player)
                && !com.avicagan.bloodandbones.backtank.FluidBacktankItem.fluid(com.avicagan.bloodandbones.backtank.FluidBacktankItem.wornBy(mc.player)).isEmpty()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.avicagan.bloodandbones.body.Vent.Payload());
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        BloodlessLanguage.install();
    }

    /** A server's forced bloodless mode does not follow the player to the next world. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BBClientConfig.setServerForced(false);
    }
}
