package com.avicagan.bloodandbones.client.effect;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.client.BloodDropParticle;
import com.avicagan.bloodandbones.config.BBClientConfig;
import com.avicagan.bloodandbones.parts.effect.BeamPayload;
import com.avicagan.bloodandbones.parts.effect.RangedContent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.Map;

/**
 * Ranged's client side ({@link com.avicagan.bloodandbones.parts.effect.RangedEffects}): projectile and hitscan beams,
 * the Bleeding effect's drips (grey sparks in bloodless mode). Never loaded on a dedicated server: only the client
 * branch of the mod's constructor calls {@link #init}, and a client-bound payload's handler reaches it from inside
 * its lambda.
 */
public final class RangedClient {
    private static final ResourceLocation BEAM_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/guardian_beam.png");
    private static final RenderType BEAM = RenderType.entityCutoutNoCull(BEAM_TEXTURE);
    private static final ResourceLocation EFFECT_ATLAS = ResourceLocation.withDefaultNamespace("textures/atlas/mob_effects.png");
    /** A shot with no windup still shows its beam this long. */
    private static final int FLASH = 4;

    /** A beam being drawn: from whom, to whom (or along their look), since and until when. */
    private record Beam(int target, long start, long end, float range, String kind) {
    }

    private static final Map<Integer, Beam> BEAMS = new HashMap<>();

    private RangedClient() {
    }

