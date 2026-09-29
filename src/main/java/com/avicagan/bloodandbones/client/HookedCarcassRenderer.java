package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.carcass.CarcassLook;
import com.avicagan.bloodandbones.carcass.HookedCarcass;
import com.avicagan.bloodandbones.carcass.ShackleHookBlock;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * A carcass hung on a Shackle Hook that rides a Create contraption, drawn from the hook's moved data (HookedCarcass): the
 * torso held at the tip with the turn it had, and every other piece at its pose on it, with its cut ends and its rot, the
 * way the carcass's own cells draw it (CarcassPartRenderer). It turns with the contraption.
 */
public final class HookedCarcassRenderer {
    private HookedCarcassRenderer() {
    }

    public static void render(MovementContext context, ContraptionMatrices matrices, MultiBufferSource buffers) {
        CompoundTag draw = context.data.getCompound("Draw");
        if (draw.isEmpty() || !context.state.hasProperty(ShackleHookBlock.FACING)) {
            return;
        }
        Rig rig = RigManager.clientRig(ResourceLocation.parse(draw.getString("Entity")), draw.getBoolean("Baby")).orElse(null);
        Bone torso = rig == null ? null : rig.bone(draw.getString("Root")).orElse(null);
        if (torso == null) {
            return;
        }
        ResourceLocation texture = ResourceLocation.parse(draw.getString("Texture"));
        List<CarcassLook.Coat> coats = new ArrayList<>();
        for (Tag t : draw.getList("Coats", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            coats.add(new CarcassLook.Coat(c.getString("Layer"), ResourceLocation.parse(c.getString("Texture")), c.getInt("Tint")));
        }
        List<String> cuts = new ArrayList<>();
        for (Tag t : draw.getList("Cuts", Tag.TAG_STRING)) {
            cuts.add(t.getAsString());
        }
        float freshness = draw.getFloat("Freshness");
        int rot = CarcassModels.rotColor(freshness);
        int light = context.position == null ? LevelRenderer.getLightColor(context.world, BlockPos.containing(Vec3.atCenterOf(context.localPos)))
                : LevelRenderer.getLightColor(context.world, BlockPos.containing(context.position));
        // the torso's bone origin, so that the point the hook holds sits on the tip (in the contraption's own space)
        Vec3 tip = ShackleHookBlock.tip(context.localPos, context.state);
        Quaterniond turn = HookedCarcass.quaternion(draw, "Turn");
        Vector3d origin = new Vector3d(tip.x, tip.y, tip.z).sub(turn.transform(HookedCarcass.vector(draw, "Held")));
        PoseStack ms = matrices.getModelViewProjection();
        ms.pushPose();
        ms.translate(origin.x, origin.y, origin.z);
        ms.mulPose(new Quaternionf(turn));
        drawPiece(rig, torso, texture, coats, cuts, rot, freshness, ms, buffers, light);
        for (Tag t : draw.getList("Pieces", Tag.TAG_COMPOUND)) {
            CompoundTag piece = (CompoundTag) t;
            Bone bone = rig.bone(piece.getString("Name")).orElse(null);
            if (bone == null) {
                continue;
            }
            ms.pushPose();
            ms.translate(piece.getDouble("X"), piece.getDouble("Y"), piece.getDouble("Z"));
            ms.mulPose(new Quaternionf((float) piece.getDouble("QX"), (float) piece.getDouble("QY"), (float) piece.getDouble("QZ"), (float) piece.getDouble("QW")));
            drawPiece(rig, bone, texture, coats, cuts, rot, freshness, ms, buffers, light);
            ms.popPose();
        }
        ms.popPose();
    }

    private static void drawPiece(Rig rig, Bone bone, ResourceLocation texture, List<CarcassLook.Coat> coats, List<String> cuts, int rot, float freshness,
                                  PoseStack ms, MultiBufferSource buffers, int light) {
        CarcassModels.drawBone(rig, bone, texture, coats, rot, ms, buffers, light);
        WoundCaps.draw(rig, bone, cuts, rot, ms, buffers, light);
        CarcassModels.drawMaggots(rig, bone, freshness, ms, buffers, light);
    }
}
