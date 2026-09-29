package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.config.BBClientConfig;
import com.avicagan.bloodandbones.mixin.SoundInstanceAccessor;
import com.avicagan.bloodandbones.registry.BBSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Bloodless mode's sounds: every wet sound the mod plays is heard as its metal twin (BBSounds#twin), with a rattle
 * over the heavy ones, whoever played it (the server sends the wet one to everyone). Only this client's ears change:
 * the same instance, where it was, as loud, at the same pitch, but a clank instead of a squelch.
 */
@EventBusSubscriber(modid = BloodAndBones.MOD_ID, value = Dist.CLIENT)
public final class BloodlessSounds {
    private static final RandomSource RANDOM = RandomSource.create();

    private BloodlessSounds() {
    }

    @SubscribeEvent
    public static void onPlay(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || !BBClientConfig.bloodless() || !(sound instanceof AbstractSoundInstance asked)) {
            return;
        }
        BBSounds.Twin twin = BBSounds.twin(sound.getLocation());
        if (twin == null) {
            return;
        }
        SoundInstanceAccessor raw = (SoundInstanceAccessor) asked;
        event.setSound(copy(sound, twin.clean().getId(), raw.bloodandbones$volume(), raw.bloodandbones$pitch()));
        if (twin.layer() != null) {
            Minecraft.getInstance().getSoundManager().play(copy(sound, twin.layer().getId(), raw.bloodandbones$volume() * 0.6F, raw.bloodandbones$pitch()));
        }
    }

    private static SoundInstance copy(SoundInstance like, net.minecraft.resources.ResourceLocation location, float volume, float pitch) {
        return new SimpleSoundInstance(location, like.getSource(), volume, pitch, RandomSource.create(RANDOM.nextLong()), like.isLooping(), like.getDelay(),
                like.getAttenuation(), like.getX(), like.getY(), like.getZ(), like.isRelative());
    }
}