    /**
     * Called once, from the mod's constructor on a client. Mod-bus events (renderers, layers, particles, key mappings)
     * go on {@code modBus}; game events (ticks, rendering the level, input) on {@code NeoForge.EVENT_BUS}.
     */
    public static void init(IEventBus modBus) {
        modBus.addListener(RangedClient::onParticles);
        modBus.addListener(RangedClient::onClientExtensions);
        NeoForge.EVENT_BUS.addListener(RangedClient::onRenderLevel);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> BEAMS.clear());
    }

    // ---- Bleeding

    private static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(RangedContent.LEAK_SPARK.get(), LeakSparkParticle.Provider::new);
        event.registerSpriteSet(RangedContent.BLEEDING_DRIP.get(), sprites -> {
            ParticleProvider<SimpleParticleType> drop = new BloodDropParticle.Provider(sprites);
            return (type, level, x, y, z, dx, dy, dz) -> {
                if (BBClientConfig.bloodless()) {
                    // bloodless mode: a grey spark in place of the drop
                    Minecraft.getInstance().particleEngine.createParticle(RangedContent.LEAK_SPARK.get(), x, y, z, 0.0, 0.02, 0.0);
                    return null;
                }
                // a mob effect's swirl asks with a speed of 1 each way: a drop just lets go and falls
                return drop.createParticle(type, level, x, y, z, 0.0, 0.0, 0.0);
            };
        });
    }

    /** Bloodless mode shows Leaking's own icon, a grey drip, where Bleeding's red one would be. */
    private static void onClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerMobEffect(new IClientMobEffectExtensions() {
            @Override
            public boolean renderInventoryIcon(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen, GuiGraphics graphics, int x, int y, int blitOffset) {
                if (!BBClientConfig.bloodless()) {
                    return false;
                }
                graphics.blit(x, y + 7, blitOffset, 18, 18, leaking());
                return true;
            }

            @Override
            public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphics graphics, int x, int y, float z, float alpha) {
                if (!BBClientConfig.bloodless()) {
                    return false;
                }
                graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
                graphics.blit(x + 3, y + 3, 0, 18, 18, leaking());
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                return true;
            }
        }, RangedContent.BLEEDING.get());
    }

    private static TextureAtlasSprite leaking() {
        return ((TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(EFFECT_ATLAS)).getSprite(BloodAndBones.asResource("leaking"));
    }

    // ---- hitscan beams

    /** A hitscan's beam started (a charge, or a flash for one with none). */
    public static void receive(BeamPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        BEAMS.put(payload.host(), new Beam(payload.target(), now, now + Math.max(FLASH, payload.ticks()), payload.range(), payload.beam()));
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || BEAMS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            BEAMS.clear();
            return;
        }
        long now = level.getGameTime();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack ms = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(BEAM);
        BEAMS.entrySet().removeIf(e -> now > e.getValue().end() || !(level.getEntity(e.getKey()) instanceof Entity));
        for (Map.Entry<Integer, Beam> e : BEAMS.entrySet()) {
            Entity host = level.getEntity(e.getKey());
            Beam beam = e.getValue();
            if (host == null) {
                continue;
            }
            Vec3 from = host.getEyePosition(partial);
            Entity target = beam.target() >= 0 ? level.getEntity(beam.target()) : null;
            Vec3 to;
            if (target != null) {
                to = target.getPosition(partial).add(0.0, target.getBbHeight() * 0.5, 0.0);
            } else {
                Vec3 reach = from.add(host.getViewVector(partial).scale(beam.range()));
                BlockHitResult block = level.clip(new ClipContext(from, reach, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, host));
                to = block.getType() == HitResult.Type.MISS ? reach : block.getLocation();
            }
            float age = now - beam.start() + partial;
            float charge = Mth.clamp(age / Math.max(1.0F, beam.end() - beam.start()), 0.0F, 1.0F);
            draw(ms, vc, from.subtract(camera), to.subtract(camera), age, charge, beam.kind());
        }
        buffers.endBatch(BEAM);
    }

    /**
     * One beam, drawn as the guardian's is (GuardianRenderer): a turning square tube of its texture, scrolling. The
     * guardian's warms from purple to yellow as it charges; the warden's is a wider sculk teal; a strand of silk is thin and
     * pale.
     */
    private static void draw(PoseStack ms, VertexConsumer vc, Vec3 from, Vec3 to, float age, float charge, String kind) {
        Vec3 d = to.subtract(from);
        float length = (float) d.length();
        if (length < 0.05F) {
            return;
        }
        d = d.normalize();
        float c = charge * charge;
        int r;
        int g;
        int b;
        float width;
        switch (kind) {
            case "sonic_boom" -> {
                r = 20 + (int) (c * 60.0F);
                g = 110 + (int) (c * 145.0F);
                b = 130 + (int) (c * 125.0F);
                width = 1.4F;
            }
            case "strand" -> {
                r = 225;
                g = 225;
                b = 215;
                width = 0.15F;
            }
            default -> {
                r = 64 + (int) (c * 191.0F);
                g = 32 + (int) (c * 191.0F);
                b = 128 - (int) (c * 64.0F);
                width = 1.0F;
            }
        }
        ms.pushPose();
        ms.translate(from.x, from.y, from.z);
        ms.mulPose(Axis.YP.rotationDegrees(((float) (Math.PI / 2) - (float) Math.atan2(d.z, d.x)) * Mth.RAD_TO_DEG));
        ms.mulPose(Axis.XP.rotationDegrees((float) Math.acos(d.y) * Mth.RAD_TO_DEG));
        float spin = age * 0.05F * -1.5F;
        float inner = 0.2F * width;
        float outer = 0.282F * width;
        float v0 = -1.0F + age * 0.5F % 1.0F;
        float v1 = length * 2.5F + v0;
        PoseStack.Pose pose = ms.last();
        // the four sides of the tube
        for (int side = 0; side < 2; side++) {
            float a = spin + (float) Math.PI * side * 0.5F;
            float x0 = Mth.cos(a + (float) Math.PI) * inner;
            float z0 = Mth.sin(a + (float) Math.PI) * inner;
            float x1 = Mth.cos(a) * inner;
            float z1 = Mth.sin(a) * inner;
            vertex(vc, pose, x0, length, z0, r, g, b, 0.4999F, v1);
            vertex(vc, pose, x0, 0.0F, z0, r, g, b, 0.4999F, v0);
            vertex(vc, pose, x1, 0.0F, z1, r, g, b, 0.0F, v0);
            vertex(vc, pose, x1, length, z1, r, g, b, 0.0F, v1);
        }
        // its glowing end cap
        float cap = age % 2.0F < 1.0F ? 0.0F : 0.5F;
        float[] turns = {(float) (Math.PI * 3.0 / 4.0), (float) (Math.PI / 4.0), (float) (Math.PI * 7.0 / 4.0), (float) (Math.PI * 5.0 / 4.0)};
        float[] us = {0.5F, 1.0F, 1.0F, 0.5F};
        float[] vs = {cap + 0.5F, cap + 0.5F, cap, cap};
        for (int i = 0; i < 4; i++) {
            vertex(vc, pose, Mth.cos(spin + turns[i]) * outer, length, Mth.sin(spin + turns[i]) * outer, r, g, b, us[i], vs[i]);
        }
        ms.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int r, int g, int b, float u, float v) {
        vc.addVertex(pose, x, y, z).setColor(r, g, b, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
