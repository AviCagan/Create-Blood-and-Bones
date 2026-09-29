package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The Tender (docs/NEXT.md 1.1): it keeps the Blood Troughs and Charging Cradles within its reach of home stocked from the
 * containers there, and wakes its maker's minions lying powered down within that reach, so neglect never destroys one.
 * <ul>
 *     <li>Blood goes into troughs by the bucket: buckets of blood from a container, or empty buckets it fills at a Create
 *     Fluid Tank or a Bleeding Rack's tray holding blood;</li>
 *     <li>full Soul Canisters and brass sheets go into cradles, and the cradles' empty canisters back into a container;</li>
 *     <li>a fallen flesh minion gets a bucket of blood poured into it, a fallen brass one a canister;</li>
 *     <li>what it carries and no longer needs (the empties, what is left over) goes back into a container.</li>
 * </ul>
 * The containers it takes from and puts back into are chests, barrels, shulker boxes and Create Item Vaults: never a
 * machine's slots, a trough, a cradle or a table.
 * <p>
 * Nothing it moves is made or lost on the way: every move is tried first and made only once it is known to go through
 * whole (a bucket pours only into a trough with room for all of it; what a fallen minion cannot hold of the bucket poured
 * into it, the Tender drinks itself if it is flesh with room, else it goes into a trough by home with room; with nowhere
 * for the rest, it does not pour, and waits). It looks round every second at 100%, a fitter Tender more often
 * (docs/NEXT.md 1.2), and carries as much at once as it has room for, so a big torso makes fewer trips.
 */
public final class MinionTender {
    /** A bucket's worth. */
    static final int BUCKET = FluidType.BUCKET_VOLUME;
    /** Half a minute: how long a place or a fallen minion it could not get to is left alone. */
    private static final int FORGET = 600;

    private MinionTender() {
    }

    // ---- what it carries

    /** A bucket of blood (any fluid a trough takes: the c:blood tag). */
    static boolean bloodBucket(ItemStack stack) {
        return stack.getItem() instanceof BucketItem && FluidUtil.getFluidContained(stack)
                .filter(f -> f.getAmount() == BUCKET && f.getFluid().is(BloodTroughBlockEntity.BLOOD)).isPresent();
    }

    static boolean emptyBucket(ItemStack stack) {
        return stack.is(Items.BUCKET);
    }

    static boolean fullCanister(ItemStack stack) {
        return stack.is(BBItems.SOUL_CANISTER.get());
    }

    static boolean emptyCanister(ItemStack stack) {
        return stack.is(BBItems.EMPTY_SOUL_CANISTER.get());
    }

    static boolean sheet(ItemStack stack) {
        return stack.is(AllItems.BRASS_SHEET.get());
    }

