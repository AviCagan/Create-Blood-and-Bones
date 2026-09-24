package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.BloodAndBones;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;

/**
 * Taking a carcass apart. A Cleaver cut on an attached limb wounds it and enough cuts sever the joint, the
 * limb becoming a carcass of its own; Cleaver cuts on a loose piece break it down into meat, bone and offal
 * by the mob's butchery table. A Flensing Knife takes the hide off the whole carcass first.
 */
public final class CarcassButchery {
    /** Cleaver cuts it takes to get through a joint. */
    public static final int CUTS_TO_SEVER = 3;

    private CarcassButchery() {
    }

    /**
     * One cut on {@code bone} at {@code hitWorld}.
     *
     * @return true if the cut did anything
     */
    public static boolean cut(ServerLevel level, @Nullable Player player, CarcassSavedData.Carcass carcass, String bone, @Nullable Vector3d hitWorld) {
        if (carcass.resting) {
            if (CarcassRest.split(level, carcass) == null) {
                return false;
            }
        }
        if (carcass.severed.contains(bone) || !carcass.bones.containsKey(bone)) {
            return false;
        }
        boolean attached = isAttached(carcass, bone);
        if (attached && bone.equals(carcass.rootBone)) {
            return false; // the body cannot be cut through while limbs hang off it
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        UUID id = carcass.bones.get(bone);
        Vector3d at = hitWorld;
        if (at == null && container != null && container.getSubLevel(id) instanceof ServerSubLevel limb && !limb.isRemoved()) {
            at = new Vector3d(limb.logicalPose().position());
        }
        int cuts = carcass.cuts.merge(bone, 1, Integer::sum);
        if (player != null && Blood.bloody(carcass)) {
            Blood.bloody(blade(player, com.avicagan.bloodandbones.item.CleaverItem.class), level);
        }
        if (at != null) {
            Blood.wound(level, carcass, at, 8, 1);
            level.playSound(null, at.x, at.y, at.z, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_CUT.get(), SoundSource.BLOCKS, 0.8F, 0.7F);
        }
        if (attached && cuts >= CUTS_TO_SEVER) {
            sever(level, carcass, bone, at);
        } else if (!attached && cuts >= CUTS_TO_BUTCHER) {
            butcher(level, carcass, bone, at);
        }
        CarcassSavedData.get(level).setDirty();
        return true;
    }

    /** Cleaver cuts it takes to break a loose piece down into meat. */
    public static final int CUTS_TO_BUTCHER = 3;
    /** Flensing Knife strokes it takes to take the hide off a carcass. */
    public static final int STROKES_TO_SKIN = 4;

    /** Whether any joint still ties this bone to another. */
    public static boolean isAttached(CarcassSavedData.Carcass carcass, String bone) {
        for (CarcassJoints.Spec joint : carcass.joints) {
            if (joint.child().equals(bone) || joint.parent().equals(bone)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Break a loose piece down into what its butchery table says, spoiled as far as the carcass has rotted.
     * The body goes; whatever else was in the record without a joint to it becomes its own carcass.
     */
    public static void butcher(ServerLevel level, CarcassSavedData.Carcass carcass, String bone, @Nullable Vector3d at) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        UUID id = carcass.bones.get(bone);
        if (container == null || id == null || !(container.getSubLevel(id) instanceof ServerSubLevel body) || body.isRemoved()) {
            return;
        }
        Vector3d where = new Vector3d(body.logicalPose().position());
        com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(carcass.entity)
                .ifPresent(table -> dropYields(level, carcass, table.part(bone), 1.0F, where));
        if (Boolean.TRUE.equals(MANGLING.get()) && SINK.get() != null) {
            net.minecraft.world.item.ItemStack scraps = scraps(level, carcass, bone);
            if (!scraps.isEmpty()) {
                SINK.get().accept(scraps);
            }
        }
        carcass.cuts.remove(bone);
        CarcassSavedData data = CarcassSavedData.get(level);
        boolean wasRoot = bone.equals(carcass.rootBone);
        container.removeSubLevel(body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        if (wasRoot) {
            // nothing is left to hold the rest of this record together: each remaining piece stands alone
            // (a piece jointed to one split off before it has already gone with that one)
            for (String other : java.util.List.copyOf(carcass.bones.keySet())) {
                if (carcass.bones.containsKey(other)) {
                    data.splitOff(level, carcass, other);
                }
            }
            if (carcass.bones.isEmpty()) {
                data.forget(carcass);
            }
        } else {
            // the stump it came off shows the wound at once
            CarcassRot.sync(level, carcass, null);
        }
        Blood.wound(level, carcass, where, 24, 3);
        level.playSound(null, where.x, where.y, where.z, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_CUT.get(), SoundSource.BLOCKS, 1.0F, 0.5F);
        data.setDirty();
        BloodAndBones.LOGGER.debug("Butchered {} of carcass {}", bone, carcass.id);
    }

    /** The blade of this kind the player is working with: the main hand's, or else the off hand's. */
    private static net.minecraft.world.item.ItemStack blade(Player player, Class<?> kind) {
        return kind.isInstance(player.getMainHandItem().getItem()) ? player.getMainHandItem() : player.getOffhandItem();
    }

    /**
     * One Flensing Knife stroke. Enough of them take the hide off: the hide (and anything the table adds,
     * like a sheep's wool) drops, and the carcass shows bare meat from then on.
     *
     * @return true if the stroke did anything
     */
    public static boolean skin(ServerLevel level, @Nullable Player player, CarcassSavedData.Carcass carcass, @Nullable Vector3d at) {
        if (carcass.skinned) {
            return false;
        }
        var table = com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(carcass.entity).orElse(null);
        if (table == null || table.hide().isEmpty()) {
            return false; // nothing to skin: bones, rotten flesh
        }
        Vector3d where = at != null ? at : CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
        if (where == null) {
            return false;
        }
        carcass.skinStrokes++;
        if (player != null && Blood.bloody(carcass)) {
            Blood.bloody(blade(player, com.avicagan.bloodandbones.item.FlensingKnifeItem.class), level);
        }
        Blood.wound(level, carcass, where, 4, 0);
        level.playSound(null, where.x, where.y, where.z, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_SKIN.get(), SoundSource.BLOCKS, 0.8F, 1.2F);
        if (carcass.skinStrokes < STROKES_TO_SKIN) {
            return true;
        }
        carcass.skinStrokes = 0;
        dropYields(level, carcass, table.hide(), shareOfAnimal(carcass), where);
        carcass.skinned = true;
        carcass.look = CarcassLook.flesh();
        CarcassRot.sync(level, carcass, null);
        Blood.wound(level, carcass, where, 16, 2);
        CarcassSavedData.get(level).setDirty();
        return true;
    }

    /** How much of the whole animal this record still is, by bone volume (a lone leg is a small hide). */
    public static float shareOfAnimal(CarcassSavedData.Carcass carcass) {
        var rig = com.avicagan.bloodandbones.carcass.rig.RigManager.forCarcass(carcass).orElse(null);
        if (rig == null) {
            return 1.0F;
        }
        float all = 0.0F;
        float here = 0.0F;
        for (var bone : rig.bones()) {
            org.joml.Vector3f size = bone.boxSize();
            float volume = size.x * size.y * size.z;
            all += volume;
            if (carcass.bones.containsKey(bone.name()) || carcass.restPoses.containsKey(bone.name())) {
                here += volume;
            }
        }
        return all <= 0.0F ? 1.0F : Math.min(1.0F, here / all);
    }

    /**
     * The tables are for grown animals: a baby gives as much less as it weighs less. The smallest slime is a
     * quarter the size of a big one but weighs a 64th, which would leave it next to nothing; it gives a
     * quarter (one slime ball, about what the game drops for one). The smallest magma cube gives nothing,
     * as in the game, where only bigger ones drop magma cream.
     */
    public static float babyYieldScale(CarcassSavedData.Carcass carcass) {
        if (!carcass.baby) {
            return 1.0F;
        }
        if (carcass.entity.equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(net.minecraft.world.entity.EntityType.SLIME))) {
            return 0.25F;
        }
        if (carcass.entity.equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(net.minecraft.world.entity.EntityType.MAGMA_CUBE))) {
            return 0.0F;
        }
        float grown = com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(carcass.entity).map(com.avicagan.bloodandbones.carcass.rig.Rig::weight).orElse(1.0F);
        float small = com.avicagan.bloodandbones.carcass.rig.RigManager.forCarcass(carcass).map(com.avicagan.bloodandbones.carcass.rig.Rig::weight).orElse(grown);
        return Math.min(1.0F, small / Math.max(grown, 1.0E-4F));
    }

    /**
     * Spawn a list of yields at a point, scaled, with rot applied: past 60% fresh everything is whole; past
     * 30% meat, offal and fat are halved; below that meat turns to rotten flesh (halved), offal and fat are
     * gone and hides halved; a rotten carcass gives no hide at all. Bones never spoil.
     */
    public static void dropYields(ServerLevel level, CarcassSavedData.Carcass carcass, java.util.List<com.avicagan.bloodandbones.carcass.butchery.Yield> yields,
                                  float scale, Vector3d at) {
        float fresh = carcass.freshness;
        scale *= babyYieldScale(carcass);
        for (var yield : yields) {
            String id = fillTraits(yield.item(), carcass.traits);
            if (id == null) {
                continue;
            }
            float count = yield.count() * scale;
            switch (yield.kind()) {
                case "meat", "offal", "fat" -> {
                    if (fresh < 0.3F) {
                        if (!yield.kind().equals("meat")) {
                            continue;
                        }
                        id = "minecraft:rotten_flesh";
                        count *= 0.5F;
                    } else if (fresh < 0.6F) {
                        count *= 0.5F;
                    }
                }
                case "hide" -> {
                    if (fresh <= 0.0F) {
                        continue;
                    }
                    if (fresh < 0.3F) {
                        count *= 0.5F;
                    }
                }
                default -> {
                }
            }
            net.minecraft.resources.ResourceLocation key = net.minecraft.resources.ResourceLocation.tryParse(id);
            var item = key == null ? java.util.Optional.<net.minecraft.world.item.Item>empty() : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(key);
            if (item.isEmpty()) {
                BloodAndBones.LOGGER.warn("Butchery yield {} of {} is not an item", id, carcass.entity);
                continue;
            }
            int n = (int) Math.floor(count) + (level.random.nextFloat() < count - Math.floor(count) ? 1 : 0);
            while (n > 0) {
                int stackSize = Math.min(n, item.get().getDefaultMaxStackSize());
                n -= stackSize;
                java.util.function.Consumer<net.minecraft.world.item.ItemStack> sink = SINK.get();
                if (sink != null) {
                    sink.accept(new net.minecraft.world.item.ItemStack(item.get(), stackSize));
                    continue;
                }
                net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y + 0.25, at.z,
                        new net.minecraft.world.item.ItemStack(item.get(), stackSize));
                entity.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.15, 0.2, (level.random.nextDouble() - 0.5) * 0.15);
                entity.setDefaultPickUpDelay();
                level.addFreshEntity(entity);
            }
        }
    }

