package com.avicagan.bloodandbones.network;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.client.ClientDragState;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class BBNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BloodAndBones.MOD_ID).versioned("1").optional();
        registrar.playToClient(DragSyncPayload.TYPE, DragSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientDragState.handle(payload)));
        registrar.playToServer(PunchCarcassPayload.TYPE, PunchCarcassPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        com.avicagan.bloodandbones.carcass.CarcassRest.punch(player.serverLevel(), player, payload.carcass());
                    }
                }));
        registrar.playToClient(RigSyncPayload.TYPE, RigSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.avicagan.bloodandbones.carcass.rig.RigManager.receiveClientRigs(payload.rigs())));
        registrar.playToClient(BloodlessRulePayload.TYPE, BloodlessRulePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.avicagan.bloodandbones.config.BBClientConfig.setServerForced(payload.forced())));
        registrar.playToClient(com.avicagan.bloodandbones.body.BodySync.Payload.TYPE, com.avicagan.bloodandbones.body.BodySync.Payload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.avicagan.bloodandbones.client.ClientBody.receive(payload)));
        registrar.playToClient(com.avicagan.bloodandbones.body.Surgery.OpenPayload.TYPE, com.avicagan.bloodandbones.body.Surgery.OpenPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.avicagan.bloodandbones.client.ClientBody.openSurgery(payload.pos(), payload.patient())));
        registrar.playToServer(com.avicagan.bloodandbones.body.Surgery.ActionPayload.TYPE, com.avicagan.bloodandbones.body.Surgery.ActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        com.avicagan.bloodandbones.body.Surgery.handle(player, payload);
                    }
                }));
        registrar.playToServer(com.avicagan.bloodandbones.body.Vent.Payload.TYPE, com.avicagan.bloodandbones.body.Vent.Payload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.avicagan.bloodandbones.body.Vent.spray(context.player())));
        registrar.playToClient(ButcherySyncPayload.TYPE, ButcherySyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.receiveClientTables(payload.tables())));
    }
}
