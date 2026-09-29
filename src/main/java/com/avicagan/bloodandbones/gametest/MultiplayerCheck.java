package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.ShackleHookBlock;
import com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBItems;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.UUID;

/**
 * The server half of the two-client check (docs/BRIEF-AUDIT.md package 18: "it has to look right in multiplayer"),
 * off unless the dedicated server runs with {@code -Dbloodandbones.multiplayer=server} (the {@code runMpServer} run).
 * Once a player called Butcher and one called Watcher are both in, it builds a small scene (a cow, a Shackle Hook over
 * a Bleeding Rack), hands the Butcher a Meat Hook, a Cleaver and a Flensing Knife, and tells both clients when the
 * clock starts. The Butcher's client then kills, drags, hangs, cuts and skins the cow with real clicks
 * (client/MultiplayerShowcase); the Watcher's client photographs it. This side moves the Watcher's camera, writes where
 * every carcass body really is each tick (to compare with what the Watcher's client draws), and stops the server at
 * the end.
 */
public final class MultiplayerCheck {
    public static final String PROPERTY = "bloodandbones.multiplayer";
    public static final String BUTCHER = "Butcher";
    public static final String WATCHER = "Watcher";
    /** What the clients read from their chat to learn the clock; they hide it. */
    public static final String SIGNAL = "[bb-mp] go ";
    /** Ticks after the start at which the Watcher is moved in close to the hook, and the server stops. */
    public static final int CLOSE_VIEW = 280;
    public static final int SIDE_VIEW = 700;
    public static final int END = 800;

    private static long start = -1;
    private static int waited;
    private static BlockPos origin;

    private MultiplayerCheck() {
    }

    public static void init() {
        if ("server".equals(System.getProperty(PROPERTY))) {
            NeoForge.EVENT_BUS.addListener(MultiplayerCheck::tick);
            BloodAndBones.LOGGER.info("[mp] server check enabled");
        }
    }

