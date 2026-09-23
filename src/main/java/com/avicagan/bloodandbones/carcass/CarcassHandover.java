package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.BloodAndBones;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import org.joml.Vector3d;

import java.lang.reflect.Method;
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
    public static final int TICKS = 3;

    private record Pending(LivingEntity entity, UUID carcassId, Vec3 look, int[] ticksLeft, Map<UUID, Pose3d> poses,
                           DamageSource source) {
    }

    private static final Method DROP_EQUIPMENT = ObfuscationReflectionHelper.findMethod(LivingEntity.class, "dropEquipment");
    private static final Method DROP_CUSTOM_LOOT = ObfuscationReflectionHelper.findMethod(LivingEntity.class,
            "dropCustomDeathLoot", ServerLevel.class, DamageSource.class, boolean.class);
    private static final Method SHOULD_DROP_LOOT = ObfuscationReflectionHelper.findMethod(LivingEntity.class, "shouldDropLoot");

    private static final Map<ServerLevel, List<Pending>> PENDING = new WeakHashMap<>();

    private CarcassHandover() {
    }

    /** Freeze the mob in place and schedule its removal. */
    public static void begin(ServerLevel level, LivingEntity entity, CarcassSavedData.Carcass carcass, Vec3 killerLook,
                             DamageSource source) {
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
        PENDING.computeIfAbsent(level, l -> new ArrayList<>()).add(new Pending(entity, carcass.id, killerLook, new int[]{TICKS}, poses, source));
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
            com.avicagan.bloodandbones.event.CarcassEvents.handedOver(entity.getUUID());
            dropBelongings(level, entity, pending.source());
            entity.discard();
            CarcassSavedData.Carcass carcass = data.carcass(pending.carcassId());
            if (carcass != null) {
                // the cells learn what to draw only now, in the same tick the mob goes
                CarcassAssembler.configureCells(level, carcass);
                CarcassAssembler.shove(level, carcass, pending.look());
                Vector3d wound = CarcassAssembler.boneWorldPosition(level, carcass, carcass.hitBone);
                if (wound != null && Blood.bloody(carcass)) {
                    Blood.spray(level, wound, new Vector3d(pending.look().x, pending.look().y, pending.look().z), 24, Blood.soul(carcass));
                    // the spray lands a little way along the blow
                    Blood.stain(level, new Vector3d(wound).add(pending.look().x * 0.8, 0, pending.look().z * 0.8), 2, Blood.soul(carcass));
                }
            }
        }
    }

    /**
     * The body keeps the meat, but not what the mob was carrying: saddles, horse armour, chests and their
     * contents, and anything it held or wore. The normal death that would have dropped these is cancelled,
     * so the same vanilla drop calls are made here, under the same rules as LivingEntity#dropAllDeathLoot
     * (worn and held gear only with doMobLoot, never from a baby) and through the same LivingDropsEvent, so
     * other mods can see or change them. They are protected, hence the reflection.
     */
    private static void dropBelongings(ServerLevel level, LivingEntity entity, DamageSource source) {
        boolean byPlayer = source.getEntity() instanceof net.minecraft.world.entity.player.Player;
        entity.captureDrops(new java.util.ArrayList<>());
        try {
            if ((boolean) SHOULD_DROP_LOOT.invoke(entity) && level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT)) {
                DROP_CUSTOM_LOOT.invoke(entity, level, source, byPlayer);
            }
            DROP_EQUIPMENT.invoke(entity);
        } catch (ReflectiveOperationException | RuntimeException e) {
            BloodAndBones.LOGGER.warn("Could not drop the belongings of {}", entity, e);
        }
        java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops = entity.captureDrops(null);
        if (drops != null && !drops.isEmpty() && !net.neoforged.neoforge.common.CommonHooks.onLivingDrops(entity, source, drops, byPlayer)) {
            drops.forEach(level::addFreshEntity);
        }
    }

    public static void clear() {
        PENDING.clear();
    }
}
