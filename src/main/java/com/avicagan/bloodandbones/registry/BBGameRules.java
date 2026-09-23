package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.network.BloodlessRulePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * {@code /gamerule bloodandbonesBloodless true} forces bloodless presentation for everyone on the server,
 * whatever their own setting. Nothing about how the game plays changes. Players are told on joining and
 * whenever it changes.
 */
public final class BBGameRules {
    public static final GameRules.Key<GameRules.BooleanValue> BLOODLESS = GameRules.register("bloodandbonesBloodless",
            GameRules.Category.MISC, GameRules.BooleanValue.create(false, (server, value) -> tellEveryone(server, value.get())));

    private BBGameRules() {
    }

    /** Loads the class, so the rule is registered before any world is. */
    public static void register() {
    }

    public static void tell(ServerPlayer player) {
        if (player.connection.hasChannel(BloodlessRulePayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new BloodlessRulePayload(player.serverLevel().getGameRules().getBoolean(BLOODLESS)));
        }
    }

    private static void tellEveryone(MinecraftServer server, boolean forced) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection.hasChannel(BloodlessRulePayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, new BloodlessRulePayload(forced));
            }
        }
    }
}
