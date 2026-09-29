package com.avicagan.bloodandbones.cooking;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The spit and what is on it: a carried piece (a leg, a head, a whole chicken), or a whole carcass, every piece of it,
 * skewered from the world with a Meat Hook (the brief: "cook whole carcasses and limbs"). Cooking needs both heat below
 * and the spit turning, and goes as fast as the spit turns: a Hand Crank's 32 RPM is the slow way, a shaft at 256 RPM
 * eight times faster (the brief: "much faster on a shaft"). Hotter fire cooks faster too. Left on for twice as long as it
 * needs, it burns to charcoal.
 */
public class SpitRoastBlockEntity extends KineticBlockEntity {
    /** Fewest and most ticks a piece takes at a campfire at a Hand Crank's speed (the server config's defaults, which are read). */
    public static final int MIN_COOK = 200;
    public static final int MAX_COOK = 1200;
    /** Most a whole carcass takes. */
    public static final int MAX_COOK_WHOLE = 4800;
    /** Ticks of cooking per cubic block of meat. */
    public static final int COOK_PER_BLOCK = 2400;
    /** The speed that cooks at the campfire's own pace (a Hand Crank's), and the speed past which it cooks no faster. */
    public static final float BASE_RPM = 32.0F;
    public static final float TOP_RPM = 256.0F;

    /** What is on the spit: one carried piece, or every piece of a whole carcass (the torso first). */
    private final List<ItemStack> pieces = new ArrayList<>();
    /**
     * Whether it was skewered from the world with the Meat Hook, however many pieces that is (a body with its limbs cut off
     * is one): too heavy to carry, so taken off raw it is set down again as a body, never handed over.
     */
    private boolean carcass;
    /** Cooking done, in campfire-ticks at a Hand Crank's speed. */
    public float progress;
    private int syncTicks;

    public SpitRoastBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The first piece on the spit (the torso of a whole carcass), or nothing. */
    public ItemStack piece() {
        return pieces.isEmpty() ? ItemStack.EMPTY : pieces.getFirst();
    }

    /** Every piece on the spit. */
    public List<ItemStack> pieces() {
        return pieces;
    }

    /** Whether a carcass skewered from the world is on it, not a carried piece. */
    public boolean whole() {
        return carcass && !pieces.isEmpty();
    }

    /** Ticks what is on the spit needs at a campfire, by how much meat there is. */
    public int cookTime() {
        // asked on the client too (browning, goggles), where only the rigs the server sent are known
        boolean client = level != null && level.isClientSide;
        float volume = 0.0F;
        for (ItemStack stack : pieces) {
            CarcassPieceItem.Piece data = CarcassPieceItem.piece(stack);
            Bone bone = data == null ? null : (client ? RigManager.clientRig(data.entity(), data.baby()) : RigManager.forEntity(data.entity(), data.baby()))
                    .flatMap(rig -> rig.bone(data.bone())).orElse(null);
            if (bone != null) {
                org.joml.Vector3f size = bone.boxSize();
                volume += size.x * size.y * size.z / 4096.0F;
            }
        }
        int min = com.avicagan.bloodandbones.config.BBServerConfig.roastMin();
        return Math.max(min, Math.min(whole() ? com.avicagan.bloodandbones.config.BBServerConfig.roastMaxWhole() : com.avicagan.bloodandbones.config.BBServerConfig.roastMax(), min + Math.round(volume * com.avicagan.bloodandbones.config.BBServerConfig.roastPerBlock())));
    }

    public boolean isCooked() {
        return !pieces.isEmpty() && progress >= cookTime();
    }

    public boolean isBurnt() {
        return !pieces.isEmpty() && progress >= 2 * cookTime();
    }

    /** How brown it is, 0 raw to 1 cooked, past 1 toward burnt. */
    public float doneness() {
        return pieces.isEmpty() ? 0 : progress / cookTime();
    }

    /** Put a carried piece on the empty spit. */
    public boolean skewer(ItemStack stack) {
        if (!pieces.isEmpty() || CarcassPieceItem.piece(stack) == null) {
            return false;
        }
        pieces.add(stack);
        carcass = false;
        progress = 0;
        notifyUpdate();
        return true;
    }

