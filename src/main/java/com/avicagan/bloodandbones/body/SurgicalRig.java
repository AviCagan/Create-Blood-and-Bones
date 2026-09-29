package com.avicagan.bloodandbones.body;

import com.avicagan.bloodandbones.carcass.Blood;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassJoints;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.butchery.ButcheryPaths;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

/**
 * The Surgery Table with its Surgical Rig as a butchery station: the brief's third path, "slowest, totally reconfigurable,
 * full yield plus organs". A Cleaver cut (by hand or a Deployer) takes one thing at a time, whatever its filter lets it:
 * <ol>
 * <li>an organ: a body's heart, lungs and stomach, a head's eyes (Surgery#organs);</li>
 * <li>once a part's organs are out, a limb off at its joint, the end of a chain first, cleanly;</li>
 * <li>once nothing hangs off it, the piece itself, broken down into all of its butchery table (the surgery path).</li>
 * </ol>
 * It works a carried piece laid on the table, or with nothing laid on it, a carcass lying on its top (a body too heavy
 * to carry, dragged there).
 */
public final class SurgicalRig {
    /** Height of the table's top, in blocks. */
    public static final double TOP = 15.0 / 16.0;

    private SurgicalRig() {
    }

    /**
     * One cut at the rig.
     *
     * @return whether it took anything
     */
    public static boolean cut(ServerLevel level, Player surgeon, SurgeryTableBlockEntity table, ItemStack blade) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(table.item());
        if (piece != null) {
            if (!table.filtering.takes(table.item())) {
                surgeon.displayClientMessage(Component.translatable("bloodandbones.surgery.filtered"), true);
                return false;
            }
            if (organsLeft(level, piece.entity(), Surgery.organsTaken(piece), kind(level, piece)) > 0) {
                return Surgery.harvest(level, surgeon, table, blade);
            }
            return finish(level, surgeon, table, blade, piece);
        }
        if (!table.item().isEmpty()) {
            return false;
        }
        for (CarcassButchery.Lying lying : CarcassButchery.lyingOn(level, table.getBlockPos(), TOP)) {
            if (work(level, surgeon, table, blade, lying.carcass(), lying.bone())) {
                return true;
            }
        }
        return false;
    }

    /** Whether anything lies on the table for it to work: a carcass on its top. */
    public static boolean anythingOn(ServerLevel level, SurgeryTableBlockEntity table) {
        return !CarcassButchery.lyingOn(level, table.getBlockPos(), TOP).isEmpty();
    }

    /** The kind of part a piece is: body, head, limb or tail. */
    private static String kind(ServerLevel level, CarcassPieceItem.Piece piece) {
        return com.avicagan.bloodandbones.registry.BBItemAttributes.PiecePart.kindOf(piece, level);
    }

    /** How many organs are still in a part: none in a mob with no blood. */
    private static int organsLeft(ServerLevel level, net.minecraft.resources.ResourceLocation entity, int taken, String kind) {
        boolean bleeds = BuiltInRegistries.ENTITY_TYPE.getOptional(entity).map(type -> !type.is(com.avicagan.bloodandbones.registry.BBTags.BLOODLESS)).orElse(false);
        return bleeds ? Math.max(0, Surgery.organs(kind).size() - taken) : 0;
    }

    /** The carried piece's organs are out: the rest of it comes apart on the table, all of it. */
    private static boolean finish(ServerLevel level, Player surgeon, SurgeryTableBlockEntity table, ItemStack blade, CarcassPieceItem.Piece piece) {
        boolean cuttable = com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(piece.entity())
                .map(t -> !t.part(piece.bone()).isEmpty()).orElse(false);
        if (!cuttable) {
            surgeon.displayClientMessage(Component.translatable("bloodandbones.surgery.no_organs"), true);
            return false;
        }
        List<ItemStack> yields = CarcassButchery.onPath(ButcheryPaths.SURGERY, 1.0F, () -> CarcassButchery.pieceYields(level, piece));
        table.take();
        BlockPos pos = table.getBlockPos();
        Vector3d top = new Vector3d(pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5);
        for (ItemStack stack : yields) {
            ItemEntity item = new ItemEntity(level, top.x, top.y, top.z, stack);
            item.setDeltaMovement(level.random.triangle(0.0, 0.06), 0.12, level.random.triangle(0.0, 0.06));
            level.addFreshEntity(item);
        }
        cutSound(level, top, piece.entity(), blade);
        return true;
    }

    /**
     * One cut on a carcass lying on the table, starting from the part of it nearest the top's middle: an organ out of any
     * of its parts, else a limb off, else a loose piece broken down; whatever the filter lets it take.
     */
    private static boolean work(ServerLevel level, Player surgeon, SurgeryTableBlockEntity table, ItemStack blade, CarcassSavedData.Carcass carcass, String near) {
        if (carcass.resting && com.avicagan.bloodandbones.carcass.CarcassRest.split(level, carcass) == null) {
            return false;
        }
        // organs: the torso's first, then the rest in rig order
        List<String> parts = new java.util.ArrayList<>(carcass.bones.keySet());
        parts.remove(carcass.rootBone);
        parts.addFirst(carcass.rootBone);
        for (String bone : parts) {
            CarcassPieceItem.Piece piece = CarcassPieceItem.piece(CarcassPieceItem.of(carcass, bone));
            String key = Surgery.ORGANS_TAKEN + "." + bone;
            int taken = Surgery.organsTaken(carcass.traits, key);
            if (piece == null || organsLeft(level, carcass.entity, taken, kind(level, piece)) == 0 || !table.filtering.takes(carcass, bone)) {
                continue;
            }
            BodyPart.Kind organ = Surgery.organs(kind(level, piece)).get(taken);
            carcass.traits.put(key, Integer.toString(taken + 1));
            CarcassSavedData.get(level).setDirty();
            Vector3d at = com.avicagan.bloodandbones.carcass.CarcassAssembler.boneWorldPosition(level, carcass, bone);
            give(level, surgeon, table, com.avicagan.bloodandbones.registry.BBItems.partItem(organ).of(carcass.entity, carcass.baby));
            if (at != null) {
                Blood.wound(level, carcass, at, 10, 1);
                cutSound(level, at, carcass.entity, blade);
            }
            return true;
        }
        // a limb off, the end of a chain first (a head before the neck it hangs from)
        String limb = null;
        for (CarcassJoints.Spec joint : carcass.joints) {
            String child = joint.child();
            if (!carcass.bones.containsKey(child) || !table.filtering.takes(carcass, child)) {
                continue;
            }
            boolean end = carcass.joints.stream().noneMatch(other -> other.parent().equals(child));
            if (limb == null || end) {
                limb = child;
                if (end) {
                    break;
                }
            }
        }
        if (limb != null) {
            Vector3d at = com.avicagan.bloodandbones.carcass.CarcassAssembler.boneWorldPosition(level, carcass, limb);
            CarcassButchery.sever(level, carcass, limb, at);
            bloody(level, carcass, blade);
            return true;
        }
        // nothing hangs off it: the piece lying on the table comes apart, all of it
        if (!CarcassButchery.isAttached(carcass, near) && CarcassButchery.hasYields(carcass, near) && table.filtering.takes(carcass, near)) {
            Vector3d at = com.avicagan.bloodandbones.carcass.CarcassAssembler.boneWorldPosition(level, carcass, near);
            if (at == null) {
                return false;
            }
            CarcassButchery.onPath(ButcheryPaths.SURGERY, 1.0F, () -> {
                CarcassButchery.butcher(level, carcass, near, at);
                return true;
            });
            bloody(level, carcass, blade);
            return true;
        }
        return false;
    }

    /** An organ goes to whoever cut it (a Deployer's stand-in would hold it and stall: it drops on the table). */
    private static void give(ServerLevel level, @Nullable Player surgeon, SurgeryTableBlockEntity table, ItemStack stack) {
        if (surgeon == null || surgeon instanceof net.neoforged.neoforge.common.util.FakePlayer || !surgeon.getInventory().add(stack)) {
            net.minecraft.world.level.block.Block.popResource(level, table.getBlockPos().above(), stack);
        }
    }

    private static void bloody(ServerLevel level, CarcassSavedData.Carcass carcass, ItemStack blade) {
        if (Blood.bloody(carcass)) {
            Blood.bloody(blade, level);
        }
    }

    private static void cutSound(ServerLevel level, Vector3d at, net.minecraft.resources.ResourceLocation entity, ItemStack blade) {
        level.playSound(null, at.x, at.y, at.z, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_CUT.get(), SoundSource.BLOCKS, 0.9F, 1.1F);
        boolean bleeds = BuiltInRegistries.ENTITY_TYPE.getOptional(entity).map(type -> !type.is(com.avicagan.bloodandbones.registry.BBTags.BLOODLESS)).orElse(true);
        if (bleeds) {
            Blood.burst(level, at, 8, Blood.soul(entity));
            Blood.bloody(blade, level);
        }
    }
}
