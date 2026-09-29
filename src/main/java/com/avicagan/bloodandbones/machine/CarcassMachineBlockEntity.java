package com.avicagan.bloodandbones.machine;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassJoints;
import com.avicagan.bloodandbones.carcass.CarcassRest;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.avicagan.bloodandbones.registry.BBSounds;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A butchery machine driven by a shaft from below. It works whatever carcass part is over it (up to a couple of blocks
 * up, so a carcass lying on it, hanging over it or passing over it on a trolley all count), and asks its filter about
 * each part it could take, so a line of them can pull one part at a time out of a mixed stream. What falls out is kept
 * in its output for funnels, chutes and hoppers to take, or for a player to take with an empty hand; while that is full
 * the machine waits.
 * <ul>
 * <li>The Mangler, Beheader and Deglover make ready for their next stroke while they turn (faster the faster, the
 * Millstone way), and strike as soon as something they can take is in reach, so a Beheader under a line takes each head
 * as it passes. Ready and idle, they look every couple of ticks at first and less often while nothing comes, and only
 * at carcasses lying near.</li>
 * <li>The Guillotine winds its blade up while it turns and holds it there, armed; a rising redstone edge drops it
 * through one limb, and it winds up again (ARCHITECTURE 7's state machine, the Sequenced Gearshift's edge).</li>
 * </ul>
 */
public class CarcassMachineBlockEntity extends KineticBlockEntity implements Clearable {
    /** Ticks per stroke at 16 RPM, before the kind's pace. */
    public static final int BASE_STROKE = 80;
    /** Fewest ticks between strokes, however fast it turns. */
    public static final int MIN_STROKE = 5;
    /** How far above the machine it reaches. */
    public static final double REACH = 2.5;
    /** How far past its own edges it reaches, so a body lying across it and the floor beside it counts. */
    public static final double SPREAD = 0.75;
    /** Ticks the Guillotine's blade takes to fall. */
    public static final int DROP_TICKS = 4;
    /** How often a machine ready to strike looks for something to take, at first. */
    private static final int LOOK_EVERY = 2;
    /** The longest it goes between looks while nothing comes (each look that finds nothing doubles the wait up to this). */
    private static final int LOOK_IDLE = 10;
    /** How far from its zone a carcass's torso may lie and still have a part in reach (its limbs hang off it). */
    private static final double BODY_SPAN = 6.0;

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
    /** Ticks since the last stroke; at {@link #strokeTicks()} it is ready and strikes when it finds work. */
    public int timer;
    /** The Guillotine's blade: 0 just fallen, 1 wound all the way up and armed. */
    public float wind;
    /** Ticks left of the Guillotine's blade falling; 0 when it is not. */
    public int falling;
    /** Whether redstone reached it when last looked, for the rising edge. */
    private boolean powered;
    /** Strokes that took something, since it was placed: what a test counts the time of a path by. */
    public int strokes;
    /** Ticks until it looks again while ready, and how long the next wait is after a look that found nothing; not saved. */
    private int lookIn;
    private int idleWait = LOOK_EVERY;
    /** Which parts it takes; see {@link PartFilter}. */
    public PartFilteringBehaviour filtering;

