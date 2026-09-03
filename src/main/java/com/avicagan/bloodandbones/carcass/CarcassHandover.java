package com.avicagan.bloodandbones.carcass;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The moment of death. The carcass exists on the server the instant the mob dies, but the client only
 * learns about new bodies a few ticks later; discarding the mob at once left a gap where nothing was
 * drawn. So the dying mob is kept for a few ticks, frozen and untouchable, standing exactly where the
 * carcass was built at rest; then it goes and the carcass gets its kill shove in the same tick.
 */
public final class CarcassHandover {
    /** Ticks the dead mob stays visible while the client catches up. */
    public static final int TICKS = 4;

    private record Pending(LivingEntity entity, UUID carcassId, Vec3 look, int[] ticksLeft, Map<UUID, Pose3d> poses) {
    }

    private static final Map<ServerLevel, List<Pending>> PENDING = new WeakHashMap<>();

    private CarcassHandover() {
    }

    /** Freeze the mob in place and schedule its removal. */
    public static void begin(ServerLevel level, LivingEntity entity, CarcassSavedData.Carcass carcass, Vec3 killerLook) {
        entity.setHealth(Math.max(1.0F, entity.getHealth()));
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtTime = 0;
        // the carcass was built facing the body; keep the head from turning the body during the wait
        entity.setYHeadRot(entity.yBodyRot);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        Map<UUID, Pose3d> poses = new HashMap<>();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            for (UUID id : carcass.bones.values()) {
                if (container.getSubLevel(id) instanceof ServerSubLevel bone && !bone.isRemoved()) {
                    Pose3d copy = new Pose3d();
                    copy.set(bone.logicalPose());
                    poses.put(id, copy);
                }
            }
        }
        PENDING.computeIfAbsent(level, l -> new ArrayList<>()).add(new Pending(entity, carcass.id, killerLook, new int[]{TICKS}, poses));
    }

    /** Hold every body exactly where it was built, so the carcass appears in the mob's own pose. */
    private static void hold(ServerLevel level, Pending pending) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        pending.poses().forEach((id, pose) -> {
            if (container.getSubLevel(id) instanceof ServerSubLevel bone && !bone.isRemoved()) {
                pipeline.teleport(bone, pose.position(), pose.orientation());
                pipeline.resetVelocity(bone);
                bone.updateLastPose();
            }
        });
    }

    /** True while the mob is waiting to go: nothing else should touch it. */
    public static boolean isHandingOver(LivingEntity entity) {
        for (List<Pending> list : PENDING.values()) {
            for (Pending pending : list) {
                if (pending.entity() == entity) {
                    return true;
                }
            }
        }
        return false;
    }

    /** End of level tick: hold the mob still, and when its time is up swap it for the moving carcass. */
    public static void tick(ServerLevel level) {
        List<Pending> list = PENDING.get(level);
        if (list == null || list.isEmpty()) {
            return;
        }
        CarcassSavedData data = CarcassSavedData.get(level);
        Iterator<Pending> iterator = list.iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next();
            LivingEntity entity = pending.entity();
            if (entity.isRemoved()) {
                iterator.remove();
                continue;
            }
            entity.setDeltaMovement(Vec3.ZERO);
            hold(level, pending);
            if (--pending.ticksLeft()[0] > 0) {
                continue;
            }
            iterator.remove();
            entity.discard();
            CarcassSavedData.Carcass carcass = data.carcass(pending.carcassId());
            if (carcass != null) {
                // the cells learn what to draw only now, in the same tick the mob goes
                CarcassAssembler.configureCells(level, carcass);
                CarcassAssembler.shove(level, carcass, pending.look());
            }
        }
    }

    public static void clear() {
        PENDING.clear();
    }
}
