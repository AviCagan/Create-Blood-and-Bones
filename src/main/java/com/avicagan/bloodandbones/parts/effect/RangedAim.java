package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.TraitContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Where Ranged's effects aim and whom they spare: a player's key aims along their look, a minion at its target; nothing
 * of theirs hurts their own side.
 */
public final class RangedAim {
    private RangedAim() {
    }

    /**
     * Whether a creature is on the host's side: the host itself, its team, a minion's maker and that maker's other
     * minions, a player's own minions. Nobody is on a missing host's side.
     */
    public static boolean friendly(@Nullable Entity host, Entity other) {
        if (host == null) {
            return false;
        }
        if (other == host || host.isAlliedTo(other)) {
            return true;
        }
        UUID side = host instanceof MinionEntity minion ? minion.makerId() : host instanceof Player ? host.getUUID() : null;
        if (side == null) {
            return false;
        }
        return other instanceof Player player && player.getUUID().equals(side)
                || other instanceof MinionEntity minion && side.equals(minion.makerId());
    }

    /** Whether a shot of the host's may hit this: a living thing, there to be hit, not on its side. */
    public static boolean hittable(LivingEntity host, Entity e) {
        return e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable() && !friendly(host, e);
    }

    /** From the host's eyes along its look to the first block in the way, or as far as it reaches. */
    public static Vec3 lookPoint(LivingEntity host, double range) {
        Vec3 from = host.getEyePosition();
        Vec3 to = from.add(host.getViewVector(1.0F).scale(range));
        BlockHitResult block = host.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, host));
        return block.getType() == HitResult.Type.MISS ? to : block.getLocation();
    }

    /** The first creature the host looks at within range (with a little give), none behind a wall. */
    @Nullable
    public static LivingEntity lookedAt(LivingEntity host, double range) {
        Vec3 from = host.getEyePosition();
        Vec3 end = lookPoint(host, range);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(host.level(), host, from, end, new AABB(from, end).inflate(1.0),
                e -> hittable(host, e), 0.5F);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    /**
     * The first creature on the line from a point to another (a hitscan's path), stopped by blocks: the line is swept
     * a little fat, so a shot that grazes a creature hits it.
     */
    @Nullable
    public static EntityHitResult firstInLine(LivingEntity host, Vec3 from, Vec3 to) {
        return ProjectileUtil.getEntityHitResult(host.level(), host, from, to, new AABB(from, to).inflate(1.0), e -> hittable(host, e), 0.3F);
    }

    /**
     * The item a vanilla effect runs as if enchanted on: the piece the trait counts from on a player (so an effect that
     * reads it sees carcass armour), nothing on a minion or from a full set.
     */
    public static EnchantedItemInUse itemInUse(TraitContext ctx) {
        EquipmentSlot slot = ctx.entry().slot();
        ItemStack stack = slot == null ? ItemStack.EMPTY : ctx.host().getItemBySlot(slot);
        return new EnchantedItemInUse(stack, slot, ctx.host(), item -> {
        });
    }
}
