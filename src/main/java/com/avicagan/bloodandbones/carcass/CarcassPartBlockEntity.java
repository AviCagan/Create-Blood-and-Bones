package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Per-cell data of a carcass limb. The root cell (the one at the limb's box minimum corner) also carries
 * everything the client needs to draw the limb: which model part, which texture, where the box sits.
 */
public class CarcassPartBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    @Nullable
    private UUID carcassId;
    private String bone = "";
    private boolean root;

    private ResourceLocation model = ResourceLocation.withDefaultNamespace("cow");
    private String layer = "main";
    private String partPath = "";
    private ResourceLocation texture = ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png");
    private final Vector3f boxMin = new Vector3f();
    private final Vector3f boxSize = new Vector3f(16, 16, 16);

    public CarcassPartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void configureRoot(UUID carcassId, Rig rig, Bone bone) {
        this.carcassId = carcassId;
        this.bone = bone.name();
        this.root = true;
        this.model = rig.model();
        this.layer = rig.layer();
        this.partPath = bone.part();
        this.texture = rig.texture();
        this.boxMin.set(bone.boxMin());
        this.boxSize.set(bone.boxSize());
        setChanged();
    }

    public void configureFiller(UUID carcassId, Bone bone) {
        this.carcassId = carcassId;
        this.bone = bone.name();
        this.root = false;
        setChanged();
    }

    @Nullable
    public UUID carcassId() {
        return carcassId;
    }

    public String bone() {
        return bone;
    }

    public boolean isRoot() {
        return root;
    }

    public ResourceLocation model() {
        return model;
    }

    public String layer() {
        return layer;
    }

    public String partPath() {
        return partPath;
    }

    public ResourceLocation texture() {
        return texture;
    }

    public Vector3f boxMin() {
        return boxMin;
    }

    public Vector3f boxSize() {
        return boxSize;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (carcassId != null) {
            tag.putUUID("Carcass", carcassId);
        }
        tag.putString("Bone", bone);
        tag.putBoolean("Root", root);
        if (root) {
            tag.putString("Model", model.toString());
            tag.putString("Layer", layer);
            tag.putString("Part", partPath);
            tag.putString("Texture", texture.toString());
            putVec(tag, "BoxMin", boxMin);
            putVec(tag, "BoxSize", boxSize);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        carcassId = tag.hasUUID("Carcass") ? tag.getUUID("Carcass") : null;
        bone = tag.getString("Bone");
        root = tag.getBoolean("Root");
        if (root) {
            model = ResourceLocation.parse(tag.getString("Model"));
            layer = tag.getString("Layer");
            partPath = tag.getString("Part");
            texture = ResourceLocation.parse(tag.getString("Texture"));
            getVec(tag, "BoxMin", boxMin);
            getVec(tag, "BoxSize", boxSize);
        }
    }

    private static void putVec(CompoundTag tag, String key, Vector3f v) {
        tag.putFloat(key + "X", v.x);
        tag.putFloat(key + "Y", v.y);
        tag.putFloat(key + "Z", v.z);
    }

    private static void getVec(CompoundTag tag, String key, Vector3f dest) {
        dest.set(tag.getFloat(key + "X"), tag.getFloat(key + "Y"), tag.getFloat(key + "Z"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        if (root && carcassId != null && level instanceof ServerLevel serverLevel) {
            CarcassSavedData.get(serverLevel).tickRoot(carcassId, subLevel);
        }
    }

    @Nullable
    @Override
    public Iterable<SubLevel> sable$getConnectionDependencies() {
        if (carcassId == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return CarcassSavedData.get(serverLevel).siblings(carcassId, bone);
    }
}
