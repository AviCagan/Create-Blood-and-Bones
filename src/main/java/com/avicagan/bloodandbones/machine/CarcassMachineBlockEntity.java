package com.avicagan.bloodandbones.machine;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassJoints;
import com.avicagan.bloodandbones.carcass.CarcassRest;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.item.ItemHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A butchery machine driven by a shaft from below. Every so often, faster the faster it turns, it works
 * whatever carcass part is over it (up to a couple of blocks up, so a carcass lying on it or hanging over
 * it both count), the same way a player with the right tool would. What falls out is kept in its output
 * for funnels, chutes and hoppers to take, or for a player to take with an empty hand; while that is full
 * the machine waits.
 */
public class CarcassMachineBlockEntity extends KineticBlockEntity implements Clearable {
    /** Ticks per stroke at 16 RPM, before the kind's pace. */
    public static final int BASE_STROKE = 80;
    /** Fewest ticks between strokes, however fast it turns. */
    public static final int MIN_STROKE = 5;
    /** How far above the machine it reaches. */
    public static final double REACH = 2.5;

    private static final Map<String, Item> SKULLS = Map.of(
            "minecraft:zombie", Items.ZOMBIE_HEAD,
            "minecraft:husk", Items.ZOMBIE_HEAD,
            "minecraft:drowned", Items.ZOMBIE_HEAD,
            "minecraft:skeleton", Items.SKELETON_SKULL,
            "minecraft:stray", Items.SKELETON_SKULL,
            "minecraft:bogged", Items.SKELETON_SKULL,
            "minecraft:wither_skeleton", Items.WITHER_SKELETON_SKULL,
            "minecraft:creeper", Items.CREEPER_HEAD,
            "minecraft:piglin", Items.PIGLIN_HEAD,
            "minecraft:piglin_brute", Items.PIGLIN_HEAD);
    /** Chance the Beheader keeps a skull whole; a wither skull is rarer. */
    public static final float SKULL_CHANCE = 0.5F;
    public static final float WITHER_SKULL_CHANCE = 0.1F;

