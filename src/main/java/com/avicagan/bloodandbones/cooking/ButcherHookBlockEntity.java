package com.avicagan.bloodandbones.cooking;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.Blood;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBParticles;
import com.avicagan.bloodandbones.registry.BBTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/**
 * What hangs on a butcher's hook: any body part (brief § Decoration: "accepts any carcass or body part as a rendered
 * attachment"), held just like a piece in a Specimen Jar. A fresh carcass piece still has blood in it, and drips it
 * on the floor below until it has run dry (about two minutes); a severed part, an organ or raw meat drips for a
 * while after it is hung.
 */
public class ButcherHookBlockEntity extends SpecimenJarBlockEntity {
    /**
     * What a hook takes: the tag {@code bloodandbones:hangs_on_hooks}, data like every other list in the mod, so a
     * datapack or another mod adds its own parts. Whole carcasses too heavy to carry are bodies, never items, and
     * do not go on (docs/BRIEF-AUDIT.md decision 9).
     */
    public static final TagKey<Item> HANGS = TagKey.create(Registries.ITEM, BloodAndBones.asResource("hangs_on_hooks"));
    /** Of what hangs, what drips for a while once hung: severed parts, organs, raw meat and offal. */
    public static final TagKey<Item> DRIPS = TagKey.create(Registries.ITEM, BloodAndBones.asResource("drips_on_hooks"));

    /** Seconds a freshly cut piece takes to drip dry. */
    public static final int DRIP_SECONDS = 120;
    /** Seconds anything else that drips drips for, once hung. */
    public static final int PART_DRIP_SECONDS = 60;

    /** Seconds left to drip, for a hung part that is not a carcass piece (a piece keeps its own blood). */
    private int dripLeft;

    public static boolean hangs(ItemStack stack) {
        return !stack.isEmpty() && stack.is(HANGS);
    }

    @Override
    protected boolean accepts(ItemStack stack) {
        return hangs(stack);
    }

    @Override
    public boolean put(ItemStack stack) {
        if (!specimen().isEmpty() || !accepts(stack)) {
            return false;
        }
        dripLeft = CarcassPieceItem.piece(stack) == null && stack.is(DRIPS) && bleeds(stack) ? PART_DRIP_SECONDS : 0;
        return super.put(stack);
    }

    @Override
    public ItemStack take() {
        dripLeft = 0;
        return super.take();
    }

    /**
     * The mob a hung thing came from, when it says: a piece, or anything stamped with where it came from (a part cut
     * out of a mob, scraps, a raw hide); null for anything else.
     */
    @org.jetbrains.annotations.Nullable
    private static net.minecraft.resources.ResourceLocation mob(ItemStack stack) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
        if (piece != null) {
            return piece.entity();
        }
        com.avicagan.bloodandbones.parts.Source source = stack.get(BBDataComponents.SOURCE.get());
        return source == null ? null : source.entity();
    }

    /** Whether what it came from has blood in it at all (a skeleton's parts never drip). */
    private static boolean bleeds(ItemStack stack) {
        net.minecraft.resources.ResourceLocation mob = mob(stack);
        return mob == null || BuiltInRegistries.ENTITY_TYPE.getOptional(mob).map(type -> !type.is(BBTags.BLOODLESS)).orElse(true);
    }

    /** Soul blood from a nether mob's parts, blood from anything else. */
    private boolean soul() {
        net.minecraft.resources.ResourceLocation mob = mob(specimen());
        return mob != null && Blood.soul(mob);
    }

    public ButcherHookBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Where the hung piece's lower end is: out from the wall under the hook's tip (see ButcherHookRenderer). */
    private Vector3d dripPoint() {
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        BlockPos pos = getBlockPos();
        return new Vector3d(pos.getX() + 0.5 + facing.getStepX() * 0.28, pos.getY() + 0.05, pos.getZ() + 0.5 + facing.getStepZ() * 0.28);
    }

    /** Whether what is on the hook still has blood to drip. */
    public boolean dripping() {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(specimen());
        if (piece == null) {
            return dripLeft > 0;
        }
        return piece.blood() > 0.0F && piece.bloodMax() > 0.0F && bleeds(specimen());
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || !dripping()) {
            return;
        }
        Vector3d at = dripPoint();
        if (level.isClientSide) {
            if (level.random.nextFloat() < 0.15F) {
                level.addParticle((soul() ? BBParticles.SOUL_BLOOD_DROP : BBParticles.BLOOD_DROP).get(), at.x + (level.random.nextDouble() - 0.5) * 0.15, at.y,
                        at.z + (level.random.nextDouble() - 0.5) * 0.15, 0.0, -0.05, 0.0);
            }
            return;
        }
        long time = level.getGameTime();
        if (time % 20 != 0) {
            return;
        }
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(specimen());
        if (piece == null) {
            dripLeft--;
            if (time % 60 == 0) {
                Blood.stain((ServerLevel) level, new Vector3d(at.x, at.y - 0.5, at.z), 1, soul());
            }
            setChanged();
            if (dripLeft <= 0) {
                sendData();
            }
            return;
        }
        float left = Math.max(0.0F, piece.blood() - piece.bloodMax() / DRIP_SECONDS);
        specimen().set(BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(piece.entity(), piece.bone(), piece.texture(), piece.coats(),
                piece.freshness(), piece.skinned(), piece.traits(), left, piece.bloodMax(), piece.decay(), piece.baby()));
        if (time % 60 == 0) {
            // from under the hook's own block, which the drops fall out of
            Blood.stain((ServerLevel) level, new Vector3d(at.x, at.y - 0.5, at.z), 1, soul());
        }
        setChanged();
        if (left <= 0.0F) {
            // clients only need to know when it stops: until then they drip on what they were sent
            sendData();
        }
    }

    @Override
    protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        if (dripLeft > 0) {
            tag.putInt("Drip", dripLeft);
        }
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        dripLeft = tag.getInt("Drip");
        super.read(tag, registries, clientPacket);
    }
}
