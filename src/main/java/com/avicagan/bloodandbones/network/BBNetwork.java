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
        registrar.playToClient(RigSyncPayload.TYPE, RigSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.avicagan.bloodandbones.carcass.rig.RigManager.receiveClientRigs(payload.rigs())));
    }
}
