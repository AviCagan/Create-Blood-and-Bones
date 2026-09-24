package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.network.OrganActivatePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The Organ Ability key (docs/PARTS-AND-TRAITS.md section 5.2): G by default, rebindable, with the mod's other keys.
 * Each press asks the server to fire the next ready activate effect of the armour worn; the server decides which.
 */
@EventBusSubscriber(modid = BloodAndBones.MOD_ID, value = Dist.CLIENT)
public final class OrganAbilityClient {
    public static final KeyMapping ORGAN_ABILITY = new KeyMapping("key.bloodandbones.organ_ability", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CyberClient.CATEGORY);

    private OrganAbilityClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (ORGAN_ABILITY.consumeClick()) {
            if (mc.player != null && mc.screen == null && !mc.player.isSpectator()) {
                PacketDistributor.sendToServer(OrganActivatePayload.INSTANCE);
            }
        }
    }
}
