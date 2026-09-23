package com.avicagan.bloodandbones.body;

import com.avicagan.bloodandbones.BloodAndBones;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class BBAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, BloodAndBones.MOD_ID);

    /** A player's body. Kept through death: a lost arm does not grow back on respawn. */
    public static final Supplier<AttachmentType<Body>> BODY = ATTACHMENTS.register("body",
            () -> AttachmentType.builder(Body::new).serialize(Body.CODEC, body -> !body.whole()).copyOnDeath().build());

    private BBAttachments() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
