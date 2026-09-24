package com.avicagan.bloodandbones.body;

import com.avicagan.bloodandbones.BloodAndBones;
import com.mojang.serialization.Codec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class BBAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, BloodAndBones.MOD_ID);

    /** A player's body. Kept through death: a lost arm does not grow back on respawn. */
    public static final Supplier<AttachmentType<Body>> BODY = ATTACHMENTS.register("body",
            () -> AttachmentType.builder(Body::new).serialize(Body.CODEC, body -> !body.whole()).copyOnDeath().build());

    /**
     * The trait effects cooling down on a creature (docs/PARTS-AND-TRAITS.md section 7.6), by trait and place in it, as the
     * game time each runs out: saved with it, so a relog, a trip home from the End or a minion's chunk reloading keeps them.
     * Not kept through death. An Organ Ability's is kept on its piece instead (BBDataComponents#COOLDOWN_UNTIL).
     */
    public static final Supplier<AttachmentType<Map<String, Long>>> TRAIT_COOLDOWNS = ATTACHMENTS.register("trait_cooldowns",
            () -> AttachmentType.<Map<String, Long>>builder(() -> new HashMap<>())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(HashMap::new, map -> map), map -> !map.isEmpty()).build());

    private BBAttachments() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
