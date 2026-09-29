package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity;
import com.avicagan.bloodandbones.gametest.MultiplayerCheck;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

import java.util.Locale;

/**
 * The client half of the two-client check (see gametest/MultiplayerCheck), off unless a client runs with
 * {@code -Dbloodandbones.multiplayer=butcher} or {@code =watcher} (the {@code runMpButcher} and {@code runMpWatcher}
 * runs). Each joins the server named by {@code -Dbloodandbones.multiplayer.server} (localhost:25565 unless given),
 * waiting and retrying until it is up, and follows the server's clock. The Butcher kills a cow with the Meat Hook,
 * hooks a hind leg, walks it to the Shackle Hook, hangs it, cuts a front leg off with the Cleaver and skins it with the
 * Flensing Knife, all with real clicks aimed at what its own client shows. The Watcher photographs it into
 * screenshots/mp_*.png and writes where it draws every carcass body, each tick and each frame, to compare with where
 * the server has them. Both quit at the end.
 */
public final class MultiplayerShowcase {
    private static final String ROLE = System.getProperty(MultiplayerCheck.PROPERTY, "");
    private static final String ADDRESS = System.getProperty(MultiplayerCheck.PROPERTY + ".server", "localhost:25565");
    private static final boolean BUTCHER = "butcher".equals(ROLE);

    private static long start = -1;
    private static BlockPos origin;
    private static int waiting;
    private static int step;
    private static int tries;
    private static long lastTry = -100;
    private static boolean quit;

    private MultiplayerShowcase() {
    }

    public static void init() {
        if (BUTCHER || "watcher".equals(ROLE)) {
            NeoForge.EVENT_BUS.addListener(MultiplayerShowcase::tick);
            NeoForge.EVENT_BUS.addListener(MultiplayerShowcase::chat);
            if (!BUTCHER) {
                NeoForge.EVENT_BUS.addListener(MultiplayerShowcase::frame);
            }
            BloodAndBones.LOGGER.info("[mp] {} client enabled, server {}", ROLE, ADDRESS);
        }
    }

    /** The server's word that the clock has started, and where; kept off the screen. */
    private static void chat(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (text.startsWith(MultiplayerCheck.SIGNAL)) {
            // "<start> <x> <y> <z>": the tick the clock starts at, and the spot the Butcher stands on
            String[] parts = text.substring(MultiplayerCheck.SIGNAL.length()).trim().split(" ");
            start = Long.parseLong(parts[0]);
            origin = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            BloodAndBones.LOGGER.info("[mp] {} told to start at {} around {}", ROLE, start, origin);
            event.setCanceled(true);
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (quit) {
            return;
        }
        if (mc.level == null || mc.player == null) {
            // on the title screen (or back on it after the server turned us away): try again every few seconds
            if (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen || mc.screen instanceof DisconnectedScreen) {
                if (++waiting > 100) {
                    waiting = 0;
                    BloodAndBones.LOGGER.info("[mp] {} connecting to {}", ROLE, ADDRESS);
                    ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(ADDRESS),
                            new ServerData("Blood & Bones check", ADDRESS, ServerData.Type.OTHER), false, null);
                }
            }
            return;
        }
        mc.options.pauseOnLostFocus = false;
        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        if (!BUTCHER) {
            mc.options.hideGui = true;
        }
        if (start < 0) {
            return;
        }
        long t = mc.level.getGameTime() - start;
        if (BUTCHER) {
            butcher(mc, t);
        } else {
            watcher(mc, t);
        }
        if (t >= MultiplayerCheck.END - 40) {
            quit = true;
            BloodAndBones.LOGGER.info("[mp] {} done", ROLE);
            mc.stop();
        }
    }

    // ---- the Butcher: real clicks, aimed at what this client draws