    private static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        ServerPlayer butcher = server.getPlayerList().getPlayerByName(BUTCHER);
        ServerPlayer watcher = server.getPlayerList().getPlayerByName(WATCHER);
        if (start < 0) {
            // both in, and given time to load the chunks and the rigs (a client drawing in software is slow to)
            if (butcher == null || watcher == null) {
                waited = 0;
                return;
            }
            if (++waited < 400) {
                return;
            }
            start = level.getGameTime() + 20;
            build(level, butcher, watcher);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(Component.literal(SIGNAL + start + " " + origin.getX() + " " + origin.getY() + " " + origin.getZ()));
            }
            BloodAndBones.LOGGER.info("[mp] started at {}, origin {}", start, origin);
            return;
        }
        long t = level.getGameTime() - start;
        if (watcher != null) {
            if (t == CLOSE_VIEW) {
                // in close from the side, level with the hanging cow, the Butcher out of the way to the left
                look(watcher, origin.getX() + 10.0, origin.getY() + 2.2, origin.getZ() + 3.5, 90.0F, 10.0F);
            } else if (t == SIDE_VIEW) {
                // from the side and above: the rack, the stains, the cut leg on the ground
                look(watcher, origin.getX() + 10.5, origin.getY() + 4.0, origin.getZ() + 3.5, 90.0F, 35.0F);
            }
        }
        if (t >= 0 && t <= END) {
            record(level, t, butcher);
        }
        if (t == END + 60 || (t > 0 && butcher == null && watcher == null)) {
            BloodAndBones.LOGGER.info("[mp] done at {}", t);
            server.halt(false);
        }
    }

    /** Flat ground at the Butcher's feet: a cow three blocks in front, a hook six to the side with a rack under it. */
    private static void build(ServerLevel level, ServerPlayer butcher, ServerPlayer watcher) {
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.setDayTime(6000);
        int x = butcher.getBlockX();
        int z = butcher.getBlockZ();
        origin = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        // clear the ground of anything the world put there, and of the animals a flat world starts with
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, 0, -8), origin.offset(14, 8, 8))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (net.minecraft.world.entity.Entity entity : level.getEntities((net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(origin).inflate(32.0), entity -> !(entity instanceof net.minecraft.world.entity.player.Player))) {
            entity.discard();
        }
        BlockPos hook = origin.offset(6, 4, 3);
        level.setBlockAndUpdate(hook.above(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(hook, BBBlocks.SHACKLE_HOOK.get().defaultBlockState().setValue(ShackleHookBlock.FACING, Direction.UP));
        level.setBlockAndUpdate(origin.offset(6, 0, 3), BBBlocks.BLEEDING_RACK.getDefaultState());
        Cow cow = EntityType.COW.create(level);
        cow.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 3.5, 180.0F, 0.0F);
        cow.setYHeadRot(180.0F);
        cow.setYBodyRot(180.0F);
        cow.setNoAi(true);
        level.addFreshEntity(cow);
        butcher.getInventory().clearContent();
        butcher.getInventory().setItem(0, new ItemStack(BBItems.MEAT_HOOK.get()));
        butcher.getInventory().setItem(1, new ItemStack(BBItems.CLEAVER.get()));
        butcher.getInventory().setItem(2, new ItemStack(BBItems.FLENSING_KNIFE.get()));
        butcher.getInventory().selected = 0;
        butcher.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
        look(butcher, origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0.0F, 30.0F);
        // the whole stretch from the cow to the hook in view, from behind and above the Butcher
        look(watcher, origin.getX() + 3.0, origin.getY() + 3.0, origin.getZ() - 6.0, 0.0F, 22.0F);
    }

    /** Put a player here, looking this way, hovering (both play in creative). */
    private static void look(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        player.getAbilities().flying = player.getName().getString().equals(WATCHER);
        player.onUpdateAbilities();
        player.teleportTo(player.serverLevel(), x, y, z, yaw, pitch);
    }

    /** Blood stains on the ground around the scene, as this side has them. */
    public static int stains(net.minecraft.world.level.Level level, BlockPos origin) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-6, -1, -10), origin.offset(16, 2, 10))) {
            if (level.getBlockState(pos).is(BBBlocks.BLOOD_STAIN.get())) {
                count++;
            }
        }
        return count;
    }

    /** Where every carcass body is this tick, as the server has it, for comparing with the Watcher's client. */
    private static void record(ServerLevel level, long t, ServerPlayer butcher) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        StringBuilder line = new StringBuilder();
        for (CarcassSavedData.Carcass carcass : CarcassSavedData.get(level).all()) {
            for (Map.Entry<String, UUID> bone : carcass.bones.entrySet()) {
                if (container.getSubLevel(bone.getValue()) instanceof ServerSubLevel body && !body.isRemoved()) {
                    Vector3dc p = body.logicalPose().position();
                    Quaterniondc q = body.logicalPose().orientation();
                    line.append(String.format(java.util.Locale.ROOT, " %s:%s:%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f", bone.getValue(), bone.getKey(),
                            p.x(), p.y(), p.z(), q.x(), q.y(), q.z(), q.w()));
                }
            }
        }
        BlockPos hook = origin.offset(6, 4, 3);
        boolean hung = level.getBlockEntity(hook) instanceof ShackleHookBlockEntity shackle && shackle.isOccupied();
        boolean dragging = butcher != null && CarcassDrag.isDragging(butcher);
        String at = butcher == null ? "gone" : String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f", butcher.getX(), butcher.getY(), butcher.getZ());
        int rack = level.getBlockEntity(origin.offset(6, 0, 3)) instanceof com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity tray ? tray.getFluid().getAmount() : -1;
        BloodAndBones.LOGGER.info("[mp] server t={} time={} dragging={} hung={} butcher={} rack={} stains={}{}", t, level.getGameTime(), dragging, hung, at, rack,
                t % 20 == 0 ? stains(level, origin) : -1, line);
    }
}