    /**
     * Take a whole carcass off the world and onto the empty spit: every piece it still has, limbs and all, the torso
     * first. Its bodies go; the spit keeps what they were.
     */
    public boolean skewer(ServerLevel level, CarcassSavedData.Carcass carcass) {
        if (!pieces.isEmpty()) {
            return false;
        }
        List<String> bones = new ArrayList<>(com.avicagan.bloodandbones.carcass.CarcassRot.pieces(carcass));
        if (bones.remove(carcass.rootBone)) {
            bones.addFirst(carcass.rootBone);
        }
        for (String bone : bones) {
            pieces.add(CarcassPieceItem.of(carcass, bone));
        }
        Vec3 at = Vec3.atCenterOf(worldPosition);
        CarcassButchery.takeAway(level, carcass);
        this.carcass = true;
        progress = 0;
        level.playSound(null, at.x, at.y, at.z, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_CUT.get(), SoundSource.BLOCKS, 1.0F, 0.5F);
        if (com.avicagan.bloodandbones.carcass.Blood.bloody(carcass)) {
            com.avicagan.bloodandbones.carcass.Blood.burst(level, new org.joml.Vector3d(at.x, at.y + 0.3, at.z), 16, com.avicagan.bloodandbones.carcass.Blood.soul(carcass));
        }
        notifyUpdate();
        return true;
    }

