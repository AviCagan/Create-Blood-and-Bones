package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.config.BBClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import java.util.Map;

/**
 * In bloodless mode a gory sound is heard as its clean twin (both are in sounds.json): the Mangler grinding flesh is a
 * grindstone and a rattle of chain. The server plays the same sound for everyone; each client picks what it hears, so
 * nothing but presentation reads the setting.
 */
@EventBusSubscriber(modid = BloodAndBones.MOD_ID, value = Dist.CLIENT)
public final class BloodlessSounds {
    /** Gory sound -> clean sound. */
    public static final Map<ResourceLocation, ResourceLocation> CLEAN = Map.of(
            BloodAndBones.asResource("machine.grind"), BloodAndBones.asResource("machine.grind_clean"));

    private BloodlessSounds() {
    }

    @SubscribeEvent
    public static void onPlay(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        ResourceLocation clean = sound == null ? null : CLEAN.get(sound.getLocation());
        if (clean == null || !BBClientConfig.bloodless()) {
            return;
        }
        // its volume and pitch are only known once it has picked which of its sounds to play
        sound.resolve(Minecraft.getInstance().getSoundManager());
        event.setSound(new SimpleSoundInstance(clean, sound.getSource(), sound.getVolume(), sound.getPitch(), RandomSource.create(),
                sound.isLooping(), sound.getDelay(), sound.getAttenuation(), sound.getX(), sound.getY(), sound.getZ(), sound.isRelative()));
    }
}