    /** Where yields go instead of the ground while a machine is working, else null. */
    private static final ThreadLocal<java.util.function.Consumer<net.minecraft.world.item.ItemStack>> SINK = new ThreadLocal<>();

    /**
     * What butchering a carried piece gives, as items: the piece's share of its mob's table, spoiled as far
     * as it had rotted and scaled for a baby, as if it were a loose piece cut up in the world.
     */
    public static java.util.List<net.minecraft.world.item.ItemStack> pieceYields(ServerLevel level, com.avicagan.bloodandbones.item.CarcassPieceItem.Piece piece) {
        java.util.List<net.minecraft.world.item.ItemStack> out = new java.util.ArrayList<>();
        CarcassSavedData.Carcass stand = new CarcassSavedData.Carcass(UUID.randomUUID(), piece.entity(), piece.bone());
        stand.freshness = piece.freshness();
        stand.baby = piece.baby();
        stand.traits.putAll(piece.traits());
        com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(piece.entity()).ifPresent(table ->
                capturing(out::add, () -> {
                    dropYields(level, stand, table.part(piece.bone()), 1.0F, new Vector3d());
                    return true;
                }));
        return out;
    }

    /** Set while the Mangler grinds: pieces it butchers give armour scraps too. */
    private static final ThreadLocal<Boolean> MANGLING = new ThreadLocal<>();