    /** Heat from what is under the spit: 0 for none. */
    public float heat() {
        if (level == null) {
            return 0;
        }
        BlockState below = level.getBlockState(worldPosition.below());
        if (below.getBlock() instanceof CampfireBlock) {
            return below.getValue(CampfireBlock.LIT) ? 1.0F : 0.0F;
        }
        if (below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE) || below.is(Blocks.LAVA)) {
            return 1.0F;
        }
        if (below.is(Blocks.MAGMA_BLOCK)) {
            return 0.5F;
        }
        if (below.getBlock() instanceof BlazeBurnerBlock) {
            return switch (BlazeBurnerBlock.getHeatLevelOf(below)) {
                case NONE -> 0.0F;
                case SMOULDERING -> 0.5F;
                case FADING, KINDLED -> 2.0F;
                case SEETHING -> 3.0F;
            };
        }
        return 0.0F;
    }

    /** How much faster than a Hand Crank it cooks at this speed: 1 at 32 RPM, 8 at 256. */
    public float turning() {
        return Math.min(Math.abs(getSpeed()), TOP_RPM) / BASE_RPM;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || pieces.isEmpty()) {
            return;
        }
        float heat = heat();
        if (heat <= 0 || getSpeed() == 0) {
            return;
        }
        boolean wasCooked = isCooked();
        boolean wasBurnt = isBurnt();
        progress += heat * turning();
        if (level.isClientSide) {
            int spread = whole() ? 3 : 1;
            if (level.random.nextInt(isBurnt() ? 3 : 8) == 0) {
                for (int i = 0; i < spread; i++) {
                    level.addParticle(isBurnt() ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                            worldPosition.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4 * (i + 1), worldPosition.getY() + 0.8,
                            worldPosition.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4 * (i + 1), 0, 0.03, 0);
                }
            }
            return;
        }
        if (level.random.nextInt(40) == 0) {
            level.playSound(null, worldPosition, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 1.4F);
        }
        if (isCooked() != wasCooked || isBurnt() != wasBurnt || ++syncTicks >= 20) {
            syncTicks = 0;
            sendData();
        }
    }

    /**
     * Take it off. Cooked, it comes apart into its cooked yields. Raw, a carried piece comes back as it went on; a carcass
     * from the world is too heavy to carry, and is set down on the spit again, whole as it went on.
     */
    public void takeOff(Player player) {
        if (pieces.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (isCooked()) {
            for (ItemStack stack : cookedYields(serverLevel)) {
                player.getInventory().placeItemBackInInventory(stack);
            }
        } else if (!whole()) {
            player.getInventory().placeItemBackInInventory(pieces.getFirst());
        } else {
            setDown(serverLevel, pieces, Vec3.atBottomCenterOf(worldPosition.above()), player.getYRot());
        }
        pieces.clear();
        carcass = false;
        progress = 0;
        notifyUpdate();
    }

    /**
     * A raw carcass taken off the spit, set down in the world as a body again: put back together whole, every piece at
     * its place on the animal and joined as it was (CarcassAssembler#assembleWhole); a piece that cannot go back on (no
     * room, or its way to the torso is gone) is set down on its own, as a carried piece is.
     */
    private static void setDown(ServerLevel level, List<ItemStack> stacks, Vec3 at, float yaw) {
        CarcassPieceItem.Piece root = stacks.isEmpty() ? null : CarcassPieceItem.piece(stacks.getFirst());
        var rig = root == null ? null : RigManager.forEntity(root.entity(), root.baby()).orElse(null);
        java.util.Set<String> back = java.util.Set.of();
        if (rig != null) {
            List<String> bones = new ArrayList<>();
            for (ItemStack stack : stacks) {
                CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
                if (piece != null && piece.entity().equals(root.entity())) {
                    bones.add(piece.bone());
                }
            }
            CarcassSavedData.Carcass whole = com.avicagan.bloodandbones.carcass.CarcassAssembler.assembleWhole(level, rig, root.bone(), bones, root.baby(),
                    root.look(), root.freshness(), at, yaw);
            if (whole != null) {
                keep(level, whole, root);
                back = java.util.Set.copyOf(whole.bones.keySet());
            }
        }
        for (ItemStack stack : stacks) {
            CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
            if (piece != null && !back.contains(piece.bone())) {
                setDown(level, piece, at, yaw);
            }
        }
    }

    /** One piece set down in the world as a body of its own (as a carried piece is). */
    private static void setDown(ServerLevel level, CarcassPieceItem.Piece piece, Vec3 at, float yaw) {
        var rig = RigManager.forEntity(piece.entity(), piece.baby()).orElse(null);
        Bone bone = rig == null ? null : rig.bone(piece.bone()).orElse(null);
        if (bone == null) {
            return;
        }
        CarcassSavedData.Carcass carcass = com.avicagan.bloodandbones.carcass.CarcassAssembler.assemblePiece(level, rig, bone, piece.look(), piece.freshness(), at, yaw);
        if (carcass != null) {
            keep(level, carcass, piece);
        }
    }

    /** A body set down from the spit keeps what its piece kept: skinned or not, its traits, its blood and its rot. */
    private static void keep(ServerLevel level, CarcassSavedData.Carcass carcass, CarcassPieceItem.Piece piece) {
        carcass.skinned = piece.skinned();
        carcass.traits.putAll(piece.traits());
        com.avicagan.bloodandbones.body.Surgery.putDown(carcass.traits, piece.bone());
        carcass.blood = piece.blood();
        carcass.bloodMax = piece.bloodMax();
        carcass.decay = piece.decay();
        carcass.baby = piece.baby();
        com.avicagan.bloodandbones.carcass.CarcassRot.sync(level, carcass, null);
    }

    /** What it gives cooked: each piece's butchery yields, each smelted where it can be; burnt, charcoal and bones. */
    public List<ItemStack> cookedYields(ServerLevel level) {
        List<ItemStack> raw = new ArrayList<>();
        for (ItemStack stack : pieces) {
            CarcassPieceItem.Piece data = CarcassPieceItem.piece(stack);
            if (data != null) {
                raw.addAll(CarcassButchery.pieceYields(level, data));
            }
        }
        List<ItemStack> out = new ArrayList<>();
        boolean burnt = isBurnt();
        for (ItemStack stack : raw) {
            if (burnt && !stack.is(Items.BONE) && !stack.is(Items.BONE_MEAL)) {
                continue;
            }
            ItemStack cooked = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level)
                    .map(recipe -> recipe.value().getResultItem(level.registryAccess()).copyWithCount(stack.getCount()))
                    .orElse(stack);
            out.add(cooked);
        }
        if (burnt) {
            out.add(new ItemStack(Items.CHARCOAL, whole() ? 1 + pieces.size() / 2 : 1));
        }
        return out;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        if (pieces.isEmpty()) {
            return true;
        }
        String state = isBurnt() ? "burnt" : isCooked() ? "cooked" : heat() <= 0 ? "no_heat" : "roasting";
        new LangBuilder(BloodAndBones.MOD_ID).translate("gui.goggles.spit_roast." + state, Math.min(100, Math.round(doneness() * 100)))
                .style(isBurnt() ? ChatFormatting.DARK_GRAY : isCooked() ? ChatFormatting.GOLD : ChatFormatting.GRAY).forGoggles(tooltip);
        if (state.equals("roasting") && getSpeed() != 0) {
            new LangBuilder(BloodAndBones.MOD_ID).translate("gui.goggles.spit_roast.speed", String.format(java.util.Locale.ROOT, "%.1f", turning()))
                    .style(ChatFormatting.GRAY).forGoggles(tooltip);
        }
        return true;
    }

    @Override
    public void destroy() {
        super.destroy();
        if (level instanceof ServerLevel serverLevel && whole()) {
            setDown(serverLevel, pieces, Vec3.atCenterOf(worldPosition), 0.0F);
        } else if (level != null) {
            for (ItemStack stack : pieces) {
                net.minecraft.world.level.block.Block.popResource(level, worldPosition, stack);
            }
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        ListTag list = new ListTag();
        for (ItemStack stack : pieces) {
            list.add(stack.save(registries));
        }
        tag.put("Pieces", list);
        tag.putBoolean("Carcass", carcass);
        tag.putFloat("Progress", progress);
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        pieces.clear();
        // spits saved before whole carcasses held one piece
        if (tag.contains("Piece")) {
            ItemStack.parse(registries, tag.getCompound("Piece")).ifPresent(pieces::add);
        }
        for (Tag entry : tag.getList("Pieces", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, entry).ifPresent(pieces::add);
        }
        // spits saved before the flag knew a whole carcass by its many pieces
        carcass = tag.contains("Carcass") ? tag.getBoolean("Carcass") : pieces.size() > 1;
        progress = tag.getFloat("Progress");
        super.read(tag, registries, clientPacket);
    }
}
