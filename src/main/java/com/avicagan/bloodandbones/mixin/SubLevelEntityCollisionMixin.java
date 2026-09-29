package com.avicagan.bloodandbones.mixin;

import com.avicagan.bloodandbones.carcass.CarcassDrag;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * What someone drags never pushes them. Sable moves an entity out of every body it walks into and carries it along with
 * that body as it moves; a carcass being dragged is left out of that for its own dragger (CarcassDrag#isDraggedBy), who
 * walks through it. Everyone else still bumps into it, and it still lies on the ground and on other bodies as before.
 */
@Mixin(value = SubLevelEntityCollision.class, remap = false)
public class SubLevelEntityCollisionMixin {
    @Redirect(method = "collide", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/ActiveSableCompanion;getAllIntersecting(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;)Ljava/lang/Iterable;"))
    private static Iterable<SubLevel> bloodandbones$notWhatTheyDrag(ActiveSableCompanion helper, Level level, BoundingBox3dc bounds, Entity entity, Vec3 motion,
                                                                  Vec3 velocity, LevelReusedVectors sink) {
        return CarcassDrag.withoutWhatTheyDrag(entity, helper.getAllIntersecting(level, bounds));
    }
}
