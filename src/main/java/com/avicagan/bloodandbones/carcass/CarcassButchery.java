package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.BloodAndBones;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import com.avicagan.bloodandbones.carcass.butchery.ButcheryPath;
import com.avicagan.bloodandbones.carcass.butchery.ButcheryPaths;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
 * <p>
 * How much of the table comes out depends on the path taking the carcass apart ({@link ButcheryPaths}): a blade in
 * a player's hand gets about half, with real loss; a machine or a Deployer at a station gets all of it; the Mangler
 * the least, but the mob's own drops and armour scraps on top. The path is set around the work with {@link #onPath},
 * or {@link #byHand} for whoever holds the blade; a player's own cut or stroke with none set is by hand.
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
        if (PATH.get() == null && handOf(player)) {
            return byHand(player, () -> cut(level, player, carcass, bone, hitWorld));
        }
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
        ButcheryPath path = path();
        if (path.scraps()) {
            net.minecraft.world.item.ItemStack scraps = scraps(level, carcass, bone);
            if (!scraps.isEmpty()) {
                emit(level, scraps, where);
            }
        }
        if (path.lootTable() && isTorso(carcass, bone)) {
            // the grinder takes what the mob would have dropped had it died any other way, once, with its body
            rollLoot(level, carcass, where);
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
        if (PATH.get() == null && handOf(player)) {
            return byHand(player, () -> skin(level, player, carcass, at));
        }
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
        return flay(level, carcass, where);
    }

    /**
     * The hide comes off at once, on whatever path is set (the last stroke of a knife or a Deglover, or one cut at the
     * Surgical Rig): the record's share of the hide (and anything the table adds, like a sheep's wool) drops, and the
     * carcass shows bare meat from then on.
     *
     * @return false if it was already skinned or has no hide to give
     */
    public static boolean flay(ServerLevel level, CarcassSavedData.Carcass carcass, @Nullable Vector3d at) {
        var table = com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(carcass.entity).orElse(null);
        Vector3d where = at != null ? at : CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
        if (carcass.skinned || table == null || table.hide().isEmpty() || where == null) {
            return false;
        }
        carcass.skinStrokes = 0;
        dropYields(level, carcass, table.hide(), shareOfAnimal(carcass), where);
        carcass.skinned = true;
        carcass.look = CarcassLook.flesh();
        CarcassRot.sync(level, carcass, null);
        Blood.wound(level, carcass, where, 16, 2);
        level.playSound(null, where.x, where.y, where.z, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_SKIN.get(), SoundSource.BLOCKS, 1.0F, 0.9F);
        CarcassSavedData.get(level).setDirty();
        return true;
    }

    /**
     * A piece ground whole with its hide still on (the Mangler's way): its share of the hide, at the path's share for
     * hide. The Mangler's is none, so it grinds the hide away; a datapack that gives it some gets that much hide back.
     */
    public static void groundHide(ServerLevel level, CarcassSavedData.Carcass carcass, String bone, Vector3d at) {
        if (carcass.skinned || path().share("hide") <= 0.0F) {
            return;
        }
        var table = com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(carcass.entity).orElse(null);
        var rig = com.avicagan.bloodandbones.carcass.rig.RigManager.forCarcass(carcass).orElse(null);
        if (table != null && rig != null && !table.hide().isEmpty()) {
            dropYields(level, carcass, table.hide(), shareOfAnimal(rig, bone::equals), at);
        }
    }

    /** How much of the whole animal this record still is, by bone volume (a lone leg is a small hide). */
    public static float shareOfAnimal(CarcassSavedData.Carcass carcass) {
        return com.avicagan.bloodandbones.carcass.rig.RigManager.forCarcass(carcass)
                .map(rig -> shareOfAnimal(rig, bone -> carcass.bones.containsKey(bone) || carcass.restPoses.containsKey(bone))).orElse(1.0F);
    }

    /** How much of the whole animal these bones of its rig are, by volume. */
    public static float shareOfAnimal(com.avicagan.bloodandbones.carcass.rig.Rig rig, java.util.function.Predicate<String> has) {
        float all = 0.0F;
        float here = 0.0F;
        for (var bone : rig.bones()) {
            org.joml.Vector3f size = bone.boxSize();
            float volume = size.x * size.y * size.z;
            all += volume;
            if (has.test(bone.name())) {
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
        // a poor minion butcher wastes some of each cut (see yielding)
        Float share = YIELD.get();
        if (share != null) {
            scale *= share;
        }
        ButcheryPath path = path();
        Context context = PATH.get();
        float hand = context == null ? 1.0F : context.yield();
        for (var yield : yields) {
            String id = fillTraits(yield.item(), carcass.traits);
            if (id == null) {
                continue;
            }
            float count = yield.count() * scale * path.share(yield.kind()) * hand;
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
            int n = roll(level, count);
            if (path.loss() > 0.0F) {
                // each whole piece the blade could have had is a chance to botch it
                int kept = 0;
                for (int i = 0; i < n; i++) {
                    if (level.random.nextFloat() >= path.loss()) {
                        kept++;
                    }
                }
                n = kept;
            }
            while (n > 0) {
                int stackSize = Math.min(n, item.get().getDefaultMaxStackSize());
                n -= stackSize;
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item.get(), stackSize);
                if (yield.kind().equals("hide")) {
                    // a hide remembers whose it was, for fitting over carcass armour, where the item alone would not say
                    com.avicagan.bloodandbones.parts.Hides.stamp(stack, carcass.entity);
                }
                emit(level, stack, at);
            }
        }
    }

    /** An expected count as a whole number: the fraction is the chance of one more. */
    private static int roll(ServerLevel level, float count) {
        return (int) Math.floor(count) + (level.random.nextFloat() < count - Math.floor(count) ? 1 : 0);
    }

    /** Into the sink if a machine is working, else thrown out of the carcass at that point. */
    private static void emit(ServerLevel level, net.minecraft.world.item.ItemStack stack, Vector3d at) {
        java.util.function.Consumer<net.minecraft.world.item.ItemStack> sink = SINK.get();
        if (sink != null) {
            sink.accept(stack);
            return;
        }
        net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y + 0.25, at.z, stack);
        entity.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.15, 0.2, (level.random.nextDouble() - 0.5) * 0.15);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }

    /** Whether this bone is the mob's torso (the rig's root), not a piece that became a record of its own. */
    public static boolean isTorso(CarcassSavedData.Carcass carcass, String bone) {
        return com.avicagan.bloodandbones.carcass.rig.RigManager.forCarcass(carcass).map(rig -> rig.root().name().equals(bone))
                .orElse(bone.equals(carcass.rootBone));
    }

    /**
     * The mob's own loot table, rolled as if it had died with nobody to blame, for the carcass it left: a grown one's, as
     * it was (a black sheep's wool is black; a sheep skinned already, or sheared when it died, has none; a big magma cube
     * is big), and nothing for a baby, as in the game, or with mob loot turned off.
     * <p>
     * What its butchery table also gives is left out: the path taking the carcass apart already took its share of that
     * from the table (the Mangler a quarter of the meat and bone, none of the hide), and the drop on top would give more
     * than a careful station does. So the Mangler gets the drops no table gives: a cow's leather, a zombie's rare iron, a
     * skeleton's arrows.
     */
    public static void rollLoot(ServerLevel level, CarcassSavedData.Carcass carcass, Vector3d at) {
        net.minecraft.world.entity.LivingEntity mob = lootMob(level, carcass);
        if (mob == null || mob.isBaby() || !level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT)) {
            return;
        }
        mob.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        java.util.Set<net.minecraft.world.item.Item> tabled = tableItems(carcass);
        var table = level.getServer().reloadableRegistries().getLootTable(mob.getLootTable());
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, mob)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, mob.position())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
        table.getRandomItems(params, stack -> {
            if (!tabled.contains(stack.getItem())) {
                emit(level, stack, at);
            }
        });
    }

    /**
     * A fresh instance of the carcass's mob, never added to the world, set as the carcass has it where its loot reads
     * it: a baby's age; a slime's size (the rig's baby is the smallest, else the biggest a carcass comes from); a sheep's
     * wool colour, and sheared if its wool is gone.
     */
    @Nullable
    private static net.minecraft.world.entity.LivingEntity lootMob(ServerLevel level, CarcassSavedData.Carcass carcass) {
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(carcass.entity);
        if (type.isEmpty() || !(type.get().create(level) instanceof net.minecraft.world.entity.LivingEntity mob)) {
            return null;
        }
        if (mob instanceof net.minecraft.world.entity.monster.Slime slime) {
            slime.setSize(carcass.baby ? 1 : 4, false);
        } else if (carcass.baby && mob instanceof net.minecraft.world.entity.Mob young) {
            young.setBaby(true);
            if (!young.isBaby()) {
                // a mob the game never raises young of: what the rig calls its baby drops nothing all the same
                return null;
            }
        }
        if (mob instanceof net.minecraft.world.entity.animal.Sheep sheep) {
            String wool = carcass.traits.get("wool");
            net.minecraft.world.item.DyeColor colour = wool == null ? null : net.minecraft.world.item.DyeColor.byName(wool, null);
            if (colour != null) {
                sheep.setColor(colour);
            }
            sheep.setSheared(colour == null || carcass.skinned);
        }
        return mob;
    }

    /** Every item the carcass's mob's butchery table gives, its hide and all its parts, with its traits filled in. */
    private static java.util.Set<net.minecraft.world.item.Item> tableItems(CarcassSavedData.Carcass carcass) {
        java.util.Set<net.minecraft.world.item.Item> items = new java.util.HashSet<>();
        com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(carcass.entity).ifPresent(table -> {
            java.util.List<com.avicagan.bloodandbones.carcass.butchery.Yield> all = new java.util.ArrayList<>(table.hide());
            table.parts().values().forEach(all::addAll);
            for (var yield : all) {
                String id = fillTraits(yield.item(), carcass.traits);
                net.minecraft.resources.ResourceLocation key = id == null ? null : net.minecraft.resources.ResourceLocation.tryParse(id);
                if (key != null) {
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(key).ifPresent(items::add);
                }
            }
        });
        return items;
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

    /**
     * What flaying a carried piece gives, as items: its share of its mob's hide, by its bone's volume, spoiled as far as it
     * had rotted and scaled for a baby. Nothing for a piece already skinned, or a mob with no hide.
     */
    public static java.util.List<net.minecraft.world.item.ItemStack> pieceHide(ServerLevel level, com.avicagan.bloodandbones.item.CarcassPieceItem.Piece piece) {
        java.util.List<net.minecraft.world.item.ItemStack> out = new java.util.ArrayList<>();
        var table = com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(piece.entity()).orElse(null);
        var rig = com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(piece.entity(), piece.baby()).orElse(null);
        if (piece.skinned() || table == null || table.hide().isEmpty() || rig == null) {
            return out;
        }
        CarcassSavedData.Carcass stand = new CarcassSavedData.Carcass(UUID.randomUUID(), piece.entity(), piece.bone());
        stand.freshness = piece.freshness();
        stand.baby = piece.baby();
        stand.traits.putAll(piece.traits());
        float share = shareOfAnimal(rig, bone -> bone.equals(piece.bone()));
        capturing(out::add, () -> {
            dropYields(level, stand, table.hide(), share, new Vector3d());
            return true;
        });
        return out;
    }

    /** The path the butchery on this thread is taking and how good the hand at it is (1 but for a hand); null: a station's. */
    private record Context(ResourceLocation path, float yield) {
    }

    private static final ThreadLocal<Context> PATH = new ThreadLocal<>();

    /** The path the work on this thread takes: the station's (everything) when none is set. */
    public static ButcheryPath path() {
        Context context = PATH.get();
        return ButcheryPaths.get(context == null ? ButcheryPaths.STATION : context.path());
    }

    /** Run some butchery along a path; {@code yield} scales what it gets (a hand's butchery yield, else 1). */
    public static <T> T onPath(ResourceLocation path, float yield, java.util.function.Supplier<T> action) {
        Context previous = PATH.get();
        PATH.set(new Context(path, yield));
        try {
            return action.get();
        } finally {
            PATH.set(previous);
        }
    }

    /**
     * Butchery with a blade in this one's hand: a player's own (not a Deployer's stand-in) or a minion's is the hand
     * path, scaled by their butchery yield; anyone else's is a station's.
     */
    public static <T> T byHand(@Nullable net.minecraft.world.entity.LivingEntity who, java.util.function.Supplier<T> action) {
        if (who == null || who instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            return onPath(ButcheryPaths.STATION, 1.0F, action);
        }
        var attribute = who.getAttribute(com.avicagan.bloodandbones.registry.BBAttributes.BUTCHERY_YIELD);
        return onPath(ButcheryPaths.HAND, attribute == null ? 1.0F : (float) attribute.getValue(), action);
    }

    /** Whether a cut or stroke by this player is by hand (a real one; a Deployer's stand-in works as a station). */
    private static boolean handOf(@Nullable Player player) {
        return player != null && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer);
    }

    /** As {@link #capturing}, for the Mangler: its path, so each piece ground gives its scraps, and the body the mob's drops. */
    public static <T> T mangling(java.util.function.Consumer<net.minecraft.world.item.ItemStack> sink, java.util.function.Supplier<T> action) {
        return onPath(ButcheryPaths.MANGLER, 1.0F, () -> capturing(sink, action));
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

    /** The share of its yields a butchery action gives while a minion butcher works it, else null (all of them). */
    private static final ThreadLocal<Float> YIELD = new ThreadLocal<>();

    /**
     * Run a butchery action (a cut, a stroke of skinning) with every yield it makes scaled by {@code share}, as
     * {@link #capturing} hands them on: a minion butcher below 100% wastes that much of each (docs/NEXT.md 1.2). A share over
     * 1 counts as 1, so no butcher beats hand yields.
     */
    public static <T> T yielding(float share, java.util.function.Supplier<T> action) {
        Float previous = YIELD.get();
        YIELD.set(Math.max(0.0F, Math.min(1.0F, share)) * (previous == null ? 1.0F : previous));
        try {
            return action.get();
        } finally {
            YIELD.set(previous);
        }
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

    /**
     * Cut the joint between a limb and its parent for good.
     *
     * @return the limb's own record from now on (with anything that hung off it), or null if it could not be split off
     */
    @Nullable
    public static CarcassSavedData.Carcass sever(ServerLevel level, CarcassSavedData.Carcass carcass, String bone, @Nullable Vector3d at) {
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
        return piece;
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

    /** A carcass part lying on a work surface, and where. */
    public record Lying(CarcassSavedData.Carcass carcass, String bone, Vector3d at) {
    }

    /**
     * The carcass parts lying on top of a block whose top is {@code top} blocks above its floor (a table top): each one's
     * middle over the block and not far above that top, nearest the middle of the top first. Bodies being dragged are left
     * out: they are only passing; so are bodies hanging from a hook or a trolley, which only sway over it.
     */
    public static java.util.List<Lying> lyingOn(ServerLevel level, net.minecraft.core.BlockPos pos, double top) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        java.util.List<Lying> found = new java.util.ArrayList<>();
        if (container == null) {
            return found;
        }
        net.minecraft.world.phys.AABB over = new net.minecraft.world.phys.AABB(pos.getX() - 0.25, pos.getY() + top - 0.3, pos.getZ() - 0.25,
                pos.getX() + 1.25, pos.getY() + top + 1.5, pos.getZ() + 1.25);
        for (CarcassSavedData.Carcass carcass : CarcassSavedData.get(level).all()) {
            if (CarcassDrag.isDraggingCarcass(carcass.id)) {
                continue;
            }
            Boolean hanging = null;
            for (Map.Entry<String, UUID> bone : carcass.bones.entrySet()) {
                if (container.getSubLevel(bone.getValue()) instanceof ServerSubLevel body && !body.isRemoved()) {
                    org.joml.Vector3dc p = body.logicalPose().position();
                    if (over.contains(p.x(), p.y(), p.z())) {
                        if (hanging == null) {
                            hanging = CarcassRest.isHeld(level, carcass);
                        }
                        if (hanging) {
                            break;
                        }
                        found.add(new Lying(carcass, bone.getKey(), new Vector3d(p)));
                    }
                }
            }
        }
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + top;
        double cz = pos.getZ() + 0.5;
        found.sort(java.util.Comparator.comparingDouble(lying -> lying.at().distanceSquared(cx, cy, cz)));
        return found;
    }

    /**
     * Take a whole carcass out of the world (onto a spit): the record is forgotten first, so taking its bodies away does
     * not split what is left into new records (as CarcassRot#crumble does), then its joints and bodies go.
     */
    public static void takeAway(ServerLevel level, CarcassSavedData.Carcass carcass) {
        CarcassSavedData.get(level).forget(carcass);
        CarcassRest.unlock(carcass);
        for (PhysicsConstraintHandle handle : carcass.liveJoints) {
            if (handle.isValid()) {
                handle.remove();
            }
        }
        carcass.liveJoints.clear();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            for (UUID id : java.util.List.copyOf(carcass.bones.values())) {
                if (container.getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved()) {
                    container.removeSubLevel(body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
                }
            }
        }
    }

    /** Whether this bone of a carcass has a butchery table entry to break it down into. */
    public static boolean hasYields(CarcassSavedData.Carcass carcass, String bone) {
        return com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(carcass.entity)
                .map(table -> !table.part(bone).isEmpty()).orElse(false);
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
