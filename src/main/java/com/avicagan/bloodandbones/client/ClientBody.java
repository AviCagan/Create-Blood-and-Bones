package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.body.BBAttachments;
import com.avicagan.bloodandbones.body.BodySync;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/** The client's side of the body: bodies as the server sends them, and the surgery screen. */
public final class ClientBody {
    private ClientBody() {
    }

    public static void receive(BodySync.Payload payload) {
        if (Minecraft.getInstance().level != null) {
            Entity entity = Minecraft.getInstance().level.getEntity(payload.entity());
            if (entity != null) {
                entity.setData(BBAttachments.BODY, payload.body());
            }
        }
    }

    public static void openSurgery(BlockPos table, int patient) {
        Minecraft.getInstance().setScreen(new SurgeryScreen(table, patient));
    }
}