    /** As {@link #capturing}, for the Mangler: each piece ground also gives its scraps. */
    public static <T> T mangling(java.util.function.Consumer<net.minecraft.world.item.ItemStack> sink, java.util.function.Supplier<T> action) {
        Boolean previous = MANGLING.get();
        MANGLING.set(true);
        try {
            return capturing(sink, action);
        } finally {
            MANGLING.set(previous);
        }
    }

    /**
     * The armour scraps one piece grinds into (docs/PARTS-AND-TRAITS.md section 7.1): its volume in blocks
     * times its material's density, half again if it was skinned (the hide is not in the way), half if it had
     * rotted, at least one; they remember the mob and the part.
     */
    public static net.minecraft.world.item.ItemStack scraps(ServerLevel level, CarcassSavedData.Carcass carcass, String bone) {
        var rig = com.avicagan.bloodandbones.carcass.rig.RigManager.forCarcass(carcass).orElse(null);
        if (rig == null) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        com.avicagan.bloodandbones.parts.PartsData.Store store = com.avicagan.bloodandbones.parts.PartsData.SERVER;
        String part = com.avicagan.bloodandbones.parts.PartSlots.of(store, carcass.entity, rig, bone).slot().scrapPart();
        float count = scrapCount(store, carcass.entity, carcass.baby, rig, bone, carcass.skinned, carcass.freshness);
        int n = Math.max(1, (int) Math.floor(count) + (level.random.nextFloat() < count - Math.floor(count) ? 1 : 0));
        return com.avicagan.bloodandbones.parts.ScrapsItem.of(new com.avicagan.bloodandbones.parts.Source(carcass.entity, part, carcass.baby), n);
    }

