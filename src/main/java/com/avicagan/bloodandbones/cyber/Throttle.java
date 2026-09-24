package com.avicagan.bloodandbones.cyber;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBSounds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The cybernetic throttle, one for every module (the design brief: "hold to spool up, release to fire, with a
 * gauge, a rising audio pitch, and visible state on the limb itself"). Holding the throttle key spools the
 * chosen module up from nothing to full over {@link #SPOOL_TICKS}; while it is held, soul blood drains at a
 * rate that climbs with the cube of the spool, so a tap costs almost nothing and full throttle costs
 * {@link #FULL_DRAIN} mB a second, something done for ten seconds to solve a problem, never kept up. Letting
 * go fires a firing module at the spool it reached; a held module works every tick at the spool it has. If
 * the tank cannot pay, the throttle chokes: it stops, and nothing fires.
 * <p>
 * The server keeps the state; the client only says when the key goes down (and for which module) and up.
 */
public final class Throttle {
    /** Game ticks from nothing to full spool. */
    public static final int SPOOL_TICKS = 40;
    /** mB of soul blood a second, held at full spool. */
    public static final float FULL_DRAIN = 150.0F;
    /** mB a firing module costs when let go, however little it was spooled. */
    public static final int TAP = 5;

    static final class State {
        final BodyPart part;
        final int slot;
        final Module module;
        final long start;
        float owed;
        float level;

        State(BodyPart part, int slot, Module module, long start) {
            this.part = part;
            this.slot = slot;
            this.module = module;
            this.start = start;
        }
    }

    private static final Map<Player, State> HELD = new WeakHashMap<>();

    private Throttle() {
    }

    /** The spool after this many ticks held, 0 to 1. */
    public static float level(long ticksHeld) {
        return Math.min(1.0F, Math.max(0, ticksHeld) / (float) SPOOL_TICKS);
    }

    /** mB a second held at this spool: steeply more at the top than at the bottom. */
    public static float drain(float level) {
        return FULL_DRAIN * level * level * level;
    }

    /** How much of the drain this wearer pays (the brass set bonus pays less). */
    public static float efficiency(Player player) {
        return SetBonus.brass(player) ? SetBonus.BRASS_DRAIN : 1.0F;
    }

    /** The module being spooled, if any. */
    @Nullable
    public static Module spooling(Player player) {
        State state = HELD.get(player);
        return state == null ? null : state.module;
    }

    /** The spool now, 0 when not held. */
    public static float level(Player player) {
        State state = HELD.get(player);
        return state == null ? 0.0F : state.level;
    }

    /** Whether the worn tank has soul blood to pay with. */
    public static boolean fuelled(Player player) {
        var fluid = FluidBacktankItem.fluid(FluidBacktankItem.wornBy(player));
        return !fluid.isEmpty() && fluid.is(BBFluids.soulBlood()) && fluid.getAmount() > 0;
    }

    /** The key went down, for the module in that limb's slot. */
    public static boolean press(Player player, BodyPart part, int slot) {
        release(player, false);
        if (player.isSpectator() || !player.isAlive()) {
            return false;
        }
        java.util.List<Module> modules = Modules.of(BodyEffects.body(player).implant(part));
        if (slot < 0 || slot >= modules.size() || !modules.get(slot).driven() || !Modules.still(player, part, slot, modules.get(slot))) {
            return false;
        }
        if (!fuelled(player)) {
            choke(player);
            return false;
        }
        Module module = modules.get(slot);
        State state = new State(part, slot, module, player.level().getGameTime());
        HELD.put(player, state);
        sync(player, state);
        return true;
    }

    /**
     * The key came up: a firing module fires at the spool it reached, a held one stops.
     *
     * @param fire false to stop without firing (the module went, the player died)
     */
    public static void release(Player player, boolean fire) {
        State state = HELD.remove(player);
        if (state == null) {
            return;
        }
        sync(player, null);
        if (state.module.mode() == Module.Mode.HOLD) {
            ModuleActions.stop(player, state.module);
            return;
        }
        if (!fire || !Modules.still(player, state.part, state.slot, state.module)) {
            return;
        }
        if (BodyEffects.take(player, Math.max(1, Math.round(TAP * efficiency(player)))) <= 0) {
            choke(player);
            return;
        }
        ModuleActions.fire(player, state.module, state.level);
    }

    /** The tank ran dry or never had soul blood: a sputter, and nothing. */
    private static void choke(Player player) {
        player.level().playSound(null, player.blockPosition(), BBSounds.CYBERNETIC_CHOKE.get(), SoundSource.PLAYERS, 0.8F, 0.8F);
        player.displayClientMessage(Component.translatable("bloodandbones.throttle.dry"), true);
    }

    /** Every tick, for a player holding the throttle: spool, pay, and drive a held module. */
    public static void tick(Player player) {
        State state = HELD.get(player);
        if (state == null) {
            return;
        }
        if (!player.isAlive() || player.isSpectator() || !Modules.still(player, state.part, state.slot, state.module)) {
            release(player, false);
            return;
        }
        state.level = level(player.level().getGameTime() - state.start);
        state.owed += drain(state.level) * efficiency(player) / 20.0F;
        int due = (int) state.owed;
        if (due > 0) {
            state.owed -= due;
            if (!fuelled(player) || BodyEffects.take(player, due) < due) {
                release(player, false);
                choke(player);
                return;
            }
        }
        if (state.module.mode() == Module.Mode.HOLD) {
            ModuleActions.hold(player, state.module, state.level);
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
            ModuleActions.tick(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player, false);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer watcher && HELD.containsKey(target)) {
            PacketDistributor.sendToPlayer(watcher, payload(target, HELD.get(target)));
        }
    }

    private static SyncPayload payload(Player player, @Nullable State state) {
        return state == null ? new SyncPayload(player.getId(), false, BodyPart.RIGHT_ARM, Module.PISTON_RAM, 0L)
                : new SyncPayload(player.getId(), true, state.part, state.module, state.start);
    }

    private static void sync(Player player, @Nullable State state) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload(player, state));
    }

    /** From the client: the throttle key went down for a module, or came up. */
    public record KeyPayload(boolean down, BodyPart part, int slot) implements CustomPacketPayload {
        public static final Type<KeyPayload> TYPE = new Type<>(BloodAndBones.asResource("throttle_key"));
        public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, KeyPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, KeyPayload::down, NeoForgeStreamCodecs.enumCodec(BodyPart.class), KeyPayload::part,
                ByteBufCodecs.VAR_INT, KeyPayload::slot, KeyPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void handle(Player player, KeyPayload payload) {
        if (payload.down()) {
            press(player, payload.part(), payload.slot());
        } else {
            release(player, true);
        }
    }

    /**
     * To the player and everyone who sees them: they started spooling this module at this game time, or
     * stopped. Clients work the spool out from the start, for the gauge, the pitch and the glow.
     */
    public record SyncPayload(int entity, boolean on, BodyPart part, Module module, long start) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(BloodAndBones.asResource("throttle_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SyncPayload::entity, ByteBufCodecs.BOOL, SyncPayload::on,
                NeoForgeStreamCodecs.enumCodec(BodyPart.class), SyncPayload::part, Module.STREAM_CODEC, SyncPayload::module,
                ByteBufCodecs.VAR_LONG, SyncPayload::start, SyncPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
