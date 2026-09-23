package com.avicagan.bloodandbones.cooking;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.butchery.ButcheryManager;
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
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The spit and what is on it. Cooking needs both heat below and the spit turning; faster turning (up to
 * 64 RPM) and hotter fire cook faster. Left on for twice as long as it needs, it burns to charcoal.
 */
public class SpitRoastBlockEntity extends KineticBlockEntity {
    /** Fewest and most ticks a piece takes at a campfire and a slow turn. */
    public static final int MIN_COOK = 200;
    public static final int MAX_COOK = 1200;
    /** Ticks of cooking per cubic block of piece. */
    public static final int COOK_PER_BLOCK = 2400;

    private ItemStack piece = ItemStack.EMPTY;
    /** Cooking done, in campfire-ticks. */
    public float progress;
    private int syncTicks;

    public SpitRoastBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ItemStack piece() {
        return piece;
    }

    /** Ticks the piece on the spit needs at a campfire. */
    public int cookTime() {
        CarcassPieceItem.Piece data = CarcassPieceItem.piece(piece);
        // asked on the client too (browning, goggles), where only the rigs the server sent are known
        boolean client = level != null && level.isClientSide;
        Bone bone = data == null ? null : (client ? RigManager.clientRig(data.entity(), data.baby()) : RigManager.forEntity(data.entity(), data.baby()))
                .flatMap(rig -> rig.bone(data.bone())).orElse(null);
        if (bone == null) {
            return MIN_COOK;
        }
        org.joml.Vector3f size = bone.boxSize();
        float volume = size.x * size.y * size.z / 4096.0F;
        return Math.max(MIN_COOK, Math.min(MAX_COOK, MIN_COOK + Math.round(volume * COOK_PER_BLOCK)));
    }

    public boolean isCooked() {
        return !piece.isEmpty() && progress >= cookTime();
    }

    public boolean isBurnt() {
        return !piece.isEmpty() && progress >= 2 * cookTime();
    }

    /** How brown the piece is, 0 raw to 1 cooked, past 1 toward burnt. */
    public float doneness() {
        return piece.isEmpty() ? 0 : progress / cookTime();
    }

    public boolean skewer(ItemStack stack) {
        if (!piece.isEmpty() || CarcassPieceItem.piece(stack) == null) {
            return false;
        }
        piece = stack;
        progress = 0;
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

    @Override
    public void tick() {
        super.tick();
        if (level == null || piece.isEmpty()) {
            return;
        }
        float heat = heat();
        if (heat <= 0 || getSpeed() == 0) {
            return;
        }
        boolean wasCooked = isCooked();
        boolean wasBurnt = isBurnt();
        progress += heat * (1.0F + Math.min(Math.abs(getSpeed()), 64.0F) / 64.0F);
        if (level.isClientSide) {
            if (level.random.nextInt(isBurnt() ? 3 : 8) == 0) {
                level.addParticle(isBurnt() ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                        worldPosition.getX() + 0.3 + level.random.nextDouble() * 0.4, worldPosition.getY() + 0.8,
                        worldPosition.getZ() + 0.3 + level.random.nextDouble() * 0.4, 0, 0.03, 0);
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

    /** Take the piece off: raw it comes back as it went on, cooked it comes apart into its cooked yields. */
    public void takeOff(Player player) {
        if (piece.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        List<ItemStack> out = isCooked() ? cookedYields(serverLevel) : List.of(piece);
        for (ItemStack stack : out) {
            player.getInventory().placeItemBackInInventory(stack);
        }
        piece = ItemStack.EMPTY;
        progress = 0;
        notifyUpdate();
    }

    /** What the piece gives cooked: its butchery yields, each smelted where it can be; burnt, charcoal and bones. */
    public List<ItemStack> cookedYields(ServerLevel level) {
        CarcassPieceItem.Piece data = CarcassPieceItem.piece(piece);
        List<ItemStack> raw = new ArrayList<>();
        if (data == null) {
            return raw;
        }
        CarcassSavedData.Carcass stand = new CarcassSavedData.Carcass(UUID.randomUUID(), data.entity(), data.bone());
        stand.freshness = data.freshness();
        stand.baby = data.baby();
        stand.traits.putAll(data.traits());
        ButcheryManager.forEntity(data.entity()).ifPresent(table ->
                CarcassButchery.capturing(raw::add, () -> {
                    CarcassButchery.dropYields(level, stand, table.part(data.bone()), 1.0F, new Vector3d());
                    return true;
                }));
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
            out.add(new ItemStack(Items.CHARCOAL));
        }
        return out;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        if (piece.isEmpty()) {
            return true;
        }
        String state = isBurnt() ? "burnt" : isCooked() ? "cooked" : heat() <= 0 ? "no_heat" : "roasting";
        new LangBuilder(BloodAndBones.MOD_ID).translate("gui.goggles.spit_roast." + state, Math.min(100, Math.round(doneness() * 100)))
                .style(isBurnt() ? ChatFormatting.DARK_GRAY : isCooked() ? ChatFormatting.GOLD : ChatFormatting.GRAY).forGoggles(tooltip);
        return true;
    }

    @Override
    public void destroy() {
        super.destroy();
        if (level != null && !piece.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource(level, worldPosition, piece);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (!piece.isEmpty()) {
            tag.put("Piece", piece.save(registries));
        }
        tag.putFloat("Progress", progress);
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        piece = tag.contains("Piece") ? ItemStack.parseOptional(registries, tag.getCompound("Piece")) : ItemStack.EMPTY;
        progress = tag.getFloat("Progress");
        super.read(tag, registries, clientPacket);
    }
}