    public CarcassMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        filtering = new PartFilteringBehaviour(this, new MachineFilterSlot());
        behaviours.add(filtering);
    }

    /** What may go in the filter slot: a spawn egg, a carcass piece, or a Create filter. */
    public static boolean filterAllowed(ItemStack stack) {
        return PartFilter.allowed(stack);
    }

    /** Whether the filter lets this machine take this part of this carcass. */
    public boolean accepts(CarcassSavedData.Carcass carcass, String bone) {
        return filtering == null || filtering.takes(carcass, bone);
    }

    /** Whether the filter lets this machine take anything of this carcass, asked about its torso. */
    public boolean accepts(CarcassSavedData.Carcass carcass) {
        return accepts(carcass, carcass.rootBone);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BBBlockEntities.CARCASS_MACHINE.get(), (be, side) -> be.outputOnly);
    }

    public MachineKind kind() {
        return getBlockState().getBlock() instanceof CarcassMachineBlock block ? block.kind : MachineKind.MANGLER;
    }

    /** MillstoneBlockEntity#getProcessingSpeed: 1 at 16 RPM, 16 at 256; no more than the kind's own top speed allows. */
    public int processingSpeed() {
        float rpm = Math.min(Math.abs(getSpeed()), kind().maxRpm);
        return Mth.clamp((int) (rpm / 16.0F), 1, 512);
    }

    /** Ticks a stroke takes at this speed; for the Guillotine, the wind-up. */
    public int strokeTicks() {
        return Math.max(MIN_STROKE, Math.round(BASE_STROKE * kind().pace / processingSpeed()));
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (kind() == MachineKind.GUILLOTINE) {
            tickGuillotine();
            return;
        }
        if (level.isClientSide || getSpeed() == 0) {
            return;
        }
        if (timer < strokeTicks()) {
            timer++;
            return;
        }
        // ready: it looks every couple of ticks, backing off while nothing comes, so an idle line costs little
        if (isOutputFull() || --lookIn > 0) {
            return;
        }
        if (stroke((ServerLevel) level)) {
            timer = 0;
            strokes++;
            lookIn = 0;
            idleWait = LOOK_EVERY;
            sendData();
        } else {
            lookIn = idleWait;
            idleWait = Math.min(LOOK_IDLE, idleWait * 2);
        }
    }

    /**
     * The blade winds up while the shaft turns, on both sides (the client to draw it rising), and is held there; the
     * server says when it drops, and cuts when it lands.
     */
    private void tickGuillotine() {
        if (falling > 0) {
            falling--;
            if (falling == 0 && !level.isClientSide) {
                land((ServerLevel) level);
            }
            return;
        }
        if (getSpeed() == 0 || wind >= 1.0F) {
            return;
        }
        float before = wind;
        wind = Math.min(1.0F, wind + 1.0F / strokeTicks());
        if (level.isClientSide) {
            return;
        }
        // a ratchet clicking as the blade goes up, and a clack when it catches at the top
        if ((int) (before * 8) != (int) (wind * 8)) {
            level.playSound(null, worldPosition, BBSounds.MACHINE_WIND.get(), SoundSource.BLOCKS, 0.35F, 0.8F + wind * 0.6F);
        }
        if (wind >= 1.0F) {
            level.playSound(null, worldPosition, SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.BLOCKS, 0.6F, 0.6F);
            sendData();
        }
    }

    /** Redstone reaching it changed: a rising edge drops an armed Guillotine's blade. */
    public void redstone(boolean signal) {
        if (signal && !powered) {
            drop();
        }
        if (signal != powered) {
            powered = signal;
            setChanged();
        }
    }

    /** Let the blade go, if it is wound up. */
    public boolean drop() {
        if (level == null || level.isClientSide || kind() != MachineKind.GUILLOTINE || wind < 1.0F || falling > 0) {
            return false;
        }
        falling = DROP_TICKS;
        wind = 0.0F;
        level.playSound(null, worldPosition, SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 0.8F, 0.5F);
        sendData();
        return true;
    }

    /** The blade hits the bottom: through whatever limb is under it. */
    private void land(ServerLevel level) {
        Vec3 bottom = Vec3.atBottomCenterOf(worldPosition.above());
        if (!isOutputFull() && stroke(level)) {
            strokes++;
        } else {
            level.playSound(null, bottom.x, bottom.y, bottom.z, BBSounds.MACHINE_BLADE.get(), SoundSource.BLOCKS, 0.5F, 1.2F);
        }
        sendData();
    }

    /** How far the Guillotine's blade is from the top of its frame, 0 armed to 1 at the bottom, for drawing. */
    public float bladeDrop(float partialTicks) {
        if (falling > 0) {
            float fallen = Math.min(1.0F, (DROP_TICKS - falling + partialTicks) / DROP_TICKS);
            return fallen * fallen;
        }
        float wound = wind;
        if (wind < 1.0F && getSpeed() != 0) {
            wound = Math.min(1.0F, wind + partialTicks / strokeTicks());
        }
        return 1.0F - wound;
    }

    private boolean isOutputFull() {
        for (int i = 0; i < output.getSlots(); i++) {
            if (output.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** The space over the machine it works in. */
    public AABB zone() {
        return new AABB(worldPosition.getX() - SPREAD, worldPosition.getY() + 0.5, worldPosition.getZ() - SPREAD,
                worldPosition.getX() + 1.0 + SPREAD, worldPosition.getY() + 1.0 + REACH, worldPosition.getZ() + 1.0 + SPREAD);
    }

    /** One stroke of work; true if it took anything. */
    public boolean stroke(ServerLevel level) {
        MachineKind kind = kind();
        for (Map.Entry<CarcassSavedData.Carcass, Map<String, Vector3d>> over : inReach(level).entrySet()) {
            CarcassSavedData.Carcass carcass = over.getKey();
            Map<String, Vector3d> reached = over.getValue();
            if (kind == MachineKind.DEGLOVER) {
                Vector3d at = reached.values().iterator().next();
                if (accepts(carcass) && capture(() -> CarcassButchery.skin(level, null, carcass, at))) {
                    return true;
                }
                continue;
            }
            // a carcass lying folded over the machine is unfolded so its limbs can be reached, in the same stroke: left
            // for the next one, it may already have settled and folded up again (only when there is something here for
            // this machine, else it would unfold the body every stroke for nothing)
            if (carcass.resting) {
                if (!hasWork(carcass, kind) || CarcassRest.split(level, carcass) == null) {
                    continue;
                }
                reached = positions(level, carcass, reached.containsKey(carcass.rootBone));
            }
            String part = pick(level, carcass, reached, kind);
            if (part == null) {
                continue;
            }
            Vector3d at = reached.getOrDefault(part, reached.values().iterator().next());
            boolean did = switch (kind) {
                case MANGLER -> grind(level, carcass, part, at);
                case GUILLOTINE -> chop(level, carcass, part, at, false);
                case BEHEADER -> chop(level, carcass, part, at, true);
                case DEGLOVER -> false;
            };
            if (did) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which part of this carcass it takes now, of those in reach (a body across it offers its limbs that stick out past
     * it too), asked of the filter, the one nearest its middle:
     * <ul>
     * <li>the Mangler: a loose piece, else a limb to tear off and grind, else the body once nothing hangs off it;</li>
     * <li>the Guillotine: a limb that is not a head;</li>
     * <li>the Beheader: a head at its neck end (a ravager's neck, not the head on it).</li>
     * </ul>
     */
    @Nullable
    private String pick(ServerLevel level, CarcassSavedData.Carcass carcass, Map<String, Vector3d> reached, MachineKind kind) {
        boolean bodyOver = reached.containsKey(carcass.rootBone);
        Map<String, Vector3d> everywhere = positions(level, carcass, true);
        Vec3 middle = zone().getCenter();
        Comparator<String> nearest = Comparator.comparingDouble(bone -> {
            Vector3d at = everywhere.get(bone);
            return at == null ? Double.MAX_VALUE : at.distanceSquared(middle.x, middle.y, middle.z);
        });
        List<String> candidates = new ArrayList<>();
        if (kind == MachineKind.MANGLER) {
            for (String bone : reached.keySet()) {
                if (!CarcassButchery.isAttached(carcass, bone) && accepts(carcass, bone)) {
                    candidates.add(bone);
                }
            }
            if (!candidates.isEmpty()) {
                return candidates.stream().min(nearest).orElse(null);
            }
        }
        for (CarcassJoints.Spec joint : carcass.joints) {
            String bone = joint.child();
            if (!everywhere.containsKey(bone) || !(bodyOver || reached.containsKey(bone)) || !takes(carcass, bone, kind) || !accepts(carcass, bone)) {
                continue;
            }
            candidates.add(bone);
        }
        return candidates.stream().min(nearest).orElse(null);
    }

    /** Whether this kind of blade can take this attached bone: a head at its neck end for the Beheader, any other limb for the rest. */
    private static boolean takes(CarcassSavedData.Carcass carcass, String bone, MachineKind kind) {
        if (bone.equals(carcass.rootBone) || !CarcassButchery.isAttached(carcass, bone)) {
            return false;
        }
        return switch (kind) {
            case BEHEADER -> isHead(bone) && !hasHeadAbove(carcass, bone);
            case GUILLOTINE -> !isHead(bone);
            case MANGLER -> true;
            case DEGLOVER -> false;
        };
    }

    /**
     * Whether there is anything on this carcass this machine would take, filter and all, as {@link #pick} would find it:
     * an attached limb it takes, or for the Mangler the body itself once nothing hangs off it (a body with a limb the
     * filter turns away still on it cannot be ground, and unfolding it would be for nothing, every stroke).
     */
    private boolean hasWork(CarcassSavedData.Carcass carcass, MachineKind kind) {
        for (CarcassJoints.Spec joint : carcass.joints) {
            if (takes(carcass, joint.child(), kind) && accepts(carcass, joint.child())) {
                return true;
            }
        }
        return kind == MachineKind.MANGLER && !CarcassButchery.isAttached(carcass, carcass.rootBone) && accepts(carcass);
    }

    /**
     * The terminal grind, one stroke a piece: a limb is torn off and goes straight between the grinders, a loose piece or
     * a bare body is ground where it lies. The Mangler's path takes the least meat and bone, but gives armour scraps for
     * every piece and the mob's own drops with its body.
     */
    private boolean grind(ServerLevel level, CarcassSavedData.Carcass carcass, String bone, Vector3d at) {
        return CarcassButchery.mangling(this::store, () -> {
            CarcassSavedData.Carcass piece = carcass;
            if (CarcassButchery.isAttached(carcass, bone)) {
                piece = CarcassButchery.sever(level, carcass, bone, at);
                if (piece == null || !piece.bones.containsKey(bone)) {
                    return false;
                }
            }
            // the hide goes between the grinders with it (the path's share of it, none unless a datapack says so)
            CarcassButchery.groundHide(level, piece, bone, at);
            CarcassButchery.butcher(level, piece, bone, at);
            level.playSound(null, at.x, at.y, at.z, BBSounds.MACHINE_GRIND.get(), SoundSource.BLOCKS, 1.0F, 0.7F + level.random.nextFloat() * 0.3F);
            if (com.avicagan.bloodandbones.carcass.Blood.bloody(piece)) {
                com.avicagan.bloodandbones.carcass.Blood.gibs(level, at, 6);
            }
            return true;
        });
    }

    /** One blade stroke through a limb's joint: it comes off at once, whole. */
    private boolean chop(ServerLevel level, CarcassSavedData.Carcass carcass, String bone, Vector3d at, boolean heads) {
        String entity = carcass.entity.toString();
        CarcassButchery.sever(level, carcass, bone, at);
        if (heads) {
            Item skull = SKULLS.get(entity);
            float chance = skull == Items.WITHER_SKELETON_SKULL ? WITHER_SKULL_CHANCE : SKULL_CHANCE;
            if (skull != null && level.random.nextFloat() < chance) {
                store(new ItemStack(skull));
            }
        }
        level.playSound(null, at.x, at.y, at.z, BBSounds.MACHINE_BLADE.get(), SoundSource.BLOCKS, 0.4F, heads ? 1.4F : 0.8F);
        return true;
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

    /**
     * Every carcass with a part in reach, and where those parts are, the one whose lowest part is lowest first (the
     * body lying on it before one hanging over it).
     */
    public Map<CarcassSavedData.Carcass, Map<String, Vector3d>> inReach(ServerLevel level) {
        Map<CarcassSavedData.Carcass, Map<String, Vector3d>> found = new LinkedHashMap<>();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return found;
        }
        AABB zone = zone();
        // a record's parts hang off its root: one whose root lies well away from the zone has nothing in it
        AABB near = zone.inflate(BODY_SPAN);
        List<Map.Entry<CarcassSavedData.Carcass, Double>> order = new ArrayList<>();
        for (CarcassSavedData.Carcass carcass : CarcassSavedData.get(level).all()) {
            UUID rootId = carcass.bones.get(carcass.rootBone);
            if (rootId != null && container.getSubLevel(rootId) instanceof ServerSubLevel root && !root.isRemoved()) {
                Vector3dc p = root.logicalPose().position();
                if (!near.contains(p.x(), p.y(), p.z())) {
                    continue;
                }
            }
            Map<String, Vector3d> here = null;
            double lowest = Double.MAX_VALUE;
            for (Map.Entry<String, UUID> bone : carcass.bones.entrySet()) {
                if (!(container.getSubLevel(bone.getValue()) instanceof ServerSubLevel body) || body.isRemoved()) {
                    continue;
                }
                Vector3dc p = body.logicalPose().position();
                if (zone.contains(p.x(), p.y(), p.z())) {
                    if (here == null) {
                        here = new LinkedHashMap<>();
                    }
                    here.put(bone.getKey(), new Vector3d(p));
                    lowest = Math.min(lowest, p.y());
                }
            }
            if (here != null) {
                found.put(carcass, here);
                order.add(Map.entry(carcass, lowest));
            }
        }
        order.sort(Map.Entry.comparingByValue());
        Map<CarcassSavedData.Carcass, Map<String, Vector3d>> sorted = new LinkedHashMap<>();
        for (Map.Entry<CarcassSavedData.Carcass, Double> entry : order) {
            sorted.put(entry.getKey(), found.get(entry.getKey()));
        }
        return sorted;
    }

    /** Where each part of a carcass is now; only those in reach unless {@code all}. */
    private Map<String, Vector3d> positions(ServerLevel level, CarcassSavedData.Carcass carcass, boolean all) {
        Map<String, Vector3d> out = new LinkedHashMap<>();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return out;
        }
        AABB zone = zone();
        for (Map.Entry<String, UUID> bone : carcass.bones.entrySet()) {
            if (container.getSubLevel(bone.getValue()) instanceof ServerSubLevel body && !body.isRemoved()) {
                Vector3dc p = body.logicalPose().position();
                if (all || zone.contains(p.x(), p.y(), p.z())) {
                    out.put(bone.getKey(), new Vector3d(p));
                }
            }
        }
        return out;
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
        MachineKind kind = kind();
        if (kind == MachineKind.GUILLOTINE) {
            boolean armed = wind >= 1.0F && falling == 0;
            new LangBuilder(BloodAndBones.MOD_ID).translate(armed ? "gui.goggles.guillotine.armed" : "gui.goggles.guillotine.winding",
                    Math.round(wind * 100)).style(armed ? ChatFormatting.GOLD : ChatFormatting.GRAY).forGoggles(tooltip);
            return true;
        }
        if (Math.abs(getSpeed()) > kind.maxRpm) {
            new LangBuilder(BloodAndBones.MOD_ID).translate("gui.goggles.carcass_machine.too_fast", kind.maxRpm)
                    .style(ChatFormatting.GOLD).forGoggles(tooltip);
        }
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
        tag.putFloat("Wind", wind);
        tag.putInt("Falling", falling);
        tag.putBoolean("Powered", powered);
        tag.putInt("Strokes", strokes);
        tag.put("Output", output.serializeNBT(registries));
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        timer = tag.getInt("Timer");
        wind = tag.getFloat("Wind");
        falling = tag.getInt("Falling");
        powered = tag.getBoolean("Powered");
        strokes = tag.getInt("Strokes");
        output.deserializeNBT(registries, tag.getCompound("Output"));
        super.read(tag, registries, clientPacket);
    }
}