    private static void butcher(Minecraft mc, long t) {
        if (t >= 0 && t <= MultiplayerCheck.END) {
            BloodAndBones.LOGGER.info(String.format(Locale.ROOT, "[mp] butcher at t=%d time=%d %.3f,%.3f,%.3f yaw=%.1f step=%d", t, mc.level.getGameTime(),
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), step));
        }
        switch (step) {
            case 0 -> {
                // kill the cow with the Meat Hook, a blow at a time
                if (t < 40 || t - lastTry < 20) {
                    return;
                }
                Cow cow = mc.level.getEntitiesOfClass(Cow.class, mc.player.getBoundingBox().inflate(8.0)).stream().filter(Cow::isAlive).findFirst().orElse(null);
                if (cow == null || tries >= 6) {
                    BloodAndBones.LOGGER.info("[mp] butcher: cow down after {} blows", tries);
                    next();
                    return;
                }
                select(mc, 0);
                face(mc, cow.getBoundingBox().getCenter());
                mc.gameMode.attack(mc.player, cow);
                mc.player.swing(InteractionHand.MAIN_HAND);
                lastTry = t;
                tries++;
            }
            case 1 -> {
                // hook a hind leg, while it still lies loose (folded to rest, only its torso can be hooked)
                if (t < 95 || t - lastTry < 5) {
                    return;
                }
                lastTry = t;
                if (tries++ > 20) {
                    BloodAndBones.LOGGER.warn("[mp] butcher: could not hook the cow");
                    next();
                    return;
                }
                ClientSubLevel leg = part(mc, "hind_leg");
                if (leg == null) {
                    leg = part(mc, "body");
                }
                if (leg != null && click(mc, leg.logicalPose().position(), true)) {
                    shot(mc, "mp_butcher_hooked.png");
                    next();
                }
            }
            case 2 -> {
                // walk it over near the hook, facing it the whole way as the Meat Hook holds it in front (step back and
                // sideways, not forward: walking forward, you walk into your own carcass)
                Vec3 to = Vec3.atBottomCenterOf(origin.offset(6, 0, 0));
                Vec3 from = mc.player.position();
                double left = Math.hypot(to.x - from.x, to.z - from.z);
                ClientSubLevel torso = part(mc, "body");
                if (left < 0.4 || t > 240) {
                    mc.player.input = new net.minecraft.client.player.KeyboardInput(mc.options);
                    BloodAndBones.LOGGER.info("[mp] butcher: walked to the hook, {} blocks short, at {}", left, t);
                    next();
                    return;
                }
                if (torso != null) {
                    face(mc, new Vec3(torso.logicalPose().position().x(), torso.logicalPose().position().y(), torso.logicalPose().position().z()));
                }
                // the way to go, turned into the player's own frame
                double yaw = Math.toRadians(mc.player.getYRot());
                double dx = (to.x - from.x) / left;
                double dz = (to.z - from.z) / left;
                Walk walk = mc.player.input instanceof Walk w ? w : new Walk();
                walk.forward = (float) (-dx * Math.sin(yaw) + dz * Math.cos(yaw));
                walk.strafe = (float) (dx * Math.cos(yaw) + dz * Math.sin(yaw));
                mc.player.input = walk;
            }
            case 3 -> {
                // hang it: the Meat Hook on the Shackle Hook
                if (t < 250 || t - lastTry < 10) {
                    return;
                }
                lastTry = t;
                if (tries++ > 10) {
                    BloodAndBones.LOGGER.warn("[mp] butcher: could not reach the Shackle Hook");
                    next();
                    return;
                }
                if (click(mc, Vec3.atCenterOf(origin.offset(6, 4, 3)), false)) {
                    next();
                }
            }
            case 4, 5, 6 -> {
                // three Cleaver strokes through a front leg's joint
                if (t < 340 + (step - 4) * 20L || t - lastTry < 5) {
                    return;
                }
                lastTry = t;
                select(mc, 1);
                ClientSubLevel leg = part(mc, "front_leg");
                if (tries++ > 12 || leg != null && click(mc, leg.logicalPose().position(), true)) {
                    if (step == 6) {
                        shot(mc, "mp_butcher_cut.png");
                    }
                    next();
                }
            }
            case 7, 8, 9, 10 -> {
                // four Flensing Knife strokes down the body
                if (t < 440 + (step - 7) * 12L || t - lastTry < 4) {
                    return;
                }
                lastTry = t;
                select(mc, 2);
                ClientSubLevel torso = part(mc, "body");
                if (tries++ > 12 || torso != null && click(mc, torso.logicalPose().position(), true)) {
                    next();
                }
            }
            case 11 -> {
                if (t >= 530) {
                    shot(mc, "mp_butcher_skinned.png");
                    next();
                }
            }
            default -> {
            }
        }
    }

    private static void next() {
        step++;
        tries = 0;
    }

    /** Walks a set way, whichever way the player faces: forward and sideways, each from -1 to 1. */
    private static final class Walk extends Input {
        float forward;
        float strafe;

        @Override
        public void tick(boolean isSneaking, float sneakingSpeedMultiplier) {
            this.forwardImpulse = forward;
            this.leftImpulse = strafe;
            this.up = forward > 0.1F;
            this.down = forward < -0.1F;
            this.left = strafe > 0.1F;
            this.right = strafe < -0.1F;
        }
    }

    private static void select(Minecraft mc, int slot) {
        mc.player.getInventory().selected = slot;
    }

    private static void face(Minecraft mc, Vec3 at) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 to = at.subtract(eye);
        mc.player.setYRot((float) Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0F);
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z))));
    }

    /**
     * Look at a point and right-click what the crosshair finds there, as a player would.
     *
     * @param carcass whether what it must find is a carcass (else any block)
     */
    private static boolean click(Minecraft mc, Vector3dc at, boolean carcass) {
        return click(mc, new Vec3(at.x(), at.y(), at.z()), carcass);
    }

    private static boolean click(Minecraft mc, Vec3 at, boolean carcass) {
        face(mc, at);
        mc.gameRenderer.pick(1.0F);
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            BloodAndBones.LOGGER.info("[mp] butcher: nothing under the crosshair aiming at {}", at);
            return false;
        }
        boolean onCarcass = mc.level.getBlockEntity(hit.getBlockPos()) instanceof CarcassPartBlockEntity;
        if (carcass && !onCarcass) {
            BloodAndBones.LOGGER.info("[mp] butcher: aimed at {} and found {}", at, mc.level.getBlockState(hit.getBlockPos()));
            return false;
        }
        String what = mc.level.getBlockEntity(hit.getBlockPos()) instanceof CarcassPartBlockEntity part ? part.bone() : mc.level.getBlockState(hit.getBlockPos()).toString();
        BloodAndBones.LOGGER.info("[mp] butcher: clicks {} holding {}", what, mc.player.getMainHandItem().getHoverName().getString());
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /** The body of the whole carcass (not a piece cut off it) whose part name contains this, nearest the player. */
    @Nullable
    private static ClientSubLevel part(Minecraft mc, String name) {
        ClientSubLevelContainer container = SubLevelContainer.getContainer(mc.level);
        if (container == null) {
            return null;
        }
        ClientSubLevel best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SubLevel sub : container.getAllSubLevels()) {
            if (sub instanceof ClientSubLevel body && mc.level.getBlockEntity(body.getPlot().getCenterBlock()) instanceof CarcassPartBlockEntity cell
                    && cell.bone().contains(name)) {
                double d = body.logicalPose().position().distance(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
                if (d < bestDistance) {
                    bestDistance = d;
                    best = body;
                }
            }
        }
        return best;
    }

    // ---- the Watcher: pictures, and where it draws every body

    private static final long[] SHOTS = {70, 95, 270, 320, 420, 520, 680, 740};
    private static final String[] NAMES = {"01_kill", "02_lying", "04_hanging", "05_hung_close", "06_cut", "07_skinned", "08_bleeding", "09_side"};
    /** The drag, a picture every six ticks. */
    private static final long FILM_FROM = 100;
    private static final long FILM_TO = 220;
    private static final long FILM_GAP = 6;
    private static int shot;
    private static long lastFilm = -1;

    private static void watcher(Minecraft mc, long t) {
        if (shot < SHOTS.length && t >= SHOTS[shot]) {
            shot(mc, "mp_watcher_" + NAMES[shot] + ".png");
            shot++;
        }
        if (t >= FILM_FROM && t <= FILM_TO && (lastFilm < 0 || t - lastFilm >= FILM_GAP)) {
            lastFilm = t;
            shot(mc, String.format(Locale.ROOT, "mp_watcher_03_drag_%03d.png", t));
        }
        if (t >= 0 && t <= MultiplayerCheck.END) {
            log(mc, "tick", t, 1.0F);
        }
    }

    /** Every frame the Watcher draws, where it draws each body (for smoothness). */
    private static void frame(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || mc.level == null || start < 0) {
            return;
        }
        long t = mc.level.getGameTime() - start;
        if (t >= FILM_FROM - 20 && t <= FILM_TO + 60) {
            log(mc, "frame", t, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
    }

    private static void log(Minecraft mc, String kind, long t, float partial) {
        ClientSubLevelContainer container = SubLevelContainer.getContainer(mc.level);
        if (container == null) {
            return;
        }
        StringBuilder line = new StringBuilder();
        for (SubLevel sub : container.getAllSubLevels()) {
            if (sub instanceof ClientSubLevel body && mc.level.getBlockEntity(body.getPlot().getCenterBlock()) instanceof CarcassPartBlockEntity cell) {
                Vector3dc p = kind.equals("tick") ? body.logicalPose().position() : body.renderPose(partial).position();
                line.append(String.format(Locale.ROOT, " %s:%s:%.4f,%.4f,%.4f", body.getUniqueId(), cell.bone(), p.x(), p.y(), p.z()));
            }
        }
        String extra = "";
        if (kind.equals("tick")) {
            // the cow while it lives (and while it stands frozen, handing over to its carcass), the rack's blood, the stains
            long cows = mc.level.getEntitiesOfClass(Cow.class, mc.player.getBoundingBox().inflate(32.0)).size();
            int rack = mc.level.getBlockEntity(origin.offset(6, 0, 3)) instanceof com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity tray
                    ? tray.getFluid().getAmount() : -1;
            extra = " cows=" + cows + " rack=" + rack + " stains=" + (t % 20 == 0 ? MultiplayerCheck.stains(mc.level, origin) : -1);
        }
        BloodAndBones.LOGGER.info("[mp] watcher {} t={} time={} pt={} nanos={}{}{}", kind, t, mc.level.getGameTime(), partial, System.nanoTime(), extra, line);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {
        });
        BloodAndBones.LOGGER.info("[mp] {} took {}", ROLE, name);
    }
}
