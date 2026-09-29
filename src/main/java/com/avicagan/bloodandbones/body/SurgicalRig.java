package com.avicagan.bloodandbones.body;

import com.avicagan.bloodandbones.carcass.Blood;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassJoints;
import com.avicagan.bloodandbones.carcass.CarcassLook;
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
 * <li>once the organs are out, the hide, if it is still on, all of it at once;</li>
 * <li>then a limb off at its joint, the end of a chain first, cleanly;</li>
 * <li>once nothing hangs off it, the piece itself, broken down into all of its butchery table (the surgery path).</li>
 * </ol>
 * It works a carried piece laid on the table, or with nothing laid on it, a carcass lying on its top (a body too heavy
 * to carry, dragged there). A Deployer's Cleaver works it as the station it is, for everything; a player's own hand gets
 * the organs whole but the meat, bone and hide only as a hand gets them (about half, with real loss). Whoever holds the
 * blade, a cut takes {@link #PAUSE} ticks before the next can start: the rig is the slowest path at any speed.
 */
public final class SurgicalRig {
    /** Height of the table's top, in blocks. */
    public static final double TOP = 15.0 / 16.0;
    /**
     * Ticks one cut at the rig takes before the next can start, by hand or by a Deployer: two and a half times a Cleaver's
     * pause at the Butcher's Table (12), so it stays the slowest path even under a Deployer at 256 RPM.
     */
    public static final int PAUSE = 30;

    /** What a click at the rig came to. */
    public enum Result {
        /** It took something. */
        CUT,
        /** The last cut is still under way. */
        BUSY,
        /** There was something, but the filter turned it all away. */
        FILTERED,
        /** Nothing on the table for it to take. */
        NOTHING
    }

    private SurgicalRig() {
    }

    /**
     * A click at the rig with a blade, paced: while the last cut is under way it does nothing, and a cut starts the pause
     * (on the table, so a Deployer is held to it too, and on a player's blade, so their hand shows it).
     */
    public static Result click(ServerLevel level, Player surgeon, SurgeryTableBlockEntity table, ItemStack blade) {
        if (level.getGameTime() < table.nextCut) {
            return Result.BUSY;
        }
        Result result = work(level, surgeon, table, blade);
        if (result == Result.CUT) {
            table.nextCut = level.getGameTime() + PAUSE;
            if (!(surgeon instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
                surgeon.getCooldowns().addCooldown(blade.getItem(), PAUSE);
            }
        }
        return result;
    }

    /**
     * One cut at the rig, unpaced (what a click does once the rig is ready).
     *
     * @return whether it took anything
     */
    public static boolean cut(ServerLevel level, Player surgeon, SurgeryTableBlockEntity table, ItemStack blade) {
        return work(level, surgeon, table, blade) == Result.CUT;
    }

    private static Result work(ServerLevel level, Player surgeon, SurgeryTableBlockEntity table, ItemStack blade) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(table.item());
        if (piece != null) {
            if (!table.filtering.takes(table.item())) {
                surgeon.displayClientMessage(Component.translatable("bloodandbones.surgery.filtered"), true);
                return Result.FILTERED;
            }
            if (organsLeft(level, piece.entity(), Surgery.organsTaken(piece), kind(level, piece)) > 0) {
                return Surgery.harvest(level, surgeon, table, blade) ? Result.CUT : Result.NOTHING;
            }
            if (!piece.skinned() && flay(level, surgeon, table, blade, piece)) {
                return Result.CUT;
            }
            return finish(level, surgeon, table, blade, piece) ? Result.CUT : Result.NOTHING;
        }
        if (!table.item().isEmpty()) {
            return Result.NOTHING;
        }
        Result result = Result.NOTHING;
        for (CarcassButchery.Lying lying : CarcassButchery.lyingOn(level, table.getBlockPos(), TOP)) {
            Result one = work(level, surgeon, table, blade, lying.carcass(), lying.bone());
            if (one == Result.CUT) {
                return one;
            }
            if (one == Result.FILTERED) {
                result = one;
            }
        }
        if (result == Result.FILTERED) {
            surgeon.displayClientMessage(Component.translatable("bloodandbones.surgery.filtered"), true);
        }
        return result;
    }

    /** Whether anything lies on the table for it to work: a carcass on its top. */
    public static boolean anythingOn(ServerLevel level, SurgeryTableBlockEntity table) {
        return !CarcassButchery.lyingOn(level, table.getBlockPos(), TOP).isEmpty();
    }

    /**
     * Run some butchery on the path whoever holds the blade is on: a player's own hand is the hand path, at their butchery
     * yield; a Deployer's stand-in works the rig as the station it is (the surgery path, all of it).
     */
    private static <T> T onPathOf(Player surgeon, java.util.function.Supplier<T> action) {
        if (surgeon == null || surgeon instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            return CarcassButchery.onPath(ButcheryPaths.SURGERY, 1.0F, action);
        }
        return CarcassButchery.byHand(surgeon, action);
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

    /** The carried piece's organs are out and its hide is still on: the hide comes off it in one cut, its share of it. */
    private static boolean flay(ServerLevel level, Player surgeon, SurgeryTableBlockEntity table, ItemStack blade, CarcassPieceItem.Piece piece) {
        boolean hasHide = com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(piece.entity()).map(t -> !t.hide().isEmpty()).orElse(false);
        if (!hasHide) {
            return false;
        }
        List<ItemStack> hide = onPathOf(surgeon, () -> CarcassButchery.pieceHide(level, piece));
        CarcassLook bare = CarcassLook.flesh();
        table.item().set(com.avicagan.bloodandbones.registry.BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(piece.entity(), piece.bone(),
                bare.texture(), List.of(), piece.freshness(), true, piece.traits(), piece.blood(), piece.bloodMax(), piece.decay(), piece.baby()));
        table.notifyUpdate();
        BlockPos pos = table.getBlockPos();
        Vector3d top = new Vector3d(pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5);
        drop(level, top, hide);
        level.playSound(null, top.x, top.y, top.z, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_SKIN.get(), SoundSource.BLOCKS, 1.0F, 0.9F);
        if (bleeds(piece.entity())) {
            Blood.burst(level, top, 6, Blood.soul(piece.entity()));
            Blood.bloody(blade, level);
        }
        return true;
    }

    /** The carried piece's organs and hide are out: the rest of it comes apart on the table, all of it. */
    private static boolean finish(ServerLevel level, Player surgeon, SurgeryTableBlockEntity table, ItemStack blade, CarcassPieceItem.Piece piece) {
        boolean cuttable = com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(piece.entity())
                .map(t -> !t.part(piece.bone()).isEmpty()).orElse(false);
        if (!cuttable) {
            surgeon.displayClientMessage(Component.translatable("bloodandbones.surgery.no_organs"), true);
            return false;
        }
        List<ItemStack> yields = onPathOf(surgeon, () -> CarcassButchery.pieceYields(level, piece));
        table.take();
        BlockPos pos = table.getBlockPos();
        Vector3d top = new Vector3d(pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5);
        drop(level, top, yields);
        cutSound(level, top, piece.entity(), blade);
        return true;
    }

    /**
     * One cut on a carcass lying on the table, starting from the part of it nearest the top's middle: an organ out of any
     * of its parts, else its hide, else a limb off, else a loose piece broken down; whatever the filter lets it take.
     */
    private static Result work(ServerLevel level, Player surgeon, SurgeryTableBlockEntity table, ItemStack blade, CarcassSavedData.Carcass carcass, String near) {
        if (carcass.resting && com.avicagan.bloodandbones.carcass.CarcassRest.split(level, carcass) == null) {
            return Result.NOTHING;
        }
        boolean turnedAway = false;
        // organs: the torso's first, then the rest in rig order, each part counting its own
        List<String> parts = new java.util.ArrayList<>(carcass.bones.keySet());
        parts.remove(carcass.rootBone);
        parts.addFirst(carcass.rootBone);
        for (String bone : parts) {
            CarcassPieceItem.Piece piece = CarcassPieceItem.piece(CarcassPieceItem.of(carcass, bone));
            int taken = Surgery.organsTaken(carcass.traits, bone, bone.equals(carcass.rootBone));
            if (piece == null || organsLeft(level, carcass.entity, taken, kind(level, piece)) == 0) {
                continue;
            }
            if (!table.filtering.takes(carcass, bone)) {
                turnedAway = true;
                continue;
            }
            BodyPart.Kind organ = Surgery.organs(kind(level, piece)).get(taken);
            carcass.traits.put(Surgery.ORGANS_TAKEN + ":" + bone, Integer.toString(taken + 1));
            CarcassSavedData.get(level).setDirty();
            Vector3d at = com.avicagan.bloodandbones.carcass.CarcassAssembler.boneWorldPosition(level, carcass, bone);
            give(level, surgeon, table, com.avicagan.bloodandbones.registry.BBItems.partItem(organ).of(carcass.entity, carcass.baby));
            if (at != null) {
                Blood.wound(level, carcass, at, 10, 1);
                cutSound(level, at, carcass.entity, blade);
            }
            return Result.CUT;
        }
        // the hide, all of it at once, while it is still on (asked of the filter as the body it comes off)
        boolean hasHide = com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(carcass.entity).map(t -> !t.hide().isEmpty()).orElse(false);
        if (!carcass.skinned && hasHide) {
            if (table.filtering.takes(carcass, carcass.rootBone)) {
                Vector3d at = com.avicagan.bloodandbones.carcass.CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
                if (onPathOf(surgeon, () -> CarcassButchery.flay(level, carcass, at))) {
                    bloody(level, carcass, blade);
                    return Result.CUT;
                }
            } else {
                turnedAway = true;
            }
        }
        // a limb off, the end of a chain first (a head before the neck it hangs from)
        String limb = null;
        for (CarcassJoints.Spec joint : carcass.joints) {
            String child = joint.child();
            if (!carcass.bones.containsKey(child)) {
                continue;
            }
            if (!table.filtering.takes(carcass, child)) {
                turnedAway = true;
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
            return Result.CUT;
        }
        // nothing hangs off it: the piece lying on the table comes apart, all of it
        if (!CarcassButchery.isAttached(carcass, near) && CarcassButchery.hasYields(carcass, near)) {
            if (!table.filtering.takes(carcass, near)) {
                return Result.FILTERED;
            }
            Vector3d at = com.avicagan.bloodandbones.carcass.CarcassAssembler.boneWorldPosition(level, carcass, near);
            if (at == null) {
                return Result.NOTHING;
            }
            onPathOf(surgeon, () -> {
                CarcassButchery.butcher(level, carcass, near, at);
                return true;
            });
            bloody(level, carcass, blade);
            return Result.CUT;
        }
        return turnedAway ? Result.FILTERED : Result.NOTHING;
    }

    /** What comes off drops on the table top, springing up a little. */
    private static void drop(ServerLevel level, Vector3d top, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            ItemEntity item = new ItemEntity(level, top.x, top.y, top.z, stack);
            item.setDeltaMovement(level.random.triangle(0.0, 0.06), 0.12, level.random.triangle(0.0, 0.06));
            level.addFreshEntity(item);
        }
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

    private static boolean bleeds(net.minecraft.resources.ResourceLocation entity) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(entity).map(type -> !type.is(com.avicagan.bloodandbones.registry.BBTags.BLOODLESS)).orElse(true);
    }

    private static void cutSound(ServerLevel level, Vector3d at, net.minecraft.resources.ResourceLocation entity, ItemStack blade) {
        level.playSound(null, at.x, at.y, at.z, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_CUT.get(), SoundSource.BLOCKS, 0.9F, 1.1F);
        if (bleeds(entity)) {
            Blood.burst(level, at, 8, Blood.soul(entity));
            Blood.bloody(blade, level);
        }
    }
}
