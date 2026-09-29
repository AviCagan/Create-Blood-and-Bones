package com.avicagan.bloodandbones.body;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.item.CleaverItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.joml.Vector3d;

/**
 * Operations at the Surgery Table on whoever lies on it. What can be done to a part depends on the part
 * and on what lies on the table: a blade takes a part of flesh out (a limb, an eye, an organ); an implant
 * that fits takes a part of flesh's place in one go, or goes where one is missing; a part of flesh goes back
 * where one is missing; and an implant comes out with nothing needed. Nothing can go wrong.
 * <p>
 * The brief's ritual: cutting flesh off a player (taking it off, or swapping it for an implant) needs a
 * surgeon minion awake by the table, and a part it takes off leaves a ragged stump; fitting anything but a
 * crude prosthetic into a ragged stump later takes a bucket of blood as well. Fitting, reattaching, swapping
 * implants and modules never need a surgeon, and a crude prosthetic never needs blood: the safety floor is
 * always in reach.
 */
public final class Surgery {
    public enum Action {
        NONE, TAKE_OFF, REPLACE, SWAP, FIT, REATTACH, UNCLIP, FIT_MODULE, TAKE_MODULE;

        public String translationKey() {
            return "bloodandbones.surgery.action." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private Surgery() {
    }

    /** How near the table a surgeon minion must stand. */
    public static final double SURGEON_REACH = 4.0;
    /** What fitting into a ragged stump costs on top, in mB of blood. */
    public static final int RAGGED_BLOOD = 1000;

    /** An awake surgeon minion by this table, if there is one. */
    @org.jetbrains.annotations.Nullable
    public static com.avicagan.bloodandbones.minion.MinionEntity surgeonAt(net.minecraft.world.level.Level level, BlockPos table) {
        for (com.avicagan.bloodandbones.minion.MinionEntity minion : level.getEntitiesOfClass(com.avicagan.bloodandbones.minion.MinionEntity.class,
                new net.minecraft.world.phys.AABB(table).inflate(SURGEON_REACH))) {
            if (minion.isAlive() && !minion.poweredDown() && minion.hasJob("surgeon")) {
                return minion;
            }
        }
        return null;
    }

    /** Whether this cuts flesh away. */
    public static boolean cuts(Action action) {
        return action == Action.TAKE_OFF || action == Action.REPLACE;
    }

    /**
     * Whether this costs a bucket of blood as well: fitting into a ragged stump (the limb back, or an implant), except a
     * crude prosthetic, which always goes on for nothing, so no stump is ever left with no way back to baseline.
     */
    public static boolean costsBlood(Body body, Action action, BodyPart part, ItemStack tool) {
        return (action == Action.FIT || action == Action.REATTACH) && body.ragged(part) && !(tool.getItem() instanceof ImplantItem implant && implant.crude());
    }

    /**
     * Why this cannot be done right now, or null if it can: cutting a player needs a surgeon at the table;
     * fitting into a ragged stump (a crude prosthetic excepted) needs a bucket of blood on whoever is operating.
     * The same on both sides, for the screen.
     */
    @org.jetbrains.annotations.Nullable
    public static Component blocked(net.minecraft.world.level.Level level, net.minecraft.world.entity.LivingEntity patient,
                                    @org.jetbrains.annotations.Nullable Player operator, BlockPos table, Body body, Action action, BodyPart part, ItemStack tool) {
        if (cuts(action) && patient instanceof Player && surgeonAt(level, table) == null) {
            return Component.translatable("bloodandbones.surgery.needs_surgeon");
        }
        if (costsBlood(body, action, part, tool) && (operator == null || !payBlood(operator, false))) {
            return Component.translatable("bloodandbones.surgery.needs_blood");
        }
        return null;
    }

    /**
     * A bucket's worth of blood (any fluid tagged c:blood) from what this player carries: a bucket, a worn or
     * carried Fluid Backtank, anything holding fluid. Only looks when {@code take} is false.
     *
     * @return whether they had it
     */
    public static boolean payBlood(Player player, boolean take) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack one = stack.copyWithCount(1);
            var handler = net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(one).orElse(null);
            if (handler == null) {
                continue;
            }
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                net.neoforged.neoforge.fluids.FluidStack in = handler.getFluidInTank(tank);
                if (!in.getFluid().is(com.avicagan.bloodandbones.minion.BloodTroughBlockEntity.BLOOD) || in.getAmount() < RAGGED_BLOOD) {
                    continue;
                }
                net.neoforged.neoforge.fluids.FluidStack want = in.copyWithAmount(RAGGED_BLOOD);
                if (handler.drain(want, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE).getAmount() < RAGGED_BLOOD) {
                    continue;
                }
                if (take && !player.hasInfiniteMaterials()) {
                    handler.drain(want, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    ItemStack after = handler.getContainer();
                    if (stack.getCount() == 1) {
                        inventory.setItem(slot, after);
                    } else {
                        stack.shrink(1);
                        if (!inventory.add(after)) {
                            player.drop(after, false);
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    public static boolean isBlade(ItemStack stack) {
        return stack.getItem() instanceof CleaverItem;
    }

    /** Create's Wrench: on the table, it takes modules out of a brass limb. */
    public static boolean isWrench(ItemStack stack) {
        return stack.is(com.simibubi.create.AllItems.WRENCH.get());
    }

    /**
     * What the table takes to lie on it: a blade, an implant, a part, a carcass piece to take organs from, a
     * cybernetic module, or a wrench.
     */
    public static boolean accepts(ItemStack stack) {
        return isBlade(stack) || stack.getItem() instanceof ImplantItem || stack.getItem() instanceof SeveredLimbItem
                || stack.is(com.avicagan.bloodandbones.registry.BBItems.CARCASS_PIECE.get())
                || stack.getItem() instanceof com.avicagan.bloodandbones.cyber.ModuleItem || isWrench(stack);
    }

    /** What would be done to this part, with this on the table. The same on both sides, for the screen. */
    public static Action action(Body body, ItemStack tool, BodyPart part) {
        boolean fits = tool.getItem() instanceof ImplantItem implant && implant.fits(part)
                || tool.getItem() instanceof SeveredLimbItem limb && limb.fits(part);
        // modules go into and come out of a brass limb where it is, no amputation needed
        if (body.state(part) == Body.State.IMPLANT && tool.getItem() instanceof com.avicagan.bloodandbones.cyber.ModuleItem module) {
            return com.avicagan.bloodandbones.cyber.Modules.fits(body.implant(part), module.module()) ? Action.FIT_MODULE : Action.NONE;
        }
        if (body.state(part) == Body.State.IMPLANT && isWrench(tool)) {
            return com.avicagan.bloodandbones.cyber.Modules.of(body.implant(part)).isEmpty() ? Action.NONE : Action.TAKE_MODULE;
        }
        return switch (body.state(part)) {
            // an implant is swapped for what fits on the table, or unclipped; a heart is never left empty
            case IMPLANT -> fits ? Action.SWAP : part == BodyPart.HEART ? Action.NONE : Action.UNCLIP;
            // flesh comes out with a blade (not the heart: that is only ever replaced), or is swapped straight out
            case NATURAL -> fits ? Action.REPLACE : isBlade(tool) && part != BodyPart.HEART ? Action.TAKE_OFF : Action.NONE;
            case MISSING -> tool.getItem() instanceof ImplantItem implant && implant.fits(part) ? Action.FIT
                    : tool.getItem() instanceof SeveredLimbItem limb && limb.fits(part) ? Action.REATTACH : Action.NONE;
        };
    }

    /** On yourself. */
    public static Action operate(ServerLevel level, Player patient, SurgeryTableBlockEntity table, BodyPart part) {
        return operate(level, patient, patient, table, part);
    }

    /**
     * Do it: the patient lies on the table, and the surgeon (the patient, or someone else) works on them.
     * What comes out goes to the surgeon.
     *
     * @return what was done ({@link Action#NONE} for nothing)
     */
    public static Action operate(ServerLevel level, net.minecraft.world.entity.LivingEntity patient, @org.jetbrains.annotations.Nullable Player surgeon,
                                 SurgeryTableBlockEntity table, BodyPart part) {
        Body body = BodyEffects.body(patient);
        Action action = action(body, table.item(), part);
        BlockPos pos = table.getBlockPos();
        Vector3d at = new Vector3d(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5);
        Component problem = action == Action.NONE ? null : blocked(level, patient, surgeon, pos, body, action, part, table.item());
        if (problem != null) {
            if (surgeon != null) {
                surgeon.displayClientMessage(problem, true);
            }
            return Action.NONE;
        }
        // on a player the surgeon minion does the cutting, and leaves the stump ragged
        com.avicagan.bloodandbones.minion.MinionEntity cutter = cuts(action) && patient instanceof Player ? surgeonAt(level, pos) : null;
        if (cutter != null) {
            cutter.getLookControl().setLookAt(patient);
            cutter.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        if (costsBlood(body, action, part, table.item()) && surgeon != null) {
            payBlood(surgeon, true);
        }
        switch (action) {
            case NONE -> {
                return action;
            }
            case TAKE_OFF -> {
                body.lose(part, cutter != null);
                ItemStack blade = table.item();
                com.avicagan.bloodandbones.carcass.Blood.bloody(blade, level);
                table.setChanged();
                table.sendData();
                cutOut(level, patient, surgeon, part, pos, at);
            }
            case REPLACE -> {
                cutOut(level, patient, surgeon, part, pos, at);
                put(body, part, table.take());
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            case SWAP -> {
                give(surgeon, body.unclip(part), pos, level);
                put(body, part, table.take());
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            case FIT -> {
                body.fit(part, table.take());
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            case REATTACH -> {
                table.take();
                body.restore(part);
                com.avicagan.bloodandbones.carcass.Blood.burst(level, at, 4);
                level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            case UNCLIP -> {
                give(surgeon, body.unclip(part), pos, level);
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
            }
            case FIT_MODULE -> {
                // into a free slot, or in place of the first one when all are taken (that one comes back)
                ItemStack chassis = body.implant(part);
                java.util.List<com.avicagan.bloodandbones.cyber.Module> modules = new java.util.ArrayList<>(com.avicagan.bloodandbones.cyber.Modules.of(chassis));
                com.avicagan.bloodandbones.cyber.Module module = ((com.avicagan.bloodandbones.cyber.ModuleItem) table.take().getItem()).module();
                if (modules.size() < com.avicagan.bloodandbones.cyber.Modules.slots(chassis)) {
                    modules.add(module);
                } else {
                    give(surgeon, new ItemStack(com.avicagan.bloodandbones.registry.BBItems.module(modules.set(0, module))), pos, level);
                }
                com.avicagan.bloodandbones.cyber.Modules.set(chassis, modules);
                level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 1.3F);
            }
            case TAKE_MODULE -> {
                ItemStack chassis = body.implant(part);
                java.util.List<com.avicagan.bloodandbones.cyber.Module> modules = new java.util.ArrayList<>(com.avicagan.bloodandbones.cyber.Modules.of(chassis));
                give(surgeon, new ItemStack(com.avicagan.bloodandbones.registry.BBItems.module(modules.remove(modules.size() - 1))), pos, level);
                com.avicagan.bloodandbones.cyber.Modules.set(chassis, modules);
                level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 1.3F);
            }
        }
        patient.setData(BBAttachments.BODY, body);
        BodyEffects.changed(patient);
        return action;
    }

    /** What came off the table goes in: an implant, or a part of flesh (flesh again). */
    private static void put(Body body, BodyPart part, ItemStack item) {
        if (item.getItem() instanceof ImplantItem) {
            body.fit(part, item);
        } else {
            body.restore(part);
        }
    }

    /**
     * The part of flesh comes out, bloodily, into the surgeon's hands. A player's is named for them; a mob's is
     * named for its kind and stamped with it, as one cut out of its carcass is, so its organs fit carcass armour.
     */
    private static void cutOut(ServerLevel level, net.minecraft.world.entity.LivingEntity patient, @org.jetbrains.annotations.Nullable Player surgeon,
                               BodyPart part, BlockPos pos, Vector3d at) {
        SeveredLimbItem item = com.avicagan.bloodandbones.registry.BBItems.partItem(part.kind());
        give(surgeon, patient instanceof Player ? item.of(patient)
                : item.of(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(patient.getType()), patient.isBaby()), pos, level);
        com.avicagan.bloodandbones.carcass.Blood.burst(level, at, 12);
        com.avicagan.bloodandbones.carcass.Blood.stain(level, at, 3);
        level.playSound(null, pos, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_CUT.get(), SoundSource.PLAYERS, 1.0F, 0.9F);
    }

    private static void give(@org.jetbrains.annotations.Nullable Player player, ItemStack stack, BlockPos pos, net.minecraft.world.level.Level level) {
        if (!stack.isEmpty() && (player == null || !player.getInventory().add(stack))) {
            net.minecraft.world.level.block.Block.popResource(level, pos.above(), stack);
        }
    }

    /** Whoever lies on the table there, if anyone. */
    @org.jetbrains.annotations.Nullable
    public static net.minecraft.world.entity.LivingEntity patientAt(net.minecraft.world.level.Level level, BlockPos table) {
        for (SurgerySeatEntity seat : level.getEntitiesOfClass(SurgerySeatEntity.class, new net.minecraft.world.phys.AABB(table))) {
            for (net.minecraft.world.entity.Entity rider : seat.getPassengers()) {
                if (rider instanceof net.minecraft.world.entity.LivingEntity living) {
                    return living;
                }
            }
        }
        return null;
    }

    /** How far from the table a surgeon may work on someone else. */
    public static final double REACH = 6.0;

    /** Whether this surgeon may work on whoever lies on this table: it is them, or they stand by it. */
    public static boolean mayOperate(Player surgeon, net.minecraft.world.entity.LivingEntity patient, BlockPos table) {
        return surgeon == patient ? lyingOn(surgeon, table)
                : patient.getVehicle() instanceof SurgerySeatEntity seat && seat.blockPosition().equals(table)
                && surgeon.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(table)) <= REACH * REACH && surgeon.isAlive();
    }

    /**
     * A Cleaver at the Surgical Rig takes a mob's organs out, one a cut, in the order its data lists them
     * (docs/PARTS-AND-TRAITS.md section 7.1): out of a carcass piece laid on the table (a body's heart, lungs, stomach and
     * any special organ its mob has, a head's eyes, a rabbit's hind leg its foot), or, with nothing laid on it, out of a
     * carcass lying over it (a cow or a horse is too heavy to carry: the Assembly Frame's work zone), its torso's first,
     * then whatever is still attached. Each comes out as its organ's file says (a heart, a Gland, a rabbit's foot),
     * stamped with its mob, with anything that comes out with it (a creeper's powder sac spills gunpowder). A mob with no
     * blood gives what it has instead (a skeleton its marrow, a blaze its core), dry. The Surgical Rig's own cuts
     * (SurgicalRig) take their organs through this, and through {@link #harvest(ServerLevel, Player, SurgeryTableBlockEntity,
     * ItemStack, com.avicagan.bloodandbones.carcass.CarcassSavedData.Carcass, String)} for a carcass lying on its top, so
     * there is one count of what came out.
     *
     * @return whether an organ came out
     */
    public static boolean harvest(ServerLevel level, Player surgeon, SurgeryTableBlockEntity table, ItemStack blade) {
        com.avicagan.bloodandbones.parts.PartsData.Store store = com.avicagan.bloodandbones.parts.PartsData.SERVER;
        ItemStack stack = table.item();
        com.avicagan.bloodandbones.item.CarcassPieceItem.Piece piece = com.avicagan.bloodandbones.item.CarcassPieceItem.piece(stack);
        if (piece == null) {
            com.avicagan.bloodandbones.carcass.CarcassSavedData.Carcass carcass = stack.isEmpty() ? carcassOn(level, table.getBlockPos()) : null;
            if (carcass == null) {
                return false;
            }
            String bone = nextOrgan(store, carcass);
            if (bone == null) {
                surgeon.displayClientMessage(Component.translatable("bloodandbones.surgery.no_organs"), true);
                return false;
            }
            return harvest(level, surgeon, table, blade, carcass, bone);
        }
        java.util.List<net.minecraft.resources.ResourceLocation> held = com.avicagan.bloodandbones.parts.Organs.held(store, piece.entity(), piece.baby(), piece.bone());
        int taken = organsTaken(piece);
        if (taken >= held.size()) {
            surgeon.displayClientMessage(Component.translatable("bloodandbones.surgery.no_organs"), true);
            return false;
        }
        java.util.Map<String, String> traits = new java.util.HashMap<>(piece.traits());
        traits.put(ORGANS_TAKEN, Integer.toString(taken + 1));
        stack.set(com.avicagan.bloodandbones.registry.BBDataComponents.PIECE.get(), new com.avicagan.bloodandbones.item.CarcassPieceItem.Piece(
                piece.entity(), piece.bone(), piece.texture(), piece.coats(), piece.freshness(), piece.skinned(), java.util.Map.copyOf(traits),
                piece.blood(), piece.bloodMax(), piece.decay(), piece.baby()));
        table.notifyUpdate();
        BlockPos pos = table.getBlockPos();
        Vector3d at = new Vector3d(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5);
        cutOrgan(level, surgeon, pos, at, blade, held.get(taken), piece.entity(), piece.baby(), piece.traits());
        return true;
    }

    /**
     * The next organ out of one bone of a carcass lying whole over or on the table, counted on the carcass under that bone's
     * own key ("organs_taken:head"), which a piece cut off it still reads.
     *
     * @return whether an organ came out (false if that bone has none left)
     */
    public static boolean harvest(ServerLevel level, Player surgeon, SurgeryTableBlockEntity table, ItemStack blade,
                                  com.avicagan.bloodandbones.carcass.CarcassSavedData.Carcass carcass, String bone) {
        com.avicagan.bloodandbones.parts.PartsData.Store store = com.avicagan.bloodandbones.parts.PartsData.SERVER;
        java.util.List<net.minecraft.resources.ResourceLocation> held = com.avicagan.bloodandbones.parts.Organs.held(store, carcass.entity, carcass.baby, bone);
        int taken = organsTaken(carcass.traits, bone, bone.equals(carcass.rootBone));
        if (taken >= held.size()) {
            return false;
        }
        carcass.traits.put(ORGANS_TAKEN + ":" + bone, Integer.toString(taken + 1));
        com.avicagan.bloodandbones.carcass.CarcassSavedData.get(level).setDirty();
        BlockPos pos = table.getBlockPos();
        Vector3d at = com.avicagan.bloodandbones.carcass.CarcassAssembler.boneWorldPosition(level, carcass, bone);
        if (at == null) {
            at = new Vector3d(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5);
        }
        cutOrgan(level, surgeon, pos, at, blade, held.get(taken), carcass.entity, carcass.baby, carcass.traits);
        return true;
    }

    /** One organ comes out, into the surgeon's hands, with whatever comes out with it, wet or (from a mob with no blood) dry. */
    private static void cutOrgan(ServerLevel level, Player surgeon, BlockPos pos, Vector3d at, ItemStack blade, net.minecraft.resources.ResourceLocation organ,
                                 net.minecraft.resources.ResourceLocation entity, boolean baby, java.util.Map<String, String> kept) {
        com.avicagan.bloodandbones.parts.PartsData.Store store = com.avicagan.bloodandbones.parts.PartsData.SERVER;
        // what the carcass kept of its mob goes with the organ, for what its data reads (a charged creeper's sac)
        ItemStack out = com.avicagan.bloodandbones.parts.Organs.stack(store, organ, entity, baby, kept);
        // a Deployer's stand-in would hold it and stall: it drops on the table, as the Butcher's Table's cuts do
        Player receiver = surgeon instanceof net.neoforged.neoforge.common.util.FakePlayer ? null : surgeon;
        give(receiver, out.copy(), pos, level);
        for (ItemStack extra : com.avicagan.bloodandbones.parts.Organs.extras(com.avicagan.bloodandbones.parts.Organs.kind(store, organ), level.random)) {
            give(receiver, extra, pos, level);
        }
        // it tears out wet and squelching, bits of it flying; out of a mob with no blood it cracks out dry
        level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, out), at.x, at.y, at.z,
                6, 0.1, 0.05, 0.1, 0.08);
        boolean bleeds = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(entity)
                .map(type -> !type.is(com.avicagan.bloodandbones.registry.BBTags.BLOODLESS)).orElse(true);
        if (bleeds) {
            com.avicagan.bloodandbones.carcass.Blood.bloody(blade, level);
            com.avicagan.bloodandbones.carcass.Blood.burst(level, at, 8, com.avicagan.bloodandbones.carcass.Blood.soul(entity));
            com.avicagan.bloodandbones.carcass.Blood.gibs(level, at, 2);
            level.playSound(null, pos, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_CUT.get(), SoundSource.PLAYERS, 1.0F, 1.1F);
            level.playSound(null, pos, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.9F, 0.6F);
            level.playSound(null, pos, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.6F, 0.7F);
        } else {
            level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM,
                    new ItemStack(net.minecraft.world.item.Items.BONE_MEAL)), at.x, at.y, at.z, 8, 0.1, 0.05, 0.1, 0.06);
            level.playSound(null, pos, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_SEVER.get(), SoundSource.PLAYERS, 0.8F, 1.4F);
        }
    }

    /**
     * The bone of a carcass lying whole whose organ a Cleaver takes out next, or null if every one is out: the torso's
     * first, then the rest still attached, each counting its own (see {@link #organBones}).
     */
    @org.jetbrains.annotations.Nullable
    public static String nextOrgan(com.avicagan.bloodandbones.parts.PartsData.Store store, com.avicagan.bloodandbones.carcass.CarcassSavedData.Carcass carcass) {
        for (String bone : organBones(carcass)) {
            if (organsLeft(store, carcass, bone) > 0) {
                return bone;
            }
        }
        return null;
    }

    /**
     * The bones of a carcass lying whole that may still hold organs, in the order a Cleaver takes them: the torso's first,
     * then the rest still attached. A carcass that has lain still a while is folded into its torso (CarcassRest), its limbs
     * kept only as rest poses: they are still attached, and still hold their organs.
     */
    public static java.util.List<String> organBones(com.avicagan.bloodandbones.carcass.CarcassSavedData.Carcass carcass) {
        java.util.Set<String> bones = new java.util.LinkedHashSet<>();
        bones.add(carcass.rootBone);
        bones.addAll(carcass.bones.keySet());
        if (carcass.resting) {
            bones.addAll(carcass.restPoses.keySet());
        }
        // a limb cut through is about to go its own way
        bones.removeIf(bone -> !bone.equals(carcass.rootBone) && carcass.severed.contains(bone));
        return java.util.List.copyOf(bones);
    }

    /** How many organs are still in one bone of a carcass, by its mob's data (Organs#held) less what was taken. */
    public static int organsLeft(com.avicagan.bloodandbones.parts.PartsData.Store store, com.avicagan.bloodandbones.carcass.CarcassSavedData.Carcass carcass, String bone) {
        int held = com.avicagan.bloodandbones.parts.Organs.held(store, carcass.entity, carcass.baby, bone).size();
        return Math.max(0, held - organsTaken(carcass.traits, bone, bone.equals(carcass.rootBone)));
    }

    /** How many organs are still in a carried piece, by its mob's data (Organs#held) less what was taken. */
    public static int organsLeft(com.avicagan.bloodandbones.parts.PartsData.Store store, com.avicagan.bloodandbones.item.CarcassPieceItem.Piece piece) {
        int held = com.avicagan.bloodandbones.parts.Organs.held(store, piece.entity(), piece.baby(), piece.bone()).size();
        return Math.max(0, held - organsTaken(piece));
    }

    /**
     * A carcass lying over this table, nearest first, that nobody is dragging: the Assembly Frame's work zone (0.75
     * past each edge, 2.5 up), which the Surgical Rig works in too.
     */
    @org.jetbrains.annotations.Nullable
    public static com.avicagan.bloodandbones.carcass.CarcassSavedData.Carcass carcassOn(ServerLevel level, BlockPos table) {
        double spread = com.avicagan.bloodandbones.minion.MinionAssembly.SPREAD;
        net.minecraft.world.phys.AABB zone = new net.minecraft.world.phys.AABB(table.getX() - spread, table.getY() + 0.5, table.getZ() - spread,
                table.getX() + 1.0 + spread, table.getY() + 1.0 + com.avicagan.bloodandbones.minion.MinionAssembly.REACH, table.getZ() + 1.0 + spread);
        com.avicagan.bloodandbones.carcass.CarcassSavedData.Carcass best = null;
        double bestDistance = Double.MAX_VALUE;
        for (com.avicagan.bloodandbones.carcass.CarcassSavedData.Carcass carcass : com.avicagan.bloodandbones.carcass.CarcassSavedData.get(level).all()) {
            Vector3d at = com.avicagan.bloodandbones.carcass.CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
            if (at == null || !zone.contains(at.x, at.y, at.z) || com.avicagan.bloodandbones.carcass.CarcassDrag.isDraggingCarcass(carcass.id)) {
                continue;
            }
            double d = at.distanceSquared(table.getX() + 0.5, table.getY() + 1.0, table.getZ() + 0.5);
            if (d < bestDistance) {
                bestDistance = d;
                best = carcass;
            }
        }
        return best;
    }

    /**
     * The trait on a carcass piece counting the organs already taken from it. A carcass lying whole counts each bone's
     * under its own key ("organs_taken:head"), which a piece cut off it still reads.
     */
    public static final String ORGANS_TAKEN = "organs_taken";

    public static int organsTaken(com.avicagan.bloodandbones.item.CarcassPieceItem.Piece piece) {
        String own = piece.traits().get(ORGANS_TAKEN);
        return count(own != null ? own : piece.traits().get(ORGANS_TAKEN + ":" + piece.bone()));
    }

    /** How many organs were taken out of one bone of a carcass (a piece put down counted its own under the plain key). */
    public static int organsTaken(java.util.Map<String, String> traits, String bone, boolean root) {
        int own = count(traits.get(ORGANS_TAKEN + ":" + bone));
        return root ? Math.max(own, count(traits.get(ORGANS_TAKEN))) : own;
    }

    /** A carried piece put down whole: what was taken out of it is its bone's own count now, not the whole carcass's. */
    public static void putDown(java.util.Map<String, String> traits, String bone) {
        String own = traits.remove(ORGANS_TAKEN);
        if (own != null) {
            traits.put(ORGANS_TAKEN + ":" + bone, own);
        }
    }

    private static int count(@org.jetbrains.annotations.Nullable String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The server tells a surgeon to show the surgery screen for whoever lies on the table (themselves, or another). */
    public record OpenPayload(BlockPos pos, int patient) implements CustomPacketPayload {
        public static final Type<OpenPayload> TYPE = new Type<>(BloodAndBones.asResource("surgery_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenPayload::pos, net.minecraft.network.codec.ByteBufCodecs.VAR_INT, OpenPayload::patient, OpenPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A button on the surgery screen: do what the table allows to this part. */
    public record ActionPayload(BlockPos pos, BodyPart part) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(BloodAndBones.asResource("surgery_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ActionPayload::pos, NeoForgeStreamCodecs.enumCodec(BodyPart.class), ActionPayload::part, ActionPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Whether this player is lying on the table there. */
    public static boolean lyingOn(Player player, BlockPos table) {
        return player.getVehicle() instanceof SurgerySeatEntity seat && seat.blockPosition().equals(table);
    }

    /** From the screen: for whoever lies on that table, by them or by someone standing by it. */
    public static void handle(ServerPlayer player, ActionPayload payload) {
        if (!player.level().isLoaded(payload.pos()) || !(player.level().getBlockEntity(payload.pos()) instanceof SurgeryTableBlockEntity table)) {
            return;
        }
        net.minecraft.world.entity.LivingEntity patient = patientAt(player.level(), payload.pos());
        if (patient == null || !mayOperate(player, patient, payload.pos())
                || SurgeryTableBlock.attachment(player.level(), payload.pos()) != TableAttachment.SURGICAL) {
            return;
        }
        if (operate(player.serverLevel(), patient, player, table, payload.part()) == Action.NONE) {
            player.displayClientMessage(Component.translatable("bloodandbones.surgery.nothing"), true);
        }
    }
}