    /** How many scraps a bone is worth, before the fraction is rolled. */
    public static float scrapCount(com.avicagan.bloodandbones.parts.PartsData.Store store, net.minecraft.resources.ResourceLocation entity, boolean baby,
                                   com.avicagan.bloodandbones.carcass.rig.Rig rig, String bone, boolean skinned, float freshness) {
        var box = rig.bone(bone).map(com.avicagan.bloodandbones.carcass.rig.Bone::boxSize).orElse(new org.joml.Vector3f());
        float volume = box.x * box.y * box.z / 4096.0F;
        float count = volume * store.material(store.resolve(entity, baby).material()).density();
        if (skinned) {
            count *= 1.5F;
        }
        if (freshness < 0.3F) {
            count *= 0.5F;
        }
        return count;
    }

    /** Run a butchery action with every yield it makes handed to {@code sink} instead of dropped. */
    public static <T> T capturing(java.util.function.Consumer<net.minecraft.world.item.ItemStack> sink, java.util.function.Supplier<T> action) {
        java.util.function.Consumer<net.minecraft.world.item.ItemStack> previous = SINK.get();
        SINK.set(sink);
        try {
            return action.get();
        } finally {
            SINK.set(previous);
        }
    }

    /** Fill {trait} placeholders; null when the carcass lacks one (an unsheared sheep's wool, say). */
    @Nullable
    static String fillTraits(String pattern, Map<String, String> traits) {
        String text = pattern;
        int open = text.indexOf('{');
        while (open >= 0) {
            int close = text.indexOf('}', open);
            if (close < 0) {
                return null;
            }
            String value = traits.get(text.substring(open + 1, close));
            if (value == null) {
                return null;
            }
            text = text.substring(0, open) + value + text.substring(close + 1);
            open = text.indexOf('{');
        }
        return text;
    }