    public final ItemStackHandler output = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            // a funnel emptying it: goggles should see the new count now, not at the next stroke
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    };
    /** Output only: nothing goes in from outside. */
    private final IItemHandler outputOnly = new RangedWrapper(output, 0, 9) {
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };
    public int timer;

    public CarcassMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BBBlockEntities.CARCASS_MACHINE.get(), (be, side) -> be.outputOnly);
    }

    public MachineKind kind() {
        return getBlockState().getBlock() instanceof CarcassMachineBlock block ? block.kind : MachineKind.MANGLER;
    }

    /** MillstoneBlockEntity#getProcessingSpeed: 1 at 16 RPM, 16 at 256. */
    public int processingSpeed() {
        return Mth.clamp((int) Math.abs(getSpeed() / 16.0F), 1, 512);
    }

    public int strokeTicks() {
        return Math.max(MIN_STROKE, Math.round(BASE_STROKE * kind().pace / processingSpeed()));
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || getSpeed() == 0) {
            return;
        }
        if (isOutputFull()) {
            return;
        }
        if (++timer < strokeTicks()) {
            return;
        }
        timer = 0;
        if (stroke((ServerLevel) level)) {
            sendData();
        }
    }

    private boolean isOutputFull() {
        for (int i = 0; i < output.getSlots(); i++) {
            if (output.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** How far past its own edges it reaches, so a body lying across it and the floor beside it counts. */
    public static final double SPREAD = 0.75;

    /** The space over the machine it works in. */
    public AABB zone() {
        return new AABB(worldPosition.getX() - SPREAD, worldPosition.getY() + 0.5, worldPosition.getZ() - SPREAD,
                worldPosition.getX() + 1.0 + SPREAD, worldPosition.getY() + 1.0 + REACH, worldPosition.getZ() + 1.0 + SPREAD);
    }

    /** One stroke of work; true if it touched anything. */
    public boolean stroke(ServerLevel level) {
        List<Target> targets = targets(level);
        if (targets.isEmpty()) {
            return false;
        }
        MachineKind kind = kind();
        for (Target target : targets) {
            CarcassSavedData.Carcass carcass = target.carcass();
            // a carcass lying folded over the machine is unfolded so its limbs can be reached, in the same
            // stroke: left for the next one, it may already have settled and folded up again
            // (only when this machine has something to cut on it: a head for the Beheader, any other limb for the
            // Guillotine; else it would unfold the body every stroke for nothing)
            if (carcass.resting && kind != MachineKind.DEGLOVER && (!hasWork(carcass, kind) || CarcassRest.split(level, carcass) == null)) {
                continue;
            }
            boolean did = switch (kind) {
                case MANGLER -> mangle(level, target);
                case GUILLOTINE -> chop(level, target, false);
                case BEHEADER -> chop(level, target, true);
                case DEGLOVER -> capture(() -> CarcassButchery.skin(level, null, carcass, target.at()));
            };
            if (did) {
                return true;
            }
        }
        return false;
    }


    /** Whether a blade of this kind has anything to cut on this carcass; the Mangler grinds anything. */
    private static boolean hasWork(CarcassSavedData.Carcass carcass, MachineKind kind) {
        if (kind != MachineKind.GUILLOTINE && kind != MachineKind.BEHEADER) {
            return true;
        }
        boolean heads = kind == MachineKind.BEHEADER;
        for (CarcassJoints.Spec joint : carcass.joints) {
            String bone = joint.child();
            if (CarcassButchery.isAttached(carcass, bone) && isHead(bone) == heads && !(heads && hasHeadAbove(carcass, bone))) {
                return true;
            }
        }
        return false;
    }

    /** Tear a limb off first, else grind a loose piece. The body is only ground once nothing hangs off it. */
    private boolean mangle(ServerLevel level, Target target) {
        CarcassSavedData.Carcass carcass = target.carcass();
        String bone = target.bone();
        if (bone.equals(carcass.rootBone) && CarcassButchery.isAttached(carcass, bone)) {
            // the body is over the machine: pull at whichever limb is nearest
            bone = nearestLimb(level, carcass, null);
            if (bone == null) {
                return false;
            }
        }
        String cutting = bone;
        return capture(() -> CarcassButchery.cut(level, null, carcass, cutting, target.at()));
    }

    /** One blade stroke through a limb's joint: it comes off at once. */
    private boolean chop(ServerLevel level, Target target, boolean heads) {
        CarcassSavedData.Carcass carcass = target.carcass();
        String bone = target.bone();
        if (bone.equals(carcass.rootBone)) {
            // the body is under the blade: the nearest limb of the right sort is what the blade meets
            bone = nearestLimb(level, carcass, heads);
            if (bone == null) {
                return false;
            }
        }
        if (bone.equals(carcass.rootBone) || !CarcassButchery.isAttached(carcass, bone) || isHead(bone) != heads) {
            return false;
        }
        if (heads && hasHeadAbove(carcass, bone)) {
            // cut at the neck, not through the skull: the bone nearer the body goes first
            return false;
        }
        String entity = carcass.entity.toString();
        CarcassButchery.sever(level, carcass, bone, target.at());
        if (heads) {
            Item skull = SKULLS.get(entity);
            float chance = skull == Items.WITHER_SKELETON_SKULL ? WITHER_SKULL_CHANCE : SKULL_CHANCE;
            if (skull != null && level.random.nextFloat() < chance) {
                store(new ItemStack(skull));
            }
        }
        level.playSound(null, target.at().x, target.at().y, target.at().z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4F, heads ? 1.4F : 0.8F);
        return true;
    }

    /**
     * The attached limb of this carcass nearest the middle of the zone; heads only, never heads, or either
     * (null). A beheader wants the neck end, so a head under another head is passed over.
     */
    @org.jetbrains.annotations.Nullable
    private String nearestLimb(ServerLevel level, CarcassSavedData.Carcass carcass, @org.jetbrains.annotations.Nullable Boolean heads) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        net.minecraft.world.phys.Vec3 middle = zone().getCenter();
        String best = null;
        double bestDistance = Double.MAX_VALUE;
        for (CarcassJoints.Spec joint : carcass.joints) {
            String bone = joint.child();
            if (heads != null && (isHead(bone) != heads || (heads && hasHeadAbove(carcass, bone)))) {
                continue;
            }
            UUID id = carcass.bones.get(bone);
            if (id == null || !(container.getSubLevel(id) instanceof ServerSubLevel body) || body.isRemoved()) {
                continue;
            }
            double distance = body.logicalPose().position().distanceSquared(middle.x, middle.y, middle.z);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = bone;
            }
        }
        return best;
    }

    /** A head or a neck, by the part's own name. */
    public static boolean isHead(String bone) {
        String name = bone.substring(bone.lastIndexOf('/') + 1).toLowerCase();
        return name.contains("head") || name.equals("neck");
    }

    /** Whether a head bone sits between this one and the body (a ravager's neck is cut, not its head). */
    private static boolean hasHeadAbove(CarcassSavedData.Carcass carcass, String bone) {
        String cursor = bone;
        for (int hops = 0; hops < 16; hops++) {
            String parent = null;
            for (CarcassJoints.Spec joint : carcass.joints) {
                if (joint.child().equals(cursor)) {
                    parent = joint.parent();
                    break;
                }
            }
            if (parent == null) {
                return false;
            }
            if (isHead(parent) && !parent.equals(carcass.rootBone)) {
                return true;
            }
            cursor = parent;
        }
        return false;
    }

    private boolean capture(java.util.function.BooleanSupplier action) {
        return CarcassButchery.capturing(this::store, action::getAsBoolean);
    }

    /** Into the output; whatever does not fit falls out on top. */
    private void store(ItemStack stack) {
        ItemStack left = ItemHandlerHelper.insertItemStacked(output, stack, false);
        if (!left.isEmpty() && level != null) {
            net.minecraft.world.level.block.Block.popResource(level, worldPosition.above(), left);
        }
    }

    public record Target(CarcassSavedData.Carcass carcass, String bone, Vector3d at) {
    }

    /** Every loaded carcass part whose centre is in the zone, lowest first. */
    public List<Target> targets(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        List<Target> found = new ArrayList<>();
        if (container == null) {
            return found;
        }
        AABB zone = zone();
        for (CarcassSavedData.Carcass carcass : List.copyOf(CarcassSavedData.get(level).all())) {
            for (Map.Entry<String, UUID> bone : carcass.bones.entrySet()) {
                if (!(container.getSubLevel(bone.getValue()) instanceof ServerSubLevel body) || body.isRemoved()) {
                    continue;
                }
                Vector3dc p = body.logicalPose().position();
                if (zone.contains(p.x(), p.y(), p.z())) {
                    found.add(new Target(carcass, bone.getKey(), new Vector3d(p)));
                }
            }
        }
        found.sort((a, b) -> Double.compare(a.at().y, b.at().y));
        return found;
    }

    public void giveContentsTo(Player player) {
        for (int slot = 0; slot < output.getSlots(); slot++) {
            player.getInventory().placeItemBackInInventory(output.getStackInSlot(slot));
            output.setStackInSlot(slot, ItemStack.EMPTY);
        }
        setChanged();
        sendData();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        int held = 0;
        for (int i = 0; i < output.getSlots(); i++) {
            held += output.getStackInSlot(i).getCount();
        }
        new LangBuilder(BloodAndBones.MOD_ID).translate("gui.goggles.carcass_machine.output", held)
                .style(isOutputFull() ? ChatFormatting.RED : ChatFormatting.GRAY).forGoggles(tooltip);
        return true;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        invalidateCapabilities();
    }

    @Override
    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, output);
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < output.getSlots(); i++) {
            output.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putInt("Timer", timer);
        tag.put("Output", output.serializeNBT(registries));
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        timer = tag.getInt("Timer");
        output.deserializeNBT(registries, tag.getCompound("Output"));
        super.read(tag, registries, clientPacket);
    }
}
