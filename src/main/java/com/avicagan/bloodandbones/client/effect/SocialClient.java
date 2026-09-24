package com.avicagan.bloodandbones.client.effect;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.config.BBClientConfig;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.effect.AlertPayload;
import com.avicagan.bloodandbones.parts.effect.GlowEffect;
import com.avicagan.bloodandbones.parts.effect.SensePayload;
import com.avicagan.bloodandbones.registry.BBParticles;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Social's client side ({@link com.avicagan.bloodandbones.parts.effect.SocialEffects}): the reveal and echolocate
 * outlines, alert pings, the glow layer. Never loaded on a dedicated server: only the client branch of the mod's
 * constructor calls {@link #init}, and a client-bound payload's handler reaches it from inside its lambda.
 * <ul>
 *     <li>Outlines: what the player's senses pick out ({@link SensePayload}) is outlined through walls for them alone,
 *     each sense in its own colour; echolocation clicks wetly and its echoes come back from what it found, and blood
 *     scent shows its wounded dripping (grey sparks in bloodless mode).</li>
 *     <li>Alerts ({@link AlertPayload}): a thump of a heartbeat and a line saying who has its eye on you, with an arrow
 *     for which way to look, as a subtitle has.</li>
 *     <li>Glow: {@link CarcassGlowLayer} on players; a glowing minion drawn at full brightness ({@link #minionLight}).</li>
 * </ul>
 */
public final class SocialClient {
    /** One outlined creature: what sensed it, and from when to when (game time). */
    private record Outline(String kind, long start, long until) {
    }

    /** One alert shown: who took aim (followed while the client has it), where it was, its name, and when. */
    private record Alert(int entity, Vec3 at, Component name, long start) {
    }

    /** An echo on its way back to the player: where from, and when it arrives. */
    private record Echo(Vec3 from, long at, float pitch) {
    }

    private static final Map<Integer, Outline> OUTLINES = new ConcurrentHashMap<>();
    private static final List<Alert> ALERTS = new CopyOnWriteArrayList<>();
    private static final List<Echo> ECHOES = new CopyOnWriteArrayList<>();
    /** How long an alert stays on screen. */
    private static final int ALERT_TICKS = 70;
    /** How many alerts show at once, the newest. */
    private static final int MOST_ALERTS = 4;

    /** Lines drawn over everything, walls included: the senses' outlines. Made the first time one is drawn. */
    private static final class Outlines {
        /** Whether the depth test was on before the outlines turned it off, to put it back as it was. */
        private static boolean depthWasOn;
        /**
         * Vanilla's "no depth test" only leaves the test alone, trusting it is off already; after the particles it is
         * not, so this turns it off for the outlines itself.
         */
        private static final RenderStateShard.LayeringStateShard THROUGH_ANYTHING = new RenderStateShard.LayeringStateShard(
                BloodAndBones.MOD_ID + "_through_walls", () -> {
                    depthWasOn = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
                    RenderSystem.disableDepthTest();
                }, () -> {
                    if (depthWasOn) {
                        RenderSystem.enableDepthTest();
                    }
                });
        static final RenderType THROUGH_WALLS = RenderType.create(BloodAndBones.MOD_ID + ":sense_outline", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 1536, RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                        .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.5)))
                        .setLayeringState(THROUGH_ANYTHING)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }

    private SocialClient() {
    }

    /**
     * Called once, from the mod's constructor on a client. Mod-bus events (renderers, layers, particles, key mappings)
     * go on {@code modBus}; game events (ticks, rendering the level, input) on {@code NeoForge.EVENT_BUS}.
     */
    public static void init(IEventBus modBus) {
        modBus.addListener(SocialClient::onAddLayers);
        modBus.addListener(SocialClient::onGuiLayers);
        NeoForge.EVENT_BUS.addListener(SocialClient::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(SocialClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(SocialClient::onLoggingOut);
    }

    // ---- glow

    /** Carcass armour's glow layer, on players of both arm shapes. */
    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new CarcassGlowLayer<AbstractClientPlayer, net.minecraft.client.model.PlayerModel<AbstractClientPlayer>>(renderer,
                        event.getEntityModels()));
            }
        }
    }

    /** The light a minion is drawn in: full brightness if it glows, whatever the dark round it. */
    public static int minionLight(MinionEntity minion, int light) {
        return GlowEffect.of(minion) != null ? LightTexture.FULL_BRIGHT : light;
    }

    // ---- what the server sends

    /** A sweep of the player's senses: outline what it found; echolocation clicks, and its echoes come back. */
    public static void receive(SensePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        long now = mc.level.getGameTime();
        for (int id : payload.entities()) {
            OUTLINES.merge(id, new Outline(payload.kind(), now, now + payload.ticks()),
                    (old, fresh) -> old.kind().equals(fresh.kind()) && old.until() > now ? new Outline(old.kind(), old.start(), fresh.until()) : fresh);
        }
        if ("echolocate".equals(payload.kind())) {
            Player player = mc.player;
            // a wet click from the throat, then the nearest few echo back, the further the later
            mc.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.3F, 1.9F, false);
            mc.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.35F, 1.7F, false);
            List<Entity> found = new ArrayList<>();
            for (int id : payload.entities()) {
                Entity entity = mc.level.getEntity(id);
                if (entity != null) {
                    found.add(entity);
                }
            }
            found.sort(Comparator.comparingDouble(player::distanceToSqr));
            for (int i = 0; i < Math.min(4, found.size()); i++) {
                Entity entity = found.get(i);
                ECHOES.add(new Echo(entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0), now + 2 + (long) (player.distanceTo(entity) / 2.5F),
                        1.5F - i * 0.08F));
            }
        }
    }

    /** Something has its eye on the player: a heartbeat's thump, and a line saying who and which way. */
    public static void receive(AlertPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Entity entity = mc.level.getEntity(payload.entity());
        Component name = entity != null ? entity.getDisplayName() : Component.translatable(payload.name());
        ALERTS.removeIf(a -> a.entity() == payload.entity());
        ALERTS.add(new Alert(payload.entity(), new Vec3(payload.x(), payload.y(), payload.z()), name, mc.level.getGameTime()));
        while (ALERTS.size() > MOST_ALERTS) {
            ALERTS.removeFirst();
        }
        Player player = mc.player;
        mc.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.9F, 1.35F, false);
        mc.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.35F, 0.6F, false);
    }

    // ---- every tick

    /** Old outlines and alerts go; echoes arrive; the wounded that blood scent picks out drip (or spark, bloodless). */
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        long now = mc.level.getGameTime();
        OUTLINES.values().removeIf(o -> o.until() < now || o.start() > now + 200);
        ALERTS.removeIf(a -> now - a.start() > ALERT_TICKS || a.start() > now);
        for (Echo echo : ECHOES) {
            if (now >= echo.at()) {
                ECHOES.remove(echo);
                mc.level.playLocalSound(echo.from().x, echo.from().y, echo.from().z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.2F, echo.pitch(), false);
            }
        }
        boolean bloodless = BBClientConfig.bloodless();
        for (Map.Entry<Integer, Outline> e : OUTLINES.entrySet()) {
            if (!"reveal".equals(e.getValue().kind()) || (now + e.getKey()) % 6 != 0) {
                continue;
            }
            if (mc.level.getEntity(e.getKey()) instanceof LivingEntity living && living.getHealth() < living.getMaxHealth() * 0.5F && !living.isInvisible()) {
                double x = living.getRandomX(0.6);
                double y = living.getY() + living.getBbHeight() * (0.3 + mc.level.random.nextDouble() * 0.5);
                double z = living.getRandomZ(0.6);
                if (bloodless) {
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, -0.02, 0.0);
                } else {
                    mc.level.addParticle(BBParticles.BLOOD_DROP.get(), x, y, z, 0.0, -0.05, 0.0);
                }
            }
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        OUTLINES.clear();
        ALERTS.clear();
        ECHOES.clear();
    }

    // ---- drawing

    /** The outlines, drawn over everything so walls hide nothing, each sense in its own colour. */
    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || OUTLINES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = mc.level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack ms = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(Outlines.THROUGH_WALLS);
        boolean bloodless = BBClientConfig.bloodless();
        for (Map.Entry<Integer, Outline> e : OUTLINES.entrySet()) {
            Entity entity = mc.level.getEntity(e.getKey());
            Outline outline = e.getValue();
            if (entity == null || entity == mc.player || entity.isRemoved()) {
                continue;
            }
            Vec3 at = entity.getPosition(partial);
            AABB box = entity.getBoundingBox().move(at.subtract(entity.position())).move(camera.scale(-1.0));
            float left = Mth.clamp((outline.until() - now) / 10.0F, 0.0F, 1.0F);
            int colour = colour(outline.kind(), bloodless);
            float r = FastColor.ARGB32.red(colour) / 255.0F;
            float g = FastColor.ARGB32.green(colour) / 255.0F;
            float b = FastColor.ARGB32.blue(colour) / 255.0F;
            float age = now - outline.start();
            if ("echolocate".equals(outline.kind())) {
                // the ping: a shell that shrinks onto the creature as it comes back, then fades with the rest
                float arrive = Mth.clamp(age / 8.0F, 0.0F, 1.0F);
                if (arrive < 1.0F) {
                    LevelRenderer.renderLineBox(ms, lines, box.inflate((1.0F - arrive) * 0.8F), r, g, b, 0.6F * arrive);
                }
                LevelRenderer.renderLineBox(ms, lines, box, r, g, b, 0.85F * left * (1.0F - 0.5F * Mth.clamp(age / 60.0F, 0.0F, 1.0F)));
            } else if ("tremor".equals(outline.kind())) {
                // a tremor shivers
                float shake = Mth.sin(now * 1.7F + e.getKey()) * 0.03F;
                LevelRenderer.renderLineBox(ms, lines, box.move(shake, 0.0, -shake), r, g, b, 0.8F * left);
            } else {
                LevelRenderer.renderLineBox(ms, lines, box, r, g, b, 0.8F * left);
            }
        }
        buffers.endBatch(Outlines.THROUGH_WALLS);
    }

    /** Each sense's colour: pale for echoes, sculk teal for tremors, blood red for reveal (grey, bloodless), violet for the unseen. */
    private static int colour(String kind, boolean bloodless) {
        return switch (kind) {
            case "echolocate" -> 0xD8E6F0;
            case "tremor" -> 0x2FB8A6;
            case "reveal" -> bloodless ? 0xA8A8A8 : 0xC0101C;
            case "see_invisible" -> 0xC9B6FF;
            default -> 0xFFFFFF;
        };
    }

    private static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.SUBTITLE_OVERLAY, BloodAndBones.asResource("alerts"), SocialClient::renderAlerts);
    }

    /**
     * The alerts, above the hotbar on the right as subtitles are: an arrow for which way to look (turned as you turn,
     * following the mob while the client has it), and who.
     */
    private static void renderAlerts(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (ALERTS.isEmpty() || player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float now = mc.level.getGameTime() + partial;
        int y = graphics.guiHeight() - 72;
        for (int i = ALERTS.size() - 1; i >= 0; i--) {
            Alert alert = ALERTS.get(i);
            Entity entity = mc.level.getEntity(alert.entity());
            Vec3 at = entity != null ? entity.getPosition(partial).add(0.0, entity.getBbHeight() * 0.8, 0.0) : alert.at();
            Component line = Component.literal(arrow(player, at, partial) + " ").append(Component.translatable("bloodandbones.sense.alert", alert.name()));
            float left = Mth.clamp((ALERT_TICKS - (now - alert.start())) / 20.0F, 0.0F, 1.0F);
            int alpha = Math.max(8, (int) (255 * left));
            int width = mc.font.width(line);
            int x = graphics.guiWidth() - width - 8;
            graphics.fill(x - 3, y - 2, x + width + 3, y + 10, FastColor.ARGB32.color((int) (alpha * 0.6F), 16, 4, 4));
            graphics.drawString(mc.font, line, x, y, FastColor.ARGB32.color(alpha, 0xE8, 0x4A, 0x4A), false);
            y -= 14;
        }
    }

    /** Which way to look for it: ahead, behind, to the left or the right of where the player faces. */
    private static String arrow(Player player, Vec3 at, float partial) {
        Vec3 to = at.subtract(player.getEyePosition(partial));
        float towards = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
        float turn = Mth.wrapDegrees(towards - player.getViewYRot(partial));
        if (Math.abs(turn) <= 30.0F) {
            return "▲";
        }
        if (Math.abs(turn) >= 150.0F) {
            return "▼";
        }
        return turn > 0.0F ? "▶" : "◀";
    }
}