    /** How many of what this tests for it carries. */
    static int held(MinionEntity minion, Predicate<ItemStack> what) {
        int n = 0;
        for (int i = 0; i < minion.slots(); i++) {
            ItemStack stack = minion.inventory.getItem(i);
            if (!stack.isEmpty() && what.test(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** How many more of this it has room for in its slots, as {@link MinionEntity#carry} fills them. */
    static int room(MinionEntity minion, ItemStack like) {
        int n = 0;
        for (int i = 0; i < minion.slots(); i++) {
            ItemStack in = minion.inventory.getItem(i);
            if (in.isEmpty()) {
                n += like.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(in, like)) {
                n += Math.max(0, in.getMaxStackSize() - in.getCount());
            }
        }
        return n;
    }

    /** One of the stack in this slot is used up and becomes this (a bucket emptied, a canister spent). */
    private static void replaceOne(MinionEntity minion, int slot, ItemStack becomes) {
        ItemStack stack = minion.inventory.getItem(slot);
        if (stack.getCount() <= 1) {
            minion.inventory.setItem(slot, becomes);
        } else {
            stack.shrink(1);
            // checked to fit before anything was used; were it not to, it is dropped at its feet, never lost
            MinionTasks.keep(minion, becomes);
        }
    }

    /** Whether using up one in this slot leaves somewhere for what it becomes. */
    private static boolean roomAfter(MinionEntity minion, int slot, ItemStack becomes) {
        return minion.inventory.getItem(slot).getCount() <= 1 || room(minion, becomes) > 0;
    }

    // ---- what lies within its reach of home

    /** A trough, and how much more blood it takes. */
    record Trough(BlockPos pos, int room) {
    }

    /** A cradle: how many full canisters and brass sheets it wants, and how many empties it holds. */
    record Cradle(BlockPos pos, int canisters, int sheets, int empties) {
    }

    /**
     * What it looked over: its maker's fallen minions, the troughs and cradles with room, the tanks and trays with a bucket of
     * blood in them, and the containers, each list nearest the Tender first.
     */
    record Survey(List<MinionEntity> fallen, List<Trough> troughs, List<Cradle> cradles, List<BlockPos> tanks, List<BlockPos> stores) {
        int canistersWanted() {
            return (int) fallen.stream().filter(MinionEntity::cybernetic).count() + cradles.stream().mapToInt(Cradle::canisters).sum();
        }

        int sheetsWanted() {
            return cradles.stream().mapToInt(Cradle::sheets).sum();
        }

        int troughRoom() {
            return troughs.stream().mapToInt(Trough::room).sum();
        }
    }

    /** Whether this is a container it takes from and puts back into: a chest, barrel, shulker box or Create Item Vault. */
    static boolean store(BlockEntity be) {
        BlockState state = be.getBlockState();
        return (state.is(Tags.Blocks.CHESTS) || state.is(Tags.Blocks.BARRELS) || state.is(BlockTags.SHULKER_BOXES) || be instanceof ItemVaultBlockEntity)
                && be.getLevel() != null && be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), null) != null;
    }

    /** Where a Create tank of many blocks is asked: its controller, or itself while it has none (just placed). */
    private static BlockPos controller(FluidTankBlockEntity tank) {
        BlockPos at = tank.getController();
        return at != null ? at : tank.getBlockPos();
    }

    /**
     * A Create Fluid Tank or a Bleeding Rack's tray with a whole bucket of blood to give, as a bucket can be filled with. A
     * tank of many blocks is one tank: asked at its controller, as each of its blocks hands on the controller's.
     */
    static boolean tank(ServerLevel level, BlockEntity be) {
        if (!(be instanceof FluidTankBlockEntity) && !(be instanceof BleedingRackBlockEntity)) {
            return false;
        }
        BlockPos at = be instanceof FluidTankBlockEntity tank ? controller(tank) : be.getBlockPos();
        // a controller across a chunk's edge that is not loaded is not looked at (looking would load it)
        IFluidHandler handler = level.isLoaded(at) ? level.getCapability(Capabilities.FluidHandler.BLOCK, at, null) : null;
        if (handler == null) {
            return false;
        }
        FluidStack there = handler.drain(BUCKET, IFluidHandler.FluidAction.SIMULATE);
        return there.getAmount() == BUCKET && there.getFluid().is(BloodTroughBlockEntity.BLOOD)
                && FluidUtil.tryFillContainer(new ItemStack(Items.BUCKET), handler, BUCKET, null, false).isSuccess();
    }

    /** The tanks a Tender's look finds now, nearest first: a tank of many blocks once, at its block nearest it (for the tests). */
    public static List<BlockPos> tanksFound(ServerLevel level, MinionEntity minion) {
        return survey(level, minion, new Tend(minion)).tanks();
    }

    /** What lies within its reach of home now, from the loaded chunks' block entities (none is loaded to look), less what it skips. */
    static Survey survey(ServerLevel level, MinionEntity minion, Tend skips) {
        BlockPos home = minion.home();
        Vec3 centre = Vec3.atBottomCenterOf(home);
        double reach = minion.reach();
        int sheets = Math.round(PartsData.of(level).task(MinionTask.TENDER).number("sheets", 16.0F));
        List<Trough> troughs = new ArrayList<>();
        List<Cradle> cradles = new ArrayList<>();
        List<BlockPos> tanks = new ArrayList<>();
        List<BlockPos> stores = new ArrayList<>();
        // a Create tank of many blocks is asked once, at its controller, and gone to at its block nearest the Tender
        java.util.Map<BlockPos, Boolean> tankHasBlood = new java.util.HashMap<>();
        java.util.Map<BlockPos, BlockPos> tankNearest = new java.util.HashMap<>();
        int r = Mth.ceil(reach);
        for (int cx = (home.getX() - r) >> 4; cx <= (home.getX() + r) >> 4; cx++) {
            for (int cz = (home.getZ() - r) >> 4; cz <= (home.getZ() + r) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (pos.distToCenterSqr(centre) >= reach * reach || skips.places.contains(minion, pos)) {
                        continue;
                    }
                    if (be instanceof BloodTroughBlockEntity trough) {
                        int room = BloodTroughBlockEntity.CAPACITY - trough.amount();
                        if (room > 0) {
                            troughs.add(new Trough(pos.immutable(), room));
                        }
                    } else if (be instanceof ChargingCradleBlockEntity cradle) {
                        int canisters = 0;
                        int empties = 0;
                        for (int i = 0; i < ChargingCradleBlockEntity.SHEETS; i++) {
                            boolean empty = cradle.inventory.getStackInSlot(i).isEmpty();
                            if (i < ChargingCradleBlockEntity.FULL && empty) {
                                canisters++;
                            } else if (i >= ChargingCradleBlockEntity.FULL && !empty) {
                                empties++;
                            }
                        }
                        int wanted = Math.max(0, sheets - cradle.inventory.getStackInSlot(ChargingCradleBlockEntity.SHEETS).getCount());
                        if (canisters > 0 || wanted > 0 || empties > 0) {
                            cradles.add(new Cradle(pos.immutable(), canisters, wanted, empties));
                        }
                    } else if (be instanceof FluidTankBlockEntity part) {
                        BlockPos controller = controller(part);
                        if (tankHasBlood.computeIfAbsent(controller, c -> tank(level, part))) {
                            tankNearest.merge(controller, pos.immutable(), (a, b) -> minion.distanceToSqr(Vec3.atCenterOf(b)) < minion.distanceToSqr(Vec3.atCenterOf(a)) ? b : a);
                        }
                    } else if (tank(level, be)) {
                        tanks.add(pos.immutable());
                    } else if (store(be)) {
                        stores.add(pos.immutable());
                    }
                }
            }
        }
        tanks.addAll(tankNearest.values());
        List<MinionEntity> fallen = level.getEntitiesOfClass(MinionEntity.class, new AABB(home).inflate(reach),
                m -> m != minion && m.isAlive() && m.poweredDown() && m.build().isPresent() && minion.makerId() != null
                        && minion.makerId().equals(m.makerId()) && m.distanceToSqr(centre) < reach * reach && !skips.skipped(m));
        Comparator<BlockPos> near = Comparator.comparingDouble(p -> minion.distanceToSqr(Vec3.atCenterOf(p)));
        troughs.sort(Comparator.comparing(Trough::pos, near));
        cradles.sort(Comparator.comparing(Cradle::pos, near));
        tanks.sort(near);
        stores.sort(near);
        fallen.sort(Comparator.comparingDouble(minion::distanceToSqr));
        return new Survey(fallen, troughs, cradles, tanks, stores);
    }

    // ---- a fallen minion's bucket (no blood is lost)

    /**
     * Where the blood of a bucket poured into a fallen flesh minion goes (none is spilled): what the fallen one holds, the
     * rest into the Tender itself up to its own room (flesh only), and what is left into troughs by home, nearest the
     * fallen one first, each up to its room. Null if the rest has nowhere to go: then it does not pour.
     *
     * @param fallenRoom  how much more the fallen one holds
     * @param ownRoom     how much more the Tender holds (0 for brass, which does not drink blood)
     * @param troughRooms how much more each trough takes, in the order they are filled
     * @return {fallen, Tender, then each trough's share}, adding up to a bucket
     */
    @Nullable
    public static int[] share(int fallenRoom, int ownRoom, int[] troughRooms) {
        int[] out = new int[2 + troughRooms.length];
        out[0] = Math.max(0, Math.min(BUCKET, fallenRoom));
        int rest = BUCKET - out[0];
        out[1] = Math.max(0, Math.min(rest, ownRoom));
        rest -= out[1];
        for (int i = 0; i < troughRooms.length && rest > 0; i++) {
            out[2 + i] = Math.max(0, Math.min(rest, troughRooms[i]));
            rest -= out[2 + i];
        }
        return out[0] > 0 && rest == 0 ? out : null;
    }

    private static int roomOf(MinionEntity minion) {
        return Math.max(0, (int) Math.floor(minion.stats().reservoir() - minion.power()));
    }

    /** The troughs of the survey nearest this fallen minion first, with the room each has. */
    private static List<Trough> troughsNear(Survey survey, MinionEntity fallen) {
        List<Trough> out = new ArrayList<>(survey.troughs());
        out.sort(Comparator.comparingDouble(t -> fallen.distanceToSqr(Vec3.atCenterOf(t.pos()))));
        return out;
    }

    // ---- the errands

    enum Kind {
        /** A bucket of blood into a fallen flesh minion, a canister into a fallen brass one. */
        WAKE,
        /** Buckets of blood into a trough. */
        POUR,
        /** Full canisters and brass sheets into a cradle. */
        STOCK,
        /** Empty buckets filled at a tank or a rack's tray. */
        FILL,
        /** What it wants, out of a container. */
        TAKE,
        /** A cradle's empty canisters taken out of it. */
        EMPTIES,
        /** What it no longer wants, back into a container. */
        PUT_BACK
    }

    /** How many of each it still wants to carry, for what it puts back. */
    record Keep(int blood, int empties, int canisters, int sheets) {
    }

    /** One trip: where to, what for, how many, and (for putting back) what it keeps. */
    record Errand(Kind kind, @Nullable BlockPos pos, @Nullable MinionEntity patient, @Nullable Predicate<ItemStack> what, int count, Keep keep) {
    }

    /** The first container that has this to give. */
    @Nullable
    private static BlockPos storeWith(ServerLevel level, Survey survey, Predicate<ItemStack> what) {
        for (BlockPos pos : survey.stores()) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler == null) {
                continue;
            }
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty() && what.test(stack) && !handler.extractItem(i, 1, true).isEmpty()) {
                    return pos;
                }
            }
        }
        return null;
    }

    /** The first container that would take some of this. */
    @Nullable
    private static BlockPos storeFor(ServerLevel level, Survey survey, ItemStack stack) {
        for (BlockPos pos : survey.stores()) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler != null && ItemHandlerHelper.insertItemStacked(handler, stack.copy(), true).getCount() < stack.getCount()) {
                return pos;
            }
        }
        return null;
    }

    /**
     * How many of this stack it no longer wants, counting down what it keeps of each kind as it goes (the first it comes to
     * are kept): blood buckets, empty buckets, canisters and sheets it still has a use for stay; empty canisters and anything
     * else go back.
     */
    private static int unwanted(ItemStack stack, int[] keep) {
        int kind = bloodBucket(stack) ? 0 : emptyBucket(stack) ? 1 : fullCanister(stack) ? 2 : sheet(stack) ? 3 : -1;
        if (kind < 0) {
            return stack.getCount();
        }
        int kept = Math.min(stack.getCount(), keep[kind]);
        keep[kind] -= kept;
        return stack.getCount() - kept;
    }

    /**
     * A Tender's work, one errand at a time: each look it surveys its reach of home and picks the most pressing thing it can
     * do now, walks there and does it. Fallen minions come first, then troughs, then cradles; then fetching what they want
     * (buckets of blood, or empty buckets to fill at a tank, canisters, sheets); then the cradles' empties; last, putting
     * back what it no longer needs. With something wanted and nothing to be had, its status line says so.
     */
    static class Tend extends Goal {
        private final MinionEntity minion;
        @Nullable
        private Errand errand;
        @Nullable
        private Survey survey;
        private boolean done;
        private final MinionGoals.Approach approach = new MinionGoals.Approach();
        /** Places it could not get to, or found would not do what they looked like doing, left alone for half a minute. */
        final MinionGoals.Unreachable places = new MinionGoals.Unreachable();
        /** Fallen minions it could not get to, or could not pour a bucket into without spilling, by id; forgotten every half minute. */
        private final List<UUID> passedOver = new ArrayList<>();
        private int forgotAt;

        Tend(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        boolean skipped(MinionEntity fallen) {
            if (minion.tickCount - forgotAt > FORGET) {
                passedOver.clear();
                forgotAt = minion.tickCount;
            }
            return passedOver.contains(fallen.getUUID());
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!minion.hasTask(MinionTask.TENDER) || !(minion.level() instanceof ServerLevel level) || !MinionGoals.looks(minion, MinionTask.TENDER)) {
                return false;
            }
            survey = survey(level, minion, this);
            errand = plan(level, survey);
            return errand != null;
        }

        @Override
        public boolean canContinueToUse() {
            return errand != null && !done && minion.hasTask(MinionTask.TENDER)
                    && (errand.patient() == null ? minion.level().isLoaded(errand.pos()) : errand.patient().isAlive() && errand.patient().poweredDown());
        }

        @Override
        public void start() {
            minion.working = true;
            done = false;
            approach.reset(minion);
        }

        @Override
        public void stop() {
            minion.working = false;
            errand = null;
            survey = null;
        }

        /** The most pressing errand it can run now, or null (and, with something wanted and nothing to be had, why). */
        @Nullable
        private Errand plan(ServerLevel level, Survey s) {
            int blood = held(minion, MinionTender::bloodBucket);
            int empties = held(minion, MinionTender::emptyBucket);
            int canisters = held(minion, MinionTender::fullCanister);
            int sheets = held(minion, MinionTender::sheet);
            // the fallen first: a bucket for flesh, if the rest of it has somewhere to go; a canister for brass
            int fleshWanted = 0;
            for (MinionEntity fallen : s.fallen()) {
                if (fallen.cybernetic()) {
                    if (canisters > 0 && fallen.stats().reservoir() - fallen.power() >= MinionStats.CANISTER) {
                        return errand(Kind.WAKE, null, fallen, null, 1, s);
                    }
                } else if (pourable(s, fallen)) {
                    fleshWanted++;
                    if (blood > 0) {
                        return errand(Kind.WAKE, null, fallen, null, 1, s);
                    }
                } else {
                    minion.idle(Component.translatable("bloodandbones.minion.idle.tender_rest"));
                }
            }
            // then the troughs
            if (blood > 0) {
                for (Trough trough : s.troughs()) {
                    if (trough.room() >= BUCKET && pours(level, trough.pos())) {
                        return errand(Kind.POUR, trough.pos(), null, null, trough.room() / BUCKET, s);
                    }
                }
            }
            // then the cradles
            for (Cradle cradle : s.cradles()) {
                if (canisters > 0 && cradle.canisters() > 0 || sheets > 0 && cradle.sheets() > 0) {
                    return errand(Kind.STOCK, cradle.pos(), null, null, 0, s);
                }
            }
            // fetching what they want: blood by the bucket, or empty buckets to fill; canisters; sheets
            int bloodWanted = fleshWanted + s.troughs().stream().mapToInt(t -> t.room() / BUCKET).sum() - blood;
            boolean wantsBlood = bloodWanted > 0;
            if (wantsBlood) {
                // a bucket of blood takes a slot of its own
                BlockPos from = MinionTasks.freeSlot(minion) ? storeWith(level, s, MinionTender::bloodBucket) : null;
                if (from != null) {
                    return errand(Kind.TAKE, from, null, MinionTender::bloodBucket, bloodWanted, s);
                }
                if (!s.tanks().isEmpty()) {
                    if (empties > 0) {
                        return errand(Kind.FILL, s.tanks().get(0), null, null, bloodWanted, s);
                    }
                    from = room(minion, new ItemStack(Items.BUCKET)) > 0 ? storeWith(level, s, MinionTender::emptyBucket) : null;
                    if (from != null) {
                        return errand(Kind.TAKE, from, null, MinionTender::emptyBucket, bloodWanted, s);
                    }
                }
            }
            int canistersWanted = s.canistersWanted() - canisters;
            if (canistersWanted > 0 && room(minion, new ItemStack(BBItems.SOUL_CANISTER.get())) > 0) {
                BlockPos from = storeWith(level, s, MinionTender::fullCanister);
                if (from != null) {
                    return errand(Kind.TAKE, from, null, MinionTender::fullCanister, canistersWanted, s);
                }
            }
            int sheetsWanted = s.sheetsWanted() - sheets;
            if (sheetsWanted > 0 && room(minion, new ItemStack(AllItems.BRASS_SHEET.get())) > 0) {
                BlockPos from = storeWith(level, s, MinionTender::sheet);
                if (from != null) {
                    return errand(Kind.TAKE, from, null, MinionTender::sheet, sheetsWanted, s);
                }
            }
            // the cradles' empties, if there is a container to take them to
            if (!s.stores().isEmpty() && room(minion, new ItemStack(BBItems.EMPTY_SOUL_CANISTER.get())) > 0) {
                for (Cradle cradle : s.cradles()) {
                    if (cradle.empties() > 0) {
                        return errand(Kind.EMPTIES, cradle.pos(), null, null, cradle.empties(), s);
                    }
                }
            }
            // what it no longer wants, back
            int[] keep = keep(s, fleshWanted);
            for (int i = 0; i < minion.slots(); i++) {
                ItemStack stack = minion.inventory.getItem(i);
                int give = stack.isEmpty() ? 0 : unwanted(stack, keep);
                BlockPos to = give > 0 ? storeFor(level, s, stack.copyWithCount(give)) : null;
                if (to != null) {
                    return errand(Kind.PUT_BACK, to, null, null, 0, s);
                }
            }
            // nothing it can do: if something is wanted and nothing is to be had, it says so
            if (wantsBlood && blood == 0) {
                minion.idle(Component.translatable("bloodandbones.minion.idle.tender_blood", minion.reach()));
            } else if (canistersWanted > 0 && canisters == 0) {
                minion.idle(Component.translatable("bloodandbones.minion.idle.tender_canister", minion.reach()));
            }
            return null;
        }

        private Errand errand(Kind kind, @Nullable BlockPos pos, @Nullable MinionEntity patient, @Nullable Predicate<ItemStack> what, int count, Survey s) {
            int[] keep = keep(s, (int) s.fallen().stream().filter(f -> !f.cybernetic() && pourable(s, f)).count());
            return new Errand(kind, pos, patient, what, count, new Keep(keep[0], keep[1], keep[2], keep[3]));
        }

        /**
         * How many of each it still wants to carry: blood buckets for the fallen and the troughs; empty buckets as many as
         * that is short of, while a tank has blood to fill them; canisters and sheets for the fallen brass and the cradles.
         */
        private int[] keep(Survey s, int fleshWanted) {
            int bloodWanted = fleshWanted + s.troughs().stream().mapToInt(t -> t.room() / BUCKET).sum();
            int blood = held(minion, MinionTender::bloodBucket);
            int short_ = s.tanks().isEmpty() ? 0 : Math.max(0, bloodWanted - blood);
            return new int[]{bloodWanted, short_, s.canistersWanted(), s.sheetsWanted()};
        }

        /** Whether a bucket poured into this fallen flesh minion has somewhere for every drop. */
        private boolean pourable(Survey s, MinionEntity fallen) {
            int[] rooms = troughsNear(s, fallen).stream().mapToInt(Trough::room).toArray();
            return share(roomOf(fallen), minion.cybernetic() ? 0 : roomOf(minion), rooms) != null;
        }

        /** Whether a bucket it carries would pour into this trough whole. */
        private boolean pours(ServerLevel level, BlockPos pos) {
            if (!(level.getBlockEntity(pos) instanceof BloodTroughBlockEntity trough)) {
                return false;
            }
            for (int i = 0; i < minion.slots(); i++) {
                ItemStack stack = minion.inventory.getItem(i);
                if (bloodBucket(stack) && FluidUtil.tryEmptyContainer(stack, trough.tank(), BUCKET, null, false).isSuccess()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void tick() {
            if (errand == null || done || !(minion.level() instanceof ServerLevel level)) {
                return;
            }
            MinionEntity patient = errand.patient();
            if (patient != null) {
                minion.getLookControl().setLookAt(patient);
                double near = 2.0 + minion.getBbWidth() / 2.0 + patient.getBbWidth() / 2.0;
                if (minion.distanceToSqr(patient) > near * near) {
                    if (!approach.step(minion, patient.blockPosition(), 1, 1.0)) {
                        passedOver.add(patient.getUUID());
                        done = true;
                    }
                    return;
                }
            } else {
                BlockPos pos = errand.pos();
                minion.getLookControl().setLookAt(Vec3.atCenterOf(pos));
                if (minion.distanceToSqr(Vec3.atCenterOf(pos)) > 6.0 + minion.getBbWidth() * 2) {
                    if (!approach.step(minion, pos, 1, 1.0)) {
                        places.add(pos);
                        done = true;
                    }
                    return;
                }
            }
            approach.reset(minion);
            minion.getNavigation().stop();
            boolean did = switch (errand.kind()) {
                case WAKE -> wake(level, patient);
                case POUR -> pour(level, errand.pos());
                case STOCK -> stock(level, errand.pos());
                case FILL -> fill(level, errand.pos(), errand.count());
                case TAKE -> take(level, errand.pos(), errand.what(), errand.count());
                case EMPTIES -> empties(level, errand.pos());
                case PUT_BACK -> putBack(level, errand.pos(), errand.keep());
            };
            if (did) {
                minion.swing(InteractionHand.MAIN_HAND);
            } else if (patient != null) {
                passedOver.add(patient.getUUID());
            } else {
                // it was not what it looked (taken meanwhile, filled by a pipe, full): left alone a while
                places.add(errand.pos());
            }
            done = true;
        }

        // ---- each errand, at its end: nothing moves unless all of it goes through

        /** A bucket of blood into a fallen flesh minion (the rest shared out, none spilled), or a canister into a fallen brass one. */
        private boolean wake(ServerLevel level, MinionEntity fallen) {
            if (fallen.cybernetic()) {
                for (int i = 0; i < minion.slots(); i++) {
                    ItemStack empty = new ItemStack(BBItems.EMPTY_SOUL_CANISTER.get());
                    if (fullCanister(minion.inventory.getItem(i)) && roomAfter(minion, i, empty)) {
                        // all of a canister goes in (brass holds one or two whole), so none is lost
                        if (fallen.stats().reservoir() - fallen.power() < MinionStats.CANISTER || !fallen.charge()) {
                            return false;
                        }
                        replaceOne(minion, i, empty);
                        level.playSound(null, fallen.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.NEUTRAL, 0.8F, 0.7F);
                        return true;
                    }
                }
                return false;
            }
            for (int i = 0; i < minion.slots(); i++) {
                ItemStack bucket = minion.inventory.getItem(i);
                if (!bloodBucket(bucket)) {
                    continue;
                }
                FluidStack blood = FluidUtil.getFluidContained(bucket).orElse(FluidStack.EMPTY);
                // where each drop goes, worked out before any is poured: the fallen one, the Tender, then the troughs
                List<Trough> troughs = troughsNear(survey(level, minion, this), fallen);
                int[] rooms = new int[troughs.size()];
                for (int t = 0; t < troughs.size(); t++) {
                    rooms[t] = level.getBlockEntity(troughs.get(t).pos()) instanceof BloodTroughBlockEntity trough
                            ? trough.tank().fill(blood.copyWithAmount(BUCKET), IFluidHandler.FluidAction.SIMULATE) : 0;
                }
                int[] share = share(roomOf(fallen), minion.cybernetic() ? 0 : roomOf(minion), rooms);
                if (share == null) {
                    minion.idle(Component.translatable("bloodandbones.minion.idle.tender_rest"));
                    return false;
                }
                // the bucket emptied (a copy of it: nothing in its hands changes until it is known to go through)
                var handler = FluidUtil.getFluidHandler(bucket.copyWithCount(1)).orElse(null);
                FluidStack out = handler == null ? FluidStack.EMPTY : handler.drain(BUCKET, IFluidHandler.FluidAction.EXECUTE);
                if (out.getAmount() != BUCKET || !roomAfter(minion, i, handler.getContainer())) {
                    return false;
                }
                replaceOne(minion, i, handler.getContainer());
                fallen.feed(share[0]);
                if (share[1] > 0) {
                    minion.feed(share[1]);
                }
                for (int t = 0; t < troughs.size(); t++) {
                    if (share[2 + t] > 0 && level.getBlockEntity(troughs.get(t).pos()) instanceof BloodTroughBlockEntity trough) {
                        trough.tank().fill(out.copyWithAmount(share[2 + t]), IFluidHandler.FluidAction.EXECUTE);
                    }
                }
                level.playSound(null, fallen.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 0.8F, 1.0F);
                return true;
            }
            return false;
        }

        /** Buckets of blood into a trough, as many as go in whole; the empties stay with it. */
        private boolean pour(ServerLevel level, BlockPos pos) {
            if (!(level.getBlockEntity(pos) instanceof BloodTroughBlockEntity trough)) {
                return false;
            }
            boolean any = false;
            for (int i = 0; i < minion.slots(); i++) {
                ItemStack bucket = minion.inventory.getItem(i);
                if (!bloodBucket(bucket) || !FluidUtil.tryEmptyContainer(bucket, trough.tank(), BUCKET, null, false).isSuccess()) {
                    continue;
                }
                FluidActionResult poured = FluidUtil.tryEmptyContainer(bucket, trough.tank(), BUCKET, null, true);
                if (poured.isSuccess()) {
                    replaceOne(minion, i, poured.getResult());
                    any = true;
                }
            }
            if (any) {
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return any;
        }

        /** Full canisters and brass sheets into a cradle, through its slots for pipes of items: what does not go in, it keeps. */
        private boolean stock(ServerLevel level, BlockPos pos) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler == null || !(level.getBlockEntity(pos) instanceof ChargingCradleBlockEntity cradle)) {
                return false;
            }
            int sheets = Math.round(PartsData.of(level).task(MinionTask.TENDER).number("sheets", 16.0F));
            boolean any = false;
            for (int i = 0; i < minion.slots(); i++) {
                ItemStack stack = minion.inventory.getItem(i);
                int give = fullCanister(stack) ? stack.getCount()
                        : sheet(stack) ? Math.min(stack.getCount(), Math.max(0, sheets - cradle.inventory.getStackInSlot(ChargingCradleBlockEntity.SHEETS).getCount())) : 0;
                if (give <= 0) {
                    continue;
                }
                ItemStack left = ItemHandlerHelper.insertItemStacked(handler, stack.copyWithCount(give), false);
                int went = give - left.getCount();
                if (went > 0) {
                    stack.shrink(went);
                    minion.inventory.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
                    any = true;
                }
            }
            if (any) {
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 0.9F);
            }
            return any;
        }

        /** Empty buckets filled at a tank or a rack's tray, a bucket at a time while it has blood to give and more is wanted. */
        private boolean fill(ServerLevel level, BlockPos pos, int wanted) {
            IFluidHandler source = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
            if (source == null || !tank(level, level.getBlockEntity(pos))) {
                return false;
            }
            int filled = 0;
            for (int i = 0; i < minion.slots() && filled < wanted; i++) {
                while (filled < wanted && emptyBucket(minion.inventory.getItem(i))) {
                    ItemStack one = minion.inventory.getItem(i).copyWithCount(1);
                    FluidStack there = source.drain(BUCKET, IFluidHandler.FluidAction.SIMULATE);
                    FluidActionResult tried = FluidUtil.tryFillContainer(one, source, BUCKET, null, false);
                    if (there.getAmount() != BUCKET || !there.getFluid().is(BloodTroughBlockEntity.BLOOD) || !tried.isSuccess() || !bloodBucket(tried.getResult())
                            || !roomAfter(minion, i, tried.getResult())) {
                        return filled > 0;
                    }
                    FluidActionResult full = FluidUtil.tryFillContainer(one, source, BUCKET, null, true);
                    if (!full.isSuccess()) {
                        return filled > 0;
                    }
                    replaceOne(minion, i, full.getResult());
                    filled++;
                    level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.8F, 1.0F);
                }
            }
            return filled > 0;
        }

        /** Up to this many of what it wants, out of a container, as many as it has room for. */
        private boolean take(ServerLevel level, BlockPos pos, @Nullable Predicate<ItemStack> what, int wanted) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler == null || what == null) {
                return false;
            }
            int taken = 0;
            for (int i = 0; i < handler.getSlots() && taken < wanted; i++) {
                ItemStack there = handler.getStackInSlot(i);
                if (there.isEmpty() || !what.test(there)) {
                    continue;
                }
                ItemStack could = handler.extractItem(i, wanted - taken, true);
                int n = Math.min(could.getCount(), room(minion, could));
                if (n <= 0) {
                    continue;
                }
                ItemStack got = handler.extractItem(i, n, false);
                ItemStack left = minion.carry(got);
                taken += got.getCount() - left.getCount();
                if (!left.isEmpty()) {
                    // it had room for it a moment ago: back where it came from, or else by its feet, never lost
                    left = handler.insertItem(i, left, false);
                    if (!left.isEmpty()) {
                        MinionTasks.keep(minion, left);
                    }
                }
            }
            return taken > 0;
        }

        /** A cradle's empty canisters out, as many as it has room for. */
        private boolean empties(ServerLevel level, BlockPos pos) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler == null || !(level.getBlockEntity(pos) instanceof ChargingCradleBlockEntity cradle)) {
                return false;
            }
            boolean any = false;
            for (int i = ChargingCradleBlockEntity.FULL; i < ChargingCradleBlockEntity.SHEETS; i++) {
                ItemStack could = handler.extractItem(i, 64, true);
                int n = could.isEmpty() ? 0 : Math.min(could.getCount(), room(minion, could));
                if (n <= 0) {
                    continue;
                }
                ItemStack got = handler.extractItem(i, n, false);
                ItemStack left = minion.carry(got);
                any |= left.getCount() < got.getCount();
                if (!left.isEmpty()) {
                    // back into the cradle's own slot (its slots for pipes take no empties in)
                    left = cradle.inventory.insertItem(i, left, false);
                    if (!left.isEmpty()) {
                        MinionTasks.keep(minion, left);
                    }
                }
            }
            return any;
        }

        /** What it no longer wants into a container: what does not fit, it keeps. */
        private boolean putBack(ServerLevel level, BlockPos pos, Keep keep) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler == null) {
                return false;
            }
            int[] kept = {keep.blood(), keep.empties(), keep.canisters(), keep.sheets()};
            boolean any = false;
            for (int i = 0; i < minion.slots(); i++) {
                ItemStack stack = minion.inventory.getItem(i);
                int give = stack.isEmpty() ? 0 : unwanted(stack, kept);
                if (give <= 0) {
                    continue;
                }
                ItemStack left = ItemHandlerHelper.insertItemStacked(handler, stack.copyWithCount(give), false);
                int went = give - left.getCount();
                if (went > 0) {
                    stack.shrink(went);
                    minion.inventory.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
                    any = true;
                }
            }
            return any;
        }
    }
}
