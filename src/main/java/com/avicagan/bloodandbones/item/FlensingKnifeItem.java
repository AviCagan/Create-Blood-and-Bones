package com.avicagan.bloodandbones.item;

import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Takes the hide off a carcass: hold it on a part (the brief: "hold on a part to take it off"), and it works the hide
 * loose in long strokes, one every {@link #STROKE_TICKS} ticks, sawing back and forth as a brush does, until the hide
 * comes away or you look off the carcass or let go. By hand it gets about half of what a Deglover would, with real loss
 * (CarcassButchery's hand path). A quick, light blade in a fight.
 */
public class FlensingKnifeItem extends SwordItem {
    /** Ticks of holding per stroke. */
    public static final int STROKE_TICKS = 10;
    /** As long as anyone would hold it; it lets go when the hide is off. */
    private static final int USE_DURATION = 72000;

    public FlensingKnifeItem(Properties properties) {
        super(Tiers.IRON, properties.attributes(SwordItem.createAttributes(Tiers.IRON, 1, -1.8F)));
    }

    /** Striking a living thing that bleeds leaves the blade bloody for a while. */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (com.avicagan.bloodandbones.carcass.Blood.bleeds(target)) {
            com.avicagan.bloodandbones.carcass.Blood.bloody(stack, target.level());
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    /**
     * Drawing blood only changes the blade's components; the held blade should not dip out of view and
     * back each time it does. Another item, or another slot, still swaps as usual.
     */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    /** Sawing back and forth: the brush's motion. */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BRUSH;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    /**
     * Each tick it is held: every {@link #STROKE_TICKS} a stroke on the carcass under the crosshair (BrushItem's way of
     * finding it). It lets go when the look leaves the carcass or the hide is off.
     */
    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingUseDuration) {
        if (!(living instanceof Player player)) {
            living.releaseUsingItem();
            return;
        }
        CarcassPartBlockEntity part = partLookedAt(level, player);
        if (part == null) {
            living.releaseUsingItem();
            return;
        }
        int held = getUseDuration(stack, living) - remainingUseDuration + 1;
        if (held % STROKE_TICKS != 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        CarcassSavedData.Carcass carcass = CarcassSavedData.get(server).carcass(part.carcassId());
        if (carcass == null || !CarcassButchery.skin(server, player, carcass, hitWorld(level, player)) || carcass.skinned) {
            living.releaseUsingItem();
        }
    }

    /** The carcass part the player is looking at, within reach. */
    @Nullable
    public static CarcassPartBlockEntity partLookedAt(Level level, Player player) {
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player, entity -> !entity.isSpectator() && entity.isPickable(), player.blockInteractionRange());
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && level.getBlockEntity(block.getBlockPos()) instanceof CarcassPartBlockEntity be && be.carcassId() != null) {
            return be;
        }
        return null;
    }

    /** Where in the world the look meets the carcass (the hit is in its body's own space). */
    @Nullable
    private static org.joml.Vector3d hitWorld(Level level, Player player) {
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player, entity -> false, player.blockInteractionRange());
        if (!(hit instanceof BlockHitResult block)) {
            return null;
        }
        dev.ryanhcode.sable.sublevel.SubLevel body = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, block.getBlockPos());
        net.minecraft.world.phys.Vec3 at = hit.getLocation();
        return body == null ? new org.joml.Vector3d(at.x, at.y, at.z)
                : body.logicalPose().transformPosition(new org.joml.Vector3d(at.x, at.y, at.z), new org.joml.Vector3d());
    }
}