    /** Cut the joint between a limb and its parent for good. */
    public static void sever(ServerLevel level, CarcassSavedData.Carcass carcass, String bone, @Nullable Vector3d at) {
        carcass.joints.removeIf(joint -> joint.child().equals(bone));
        carcass.severed.add(bone);
        carcass.cuts.remove(bone);
        // live joints are not tied to their specs; drop them all and let the root tick rebuild the survivors
        for (PhysicsConstraintHandle handle : carcass.liveJoints) {
            if (handle.isValid()) {
                handle.remove();
            }
        }
        carcass.liveJoints.clear();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            for (UUID id : carcass.bones.values()) {
                if (container.getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved()) {
                    container.physicsSystem().getPipeline().wakeUp(body);
                }
            }
        }
        if (at != null) {
            Blood.wound(level, carcass, at, 30, 3);
            level.playSound(null, at.x, at.y, at.z, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_SEVER.get(), SoundSource.BLOCKS, 1.0F, 0.6F);
        }
        // the piece is a carcass of its own from here: it rests, rots and is hooked on its own terms
        CarcassSavedData.Carcass piece = CarcassSavedData.get(level).splitOff(level, carcass, bone);
        CarcassSavedData.get(level).setDirty();
        // both ends show the wound at once, and pour for a while
        String parent = com.avicagan.bloodandbones.carcass.rig.RigManager.forCarcass(carcass)
                .flatMap(rig -> rig.bone(bone)).flatMap(com.avicagan.bloodandbones.carcass.rig.Bone::parent).orElse(null);
        CarcassRot.sync(level, carcass, null);
        if (parent != null) {
            CarcassBleeding.freshCut(carcass, parent, bone);
        }
        if (piece != null) {
            CarcassRot.sync(level, piece, null);
            if (parent != null) {
                CarcassBleeding.freshCut(piece, parent, bone);
            }
        }
        BloodAndBones.LOGGER.debug("Severed {} from carcass {}", bone, carcass.id);
    }

    /** A body no heavier than this (Sable mass units) can be picked up by hand: heads, legs, a whole chicken. */
    public static final double LIGHT_MASS = 0.13;

    /** Whether a bone of a carcass is light enough to carry. */
    public static boolean canPickUp(ServerLevel level, CarcassSavedData.Carcass carcass, String bone) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        UUID id = carcass.bones.get(bone);
        if (container == null || id == null || !(container.getSubLevel(id) instanceof ServerSubLevel body) || body.isRemoved()) {
            return false;
        }
        return body.getMassTracker().getMass() <= LIGHT_MASS;
    }

    /**
     * Take a light piece into the hand as an item. The piece must be a whole carcass record of its own
     * (a severed limb, or a light animal's single-bone remains), or the bone must be free of joints.
     */
    public static boolean pickUp(ServerLevel level, Player player, CarcassSavedData.Carcass carcass, String bone) {
        if (carcass.resting) {
            if (CarcassRest.split(level, carcass) == null) {
                return false;
            }
        }
        if (!canPickUp(level, carcass, bone)) {
            return false;
        }
        for (CarcassJoints.Spec joint : carcass.joints) {
            if (joint.child().equals(bone) || joint.parent().equals(bone)) {
                return false; // still attached: cut it off first
            }
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        UUID id = carcass.bones.get(bone);
        if (container == null || !(container.getSubLevel(id) instanceof ServerSubLevel body) || body.isRemoved()) {
            return false;
        }
        net.minecraft.world.item.ItemStack stack = com.avicagan.bloodandbones.item.CarcassPieceItem.of(carcass, bone);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        Vector3d at = new Vector3d(body.logicalPose().position());
        container.removeSubLevel(body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        level.playSound(null, at.x, at.y, at.z, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_PICK_UP.get(), SoundSource.BLOCKS, 0.6F, 0.8F);
        return true;
    }

    /** How many joints a carcass still has to hold it together. */
    public static int intactJoints(CarcassSavedData.Carcass carcass) {
        return carcass.joints.size();
    }

    static int cutsOn(CarcassSavedData.Carcass carcass, String bone) {
        return carcass.cuts.getOrDefault(bone, 0);
    }

    static Map<String, Integer> cuts(CarcassSavedData.Carcass carcass) {
        return carcass.cuts;
    }
}
