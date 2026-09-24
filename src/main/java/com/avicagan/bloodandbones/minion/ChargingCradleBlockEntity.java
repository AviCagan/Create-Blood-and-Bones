package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The cradle's stock and its work: eight full canisters, eight empties, a stack of brass sheets. At 16 RPM or more it
 * looks for brass minions within {@link #REACH} blocks; one below a quarter or powered down gets a full canister (the
 * empty comes back here). The faster it turns the sooner it swaps: a second at 64 RPM. With sheets, it mends a docked
 * brass minion a heart a second, a sheet every ten hearts. Cradles in the world keep a per-level list, as troughs do,
 * so a minion finds the nearest cheaply; one riding a contraption is off the list and does nothing.
 */
public class ChargingCradleBlockEntity extends KineticBlockEntity {
    public static final int FULL = 8;
    public static final int EMPTY = 8;
    public static final int SHEETS = FULL + EMPTY;
    /** How near a brass minion must be to be served. */
    public static final double REACH = 2.0;
    /** The least speed it works at. */
    public static final float MIN_RPM = 16.0F;
    private static final Map<ResourceKey<Level>, Set<BlockPos>> CRADLES = new ConcurrentHashMap<>();

    public final ItemStackHandler inventory = new ItemStackHandler(SHEETS + 1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < FULL) {
                return stack.is(BBItems.SOUL_CANISTER.get());
            }
            if (slot < SHEETS) {
                return stack.is(BBItems.EMPTY_SOUL_CANISTER.get());
            }
            return stack.is(AllItems.BRASS_SHEET.get());
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot < SHEETS ? 1 : 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            sendData();
        }
    };

    /**
     * What pipes of items see: full canisters and sheets go in, empties come out, nothing else. A hopper under it
     * takes the empties; a funnel into it feeds full canisters.
     */
    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot < FULL || slot == SHEETS ? inventory.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= FULL && slot < SHEETS ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return (slot < FULL || slot == SHEETS) && inventory.isItemValid(slot, stack);
        }
    };

    private int cooldown;
    private float mended;

    public ChargingCradleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IItemHandler automation() {
        return automation;
    }

    /** How long a swap takes at this speed: a second at 64 RPM, four at 16. */
    public int swapTicks() {
        return Mth.clamp(Math.round(1280.0F / Math.max(1.0F, Math.abs(getSpeed()))), 10, 80);
    }

    public int fullCanisters() {
        int n = 0;
        for (int i = 0; i < FULL; i++) {
            n += inventory.getStackInSlot(i).getCount();
        }
        return n;
    }

    /** Whether it can swap a canister in now: turning fast enough, a full one in it, and a place for the empty. */
    public boolean canServe() {
        if (Math.abs(getSpeed()) < MIN_RPM || fullCanisters() == 0) {
            return false;
        }
        for (int i = FULL; i < SHEETS; i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            CRADLES.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(worldPosition.immutable());
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (level != null && !level.isClientSide) {
            Set<BlockPos> set = CRADLES.get(level.dimension());
            if (set != null) {
                set.remove(worldPosition);
            }
        }
    }

    /** Every cradle in this level now. */
    public static Set<BlockPos> all(Level level) {
        return CRADLES.getOrDefault(level.dimension(), Set.of());
    }

    /** Forget every cradle (the server stopped: chunks are saved, not broken, so none took itself off). */
    public static void clear() {
        CRADLES.clear();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || Math.abs(getSpeed()) < MIN_RPM) {
            return;
        }
        if (--cooldown > 0) {
            return;
        }
        cooldown = 20;
        AABB near = new AABB(worldPosition).inflate(REACH);
        for (MinionEntity minion : level.getEntitiesOfClass(MinionEntity.class, near, m -> m.isAlive() && m.cybernetic())) {
            if ((minion.poweredDown() || minion.powerShare() < MinionEntity.HUNGRY) && swap(minion)) {
                cooldown = swapTicks();
                return;
            }
            if (!minion.poweredDown() && minion.getHealth() < minion.getMaxHealth() && !inventory.getStackInSlot(SHEETS).isEmpty()) {
                minion.heal(1.0F);
                mended += 1.0F;
                if (mended >= MinionEntity.SHEET_REPAIR) {
                    mended = 0.0F;
                    inventory.extractItem(SHEETS, 1, false);
                }
                return;
            }
        }
    }

    /** A full canister in, the empty out; false if it has no full one, or nowhere to put the empty. */
    private boolean swap(MinionEntity minion) {
        int full = -1;
        int room = -1;
        for (int i = 0; i < FULL && full < 0; i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                full = i;
            }
        }
        for (int i = FULL; i < SHEETS && room < 0; i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                room = i;
            }
        }
        if (full < 0 || room < 0 || !minion.charge()) {
            return false;
        }
        inventory.extractItem(full, 1, false);
        inventory.setStackInSlot(room, new ItemStack(BBItems.EMPTY_SOUL_CANISTER.get()));
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 0.7F);
        return true;
    }

    /** By hand: a canister or a sheet goes in; an empty hand takes the empties out. */
    public boolean use(Player player, ItemStack held) {
        if (held.isEmpty()) {
            boolean any = false;
            for (int i = FULL; i < SHEETS; i++) {
                ItemStack out = inventory.extractItem(i, 64, false);
                if (!out.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(out);
                    any = true;
                }
            }
            return any;
        }
        ItemStack rest = held.copy();
        for (int i = 0; i < inventory.getSlots() && !rest.isEmpty(); i++) {
            if (i >= FULL && i < SHEETS) {
                continue;
            }
            rest = inventory.insertItem(i, rest, false);
        }
        if (rest.getCount() == held.getCount()) {
            return false;
        }
        if (!player.hasInfiniteMaterials()) {
            held.setCount(rest.getCount());
        }
        return true;
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < inventory.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), inventory.getStackInSlot(i));
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        dropContents();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putFloat("Mended", mended);
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        mended = tag.getFloat("Mended");
        super.read(tag, registries, clientPacket);
    }
}
