package com.avicagan.bloodandbones.carcass;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * A Shackle Hook riding a Create contraption (ARCHITECTURE 3.5). As the contraption is put together the carcass hung on
 * the hook goes into the hook's moved data (HookedCarcass) and leaves the world, rather than fall where the hook was;
 * while it moves this draws it hanging from the hook; set down, the hook hangs it again (ShackleHookBlockEntity#tick).
 * It does nothing else while it moves, so it is never switched off by Contraption Controls.
 */
public class ShackleHookMovement implements MovementBehaviour {
    @Override
    public void startMoving(MovementContext context) {
        if (!(context.world instanceof ServerLevel level) || context.blockEntityData == null) {
            return;
        }
        CompoundTag data = context.blockEntityData;
        UUID id = data.hasUUID("Carcass") ? data.getUUID("Carcass") : null;
        CarcassSavedData.Carcass carcass = id == null ? null : CarcassSavedData.get(level).carcass(id);
        if (carcass == null) {
            return;
        }
        // read from the data Create took, not the hook in the world: a train takes its blocks before it starts them moving
        Vector3d anchor = new Vector3d(data.getDouble("AnchorX"), data.getDouble("AnchorY"), data.getDouble("AnchorZ"));
        CompoundTag packed = HookedCarcass.pack(level, carcass, anchor);
        if (packed == null) {
            return;
        }
        CarcassButchery.takeAway(level, carcass);
        BlockPos at = context.contraption.anchor.offset(context.localPos);
        if (level.getBlockEntity(at) instanceof ShackleHookBlockEntity hook && id.equals(hook.hookedCarcass())) {
            // still in the world: it keeps the carcass too, and hangs it again if the contraption does not move after all
            hook.keepPacked(level, packed.copy());
        }
        data.remove("Carcass");
        data.remove("SubLevel");
        data.put("Packed", packed);
        context.data.put("Draw", packed.getCompound("Draw").copy());
    }

    /** Nothing to switch off: it only carries. */
    @Nullable
    @Override
    public ItemStack canBeDisabledVia(MovementContext context) {
        return null;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld, ContraptionMatrices matrices, MultiBufferSource buffer) {
        com.avicagan.bloodandbones.client.HookedCarcassRenderer.render(context, matrices, buffer);
    }
}
