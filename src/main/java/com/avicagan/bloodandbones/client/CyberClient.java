package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.cyber.Module;
import com.avicagan.bloodandbones.cyber.ModuleActions;
import com.avicagan.bloodandbones.cyber.Modules;
import com.avicagan.bloodandbones.cyber.Throttle;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBSounds;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The throttle on the client: the two keys (hold to spool the chosen module, tap to choose the next), the
 * gauge beside the crosshair, the rising whine and the glow for anyone spooling, the hover and reel that
 * move the player's own body, and the grappling line.
 */
@EventBusSubscriber(modid = BloodAndBones.MOD_ID, value = Dist.CLIENT)
public final class CyberClient {
    public static final String CATEGORY = "key.categories.bloodandbones";
    public static final KeyMapping THROTTLE = new KeyMapping("key.bloodandbones.throttle", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping NEXT_MODULE = new KeyMapping("key.bloodandbones.next_module", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    /** Someone spooling a module: which, and since what game time. */
    record Spool(BodyPart part, Module module, long start) {
        float level(long now) {
            return Throttle.level(now - start);
        }
    }

    /** A hover, reel or tether seen from here. */
    record Motion(int kind, long start, long until, int anchorEntity, Vec3 anchor, double speed) {
    }

    private static final Map<Integer, Spool> SPOOLS = new HashMap<>();
    private static final Map<Integer, Motion> MOTIONS = new HashMap<>();
    private static final Map<Integer, Motion> TETHERS = new HashMap<>();
    private static final Map<Integer, SpoolSound> SOUNDS = new HashMap<>();
    /** The module the throttle key drives: its limb and slot. */
    @Nullable
    private static BodyPart selectedPart;
    private static int selectedSlot;
    private static boolean held;

    private CyberClient() {
    }

    public static void receive(Throttle.SyncPayload payload) {
        if (payload.on()) {
            SPOOLS.put(payload.entity(), new Spool(payload.part(), payload.module(), payload.start()));
        } else {
            SPOOLS.remove(payload.entity());
        }
    }

    public static void receive(ModuleActions.MotionPayload payload) {
        Motion motion = new Motion(payload.kind(), payload.start(), payload.until(), payload.anchorEntity(),
                new Vec3(payload.x(), payload.y(), payload.z()), payload.speed());
        switch (payload.kind()) {
            case ModuleActions.MotionPayload.ENDED -> {
                MOTIONS.remove(payload.entity());
                TETHERS.remove(payload.entity());
            }
            case ModuleActions.MotionPayload.TETHER -> TETHERS.put(payload.entity(), motion);
            default -> {
                MOTIONS.put(payload.entity(), motion);
                if (payload.kind() == ModuleActions.MotionPayload.REEL) {
                    TETHERS.put(payload.entity(), motion);
                }
            }
        }
    }

    /** How far this entity has the throttle spooled now, 0 if not at all; and on which limb. */
    public static float level(Entity entity, BodyPart part) {
        Spool spool = SPOOLS.get(entity.getId());
        return spool == null || spool.part() != part ? 0.0F : spool.level(entity.level().getGameTime());
    }

    /** The modules the throttle can drive for the player here, and the chosen one. */
    @Nullable
    static Modules.Fitted selected(Player player) {
        List<Modules.Fitted> driven = Modules.driven(player);
        if (driven.isEmpty()) {
            return null;
        }
        for (Modules.Fitted fitted : driven) {
            if (fitted.part() == selectedPart && fitted.slot() == selectedSlot) {
                return fitted;
            }
        }
        Modules.Fitted first = driven.get(0);
        selectedPart = first.part();
        selectedSlot = first.slot();
        return first;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            SPOOLS.clear();
            MOTIONS.clear();
            TETHERS.clear();
            held = false;
            return;
        }
        while (NEXT_MODULE.consumeClick()) {
            List<Modules.Fitted> driven = Modules.driven(player);
            Modules.Fitted now = selected(player);
            if (now != null && !held) {
                Modules.Fitted next = driven.get((driven.indexOf(now) + 1) % driven.size());
                selectedPart = next.part();
                selectedSlot = next.slot();
                player.displayClientMessage(Component.translatable("bloodandbones.throttle.selected", Component.translatable(next.module().translationKey())), true);
            }
        }
        boolean down = THROTTLE.isDown() && mc.screen == null;
        if (down && !held) {
            Modules.Fitted fitted = selected(player);
            if (fitted != null) {
                held = true;
                PacketDistributor.sendToServer(new Throttle.KeyPayload(true, fitted.part(), fitted.slot()));
            }
        } else if (!down && held) {
            held = false;
            PacketDistributor.sendToServer(new Throttle.KeyPayload(false, BodyPart.RIGHT_ARM, 0));
        }
        long now = mc.level.getGameTime();
        // a whine for everyone spooling, rising with the spool; soul fire off a limb near the top
        for (Map.Entry<Integer, Spool> e : SPOOLS.entrySet()) {
            Entity entity = mc.level.getEntity(e.getKey());
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            SOUNDS.computeIfAbsent(e.getKey(), id -> {
                SpoolSound sound = new SpoolSound(living);
                mc.getSoundManager().play(sound);
                return sound;
            });
            float level = e.getValue().level(now);
            if (level > 0.5F && mc.level.random.nextFloat() < level * 0.6F) {
                Vec3 at = limbEnd(living, 1.0F, e.getValue().part());
                mc.level.addParticle(level > 0.9F ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMOKE, at.x, at.y, at.z, 0.0, 0.03, 0.0);
            }
        }
        SOUNDS.entrySet().removeIf(e -> !SPOOLS.containsKey(e.getKey()) || e.getValue().isStopped());
        MOTIONS.entrySet().removeIf(e -> now >= e.getValue().until() + 20);
        TETHERS.entrySet().removeIf(e -> now >= e.getValue().until());
        // vent puffs under anyone hovering
        for (Map.Entry<Integer, Motion> e : MOTIONS.entrySet()) {
            if (e.getValue().kind() == ModuleActions.MotionPayload.HOVER && mc.level.getEntity(e.getKey()) instanceof LivingEntity living && now % 2 == 0) {
                mc.level.addParticle(ParticleTypes.CLOUD, living.getX() + (mc.level.random.nextDouble() - 0.5) * 0.4, living.getY() - 0.1,
                        living.getZ() + (mc.level.random.nextDouble() - 0.5) * 0.4, 0.0, -0.15, 0.0);
            }
        }
    }

    /** The player's own hover or reel, moved here where the player's movement is worked out. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide || player != Minecraft.getInstance().player) {
            return;
        }
        Motion motion = MOTIONS.get(player.getId());
        if (motion == null || motion.kind() == ModuleActions.MotionPayload.TETHER) {
            return;
        }
        Vec3 anchor = motion.anchorEntity() >= 0 && player.level().getEntity(motion.anchorEntity()) instanceof Entity e
                ? e.getBoundingBox().getCenter() : motion.anchor();
        boolean hover = motion.kind() == ModuleActions.MotionPayload.HOVER;
        if (hover && player.level().getGameTime() <= motion.start() + 1) {
            // the server's puff of lift may not have landed yet
            player.setDeltaMovement(player.getDeltaMovement().x, Math.max(player.getDeltaMovement().y, ModuleActions.HOVER_LIFT), player.getDeltaMovement().z);
        }
        if (!ModuleActions.move(player, hover, motion.start(), motion.until(), anchor, motion.speed())) {
            MOTIONS.remove(player.getId());
        }
    }

    /** The grappling line: from the arm to what it caught, flying out at first. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || TETHERS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack ms = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.leash());
        long now = mc.level.getGameTime();
        for (Map.Entry<Integer, Motion> e : TETHERS.entrySet()) {
            if (!(mc.level.getEntity(e.getKey()) instanceof LivingEntity owner)) {
                continue;
            }
            Motion motion = e.getValue();
            Vec3 from = limbEnd(owner, partial, BodyPart.arm(owner.getMainArm()));
            Vec3 to = motion.anchorEntity() >= 0 && mc.level.getEntity(motion.anchorEntity()) instanceof Entity target
                    ? target.getPosition(partial).add(0.0, target.getBbHeight() * 0.5, 0.0) : motion.anchor();
            // the hook flies out at three blocks a tick
            double out = Math.min(1.0, (now - motion.start() + partial) * 3.0 / Math.max(0.1, to.distanceTo(from)));
            to = from.add(to.subtract(from).scale(out));
            strip(ms, lines, from.subtract(camera), to.subtract(camera));
        }
        buffers.endBatch(RenderType.leash());
    }

    /** A thin dark cable, drawn as a leash strip is: two crossed ribbons of segments. */
    private static void strip(PoseStack ms, VertexConsumer vc, Vec3 from, Vec3 to) {
        var pose = ms.last().pose();
        Vec3 d = to.subtract(from);
        int segments = 24;
        for (int pass = 0; pass < 2; pass++) {
            Vec3 across = pass == 0 ? d.cross(new Vec3(0, 1, 0)) : d.cross(d.cross(new Vec3(0, 1, 0)));
            across = across.lengthSqr() < 1.0E-6 ? new Vec3(0.025, 0, 0) : across.normalize().scale(0.025);
            for (int i = 0; i <= segments; i++) {
                float t = i / (float) segments;
                // a little sag in the middle
                Vec3 p = from.add(d.scale(t)).add(0.0, -Math.sin(t * Math.PI) * 0.15, 0.0);
                int shade = i % 2 == 0 ? 0xFF3A2A1A : 0xFF6B4A2A;
                vc.addVertex(pose, (float) (p.x - across.x), (float) (p.y - across.y), (float) (p.z - across.z)).setColor(shade).setLight(0xF000F0);
                vc.addVertex(pose, (float) (p.x + across.x), (float) (p.y + across.y), (float) (p.z + across.z)).setColor(shade).setLight(0xF000F0);
            }
        }
    }

    /** The gauge: the chosen module, a dial whose needle climbs with the spool, and the soul blood left. */
    public static void renderGauge(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator()) {
            return;
        }
        Modules.Fitted fitted = selected(player);
        if (fitted == null) {
            return;
        }
        Spool spool = SPOOLS.get(player.getId());
        float level = spool == null ? 0.0F : spool.level(player.level().getGameTime());
        int x = graphics.guiWidth() / 2 + 14;
        int y = graphics.guiHeight() / 2 - 4;
        // the module being spooled, or the one the key would spool
        ItemStack icon = new ItemStack(com.avicagan.bloodandbones.registry.BBItems.module(spool != null ? spool.module() : fitted.module()));
        graphics.renderItem(icon, x, y - 4);
        // the dial: a half circle of ticks, cool to hot, and the needle
        int cx = x + 32;
        int cy = y + 10;
        for (int i = 0; i <= 12; i++) {
            float t = i / 12.0F;
            double a = Math.PI * (1.0 - t);
            int tx = cx + (int) Math.round(Math.cos(a) * 11);
            int ty = cy - (int) Math.round(Math.sin(a) * 11);
            graphics.fill(tx, ty, tx + 1, ty + 1, 0xFF000000 | heat(t));
        }
        double needle = Math.PI * (1.0 - level);
        for (int r = 0; r <= 9; r++) {
            int nx = cx + (int) Math.round(Math.cos(needle) * r);
            int ny = cy - (int) Math.round(Math.sin(needle) * r);
            graphics.fill(nx, ny, nx + 1, ny + 1, spool == null ? 0xFF8A8A8A : 0xFFFFFFFF);
        }
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFB08A3E);
        // the tank: soul blood left, as a bar under the dial
        var fluid = FluidBacktankItem.fluid(FluidBacktankItem.wornBy(player));
        int capacity = FluidBacktankItem.capacity(FluidBacktankItem.wornBy(player));
        float full = capacity <= 0 || !fluid.is(BBFluids.soulBlood()) ? 0.0F : fluid.getAmount() / (float) capacity;
        graphics.fill(cx - 12, cy + 4, cx + 13, cy + 7, 0xFF1A1A1A);
        graphics.fill(cx - 12, cy + 4, cx - 12 + Math.round(25 * full), cy + 7, 0xFF4FD1C5);
        if (spool != null) {
            graphics.drawString(mc.font, Math.round(level * 100) + "%", cx + 16, cy - 6, 0xFF000000 | heat(level), true);
        }
    }

    /** How far the Analytical Lens sees through walls. */
    public static final double LENS_RANGE = 24.0;

    /**
     * The Analytical Lens: the first machine along the look, through anything in the way, with its speed,
     * its network's stress against capacity, and whatever Create's goggles would say of it. (Looking straight
     * at a machine, Create's own goggle overlay shows it: the lens counts as goggles.)
     */
    public static void renderLens(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui || mc.screen != null || !Modules.has(player, Module.ANALYTICAL_LENS)) {
            return;
        }
        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && mc.level.getBlockEntity(hit.getBlockPos()) instanceof com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        Vec3 eye = player.getEyePosition(partial);
        Vec3 look = player.getViewVector(partial);
        net.minecraft.core.BlockPos last = null;
        net.minecraft.world.level.block.entity.BlockEntity found = null;
        double distance = 0.0;
        for (double d = 0.5; d <= LENS_RANGE; d += 0.2) {
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(eye.add(look.scale(d)));
            if (pos.equals(last)) {
                continue;
            }
            last = pos;
            net.minecraft.world.level.block.entity.BlockEntity be = mc.level.getBlockEntity(pos);
            if (be instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity || be instanceof com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation) {
                found = be;
                distance = d;
                break;
            }
        }
        if (found == null) {
            return;
        }
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("bloodandbones.lens.seen", found.getBlockState().getBlock().getName(), (int) Math.round(distance))
                .withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
        if (found instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity kinetic) {
            lines.add(Component.translatable("bloodandbones.lens.speed", Math.round(Math.abs(kinetic.getSpeed()))).withStyle(net.minecraft.ChatFormatting.GRAY));
            var network = (com.avicagan.bloodandbones.mixin.KineticBlockEntityAccessor) kinetic;
            if (network.bloodandbones$capacity() > 0.0F) {
                lines.add(Component.translatable("bloodandbones.lens.stress", Math.round(network.bloodandbones$stress()), Math.round(network.bloodandbones$capacity()))
                        .withStyle(kinetic.isOverStressed() ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GRAY));
            }
            if (kinetic.isOverStressed()) {
                lines.add(Component.translatable("bloodandbones.lens.overstressed").withStyle(net.minecraft.ChatFormatting.RED));
            }
        }
        if (found instanceof com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation info) {
            List<Component> more = new java.util.ArrayList<>();
            if (info.addToGoggleTooltip(more, player.isShiftKeyDown())) {
                lines.addAll(more);
            }
        }
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, mc.font.width(line));
        }
        // left of the crosshair, clear of the gauge and of Create's own overlay on the right
        graphics.renderComponentTooltip(mc.font, lines, graphics.guiWidth() / 2 - 24 - width, graphics.guiHeight() / 2 + 12);
    }

    /** From soul-cyan at the bottom of the throttle, through yellow, to angry red at the top. */
    static int heat(float t) {
        int from = t < 0.5F ? 0x30E0FF : 0xFFD030;
        int to = t < 0.5F ? 0xFFD030 : 0xFF2010;
        float f = t < 0.5F ? t * 2.0F : (t - 0.5F) * 2.0F;
        int r = (int) Mth.lerp(f, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int g = (int) Mth.lerp(f, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int b = (int) Mth.lerp(f, from & 0xFF, to & 0xFF);
        return r << 16 | g << 8 | b;
    }

    /** The whine of a module spooling, pitched up as the spool climbs; it follows whoever is spooling. */
    static final class SpoolSound extends AbstractTickableSoundInstance {
        private final LivingEntity entity;

        SpoolSound(LivingEntity entity) {
            super(BBSounds.CYBERNETIC_SPOOL.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.entity = entity;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.2F;
            this.pitch = 0.5F;
            this.x = entity.getX();
            this.y = entity.getY();
            this.z = entity.getZ();
        }

        @Override
        public void tick() {
            Spool spool = SPOOLS.get(entity.getId());
            if (spool == null || entity.isRemoved()) {
                stop();
                return;
            }
            float level = spool.level(entity.level().getGameTime());
            pitch = 0.5F + 1.5F * level;
            volume = 0.25F + 0.55F * level;
            x = entity.getX();
            y = entity.getY() + 1.0;
            z = entity.getZ();
        }
    }

    /**
     * Where the coupler's rod leaves the arm: the main hand in third person; seen from your own eyes, where
     * the first-person arm is drawn (low and to the side, a little ahead), so the rod does not fill the view.
     */
    public static Vec3 rodStart(LivingEntity owner, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (owner == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
            Vec3 look = owner.getViewVector(partial);
            Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize().scale(owner.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1.0 : -1.0);
            return owner.getEyePosition(partial).add(look.scale(0.9)).add(right.scale(0.45)).add(0.0, -0.45, 0.0);
        }
        return limbEnd(owner, partial, BodyPart.arm(owner.getMainArm()));
    }

    /**
     * Roughly where a limb ends on a body, for particles, lines and the coupler's shaft: an arm's hand at
     * shoulder height, off to its side and reaching forward; a leg's foot; an eye at the face.
     */
    public static Vec3 limbEnd(LivingEntity owner, float partial, BodyPart part) {
        Vec3 pos = owner.getPosition(partial);
        float yaw = (float) Math.toRadians(Mth.rotLerp(partial, owner.yBodyRotO, owner.yBodyRot));
        Vec3 left = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
        boolean right = part == BodyPart.RIGHT_ARM || part == BodyPart.RIGHT_LEG || part == BodyPart.RIGHT_EYE;
        double side = right ? -1.0 : 1.0;
        return switch (part.kind()) {
            case LEG -> pos.add(left.scale(0.12 * side)).add(0.0, 0.1, 0.0);
            case ARM -> pos.add(0.0, owner.getBbHeight() * 0.72, 0.0).add(left.scale(0.35 * side))
                    .add(Vec3.directionFromRotation(0.0F, owner.getViewYRot(partial)).scale(0.4));
            default -> owner.getEyePosition(partial);
        };
    }
}
