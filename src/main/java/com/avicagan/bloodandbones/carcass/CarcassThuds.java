package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;

/**
 * The sound of meat hitting the ground. A limb that was falling and suddenly is not has landed: it lands
 * with a wet thud, louder and deeper the faster and heavier it came down, and a hard landing splats blood.
 */
public final class CarcassThuds {
    /** Falling at least this fast (blocks a second) before the stop counts as a landing. */
    public static final double FALLING = 3.0;
    /** Landing at least this hard splats blood. */
    public static final double SPLAT = 6.0;
    /** Ticks between thuds of one carcass, so a body bouncing to rest does not rattle. */
    private static final int QUIET_TICKS = 8;

    private CarcassThuds() {
    }

    /** Once a tick for a moving carcass (not a resting one), from its torso. */
    public static void tick(ServerLevel level, CarcassSavedData.Carcass carcass) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        if (carcass.quietTicks > 0) {
            carcass.quietTicks--;
        }
        SubLevelPhysicsSystem physics = container.physicsSystem();
        Rig rig = RigManager.forCarcass(carcass).orElse(null);
        Vector3d velocity = new Vector3d();
        for (Map.Entry<String, UUID> entry : carcass.bones.entrySet()) {
            if (!(container.getSubLevel(entry.getValue()) instanceof ServerSubLevel body) || body.isRemoved()) {
                continue;
            }
            physics.getPhysicsHandle(body).getLinearVelocity(velocity);
            Double before = carcass.fallSpeeds.put(entry.getKey(), velocity.y);
            if (before == null || before > -FALLING || carcass.quietTicks > 0) {
                continue;
            }
            // it was coming down fast and has all but stopped: it hit something
            double impact = velocity.y - before;
            if (impact < FALLING) {
                continue;
            }
            Bone bone = rig == null ? null : rig.bone(entry.getKey()).orElse(null);
            double mass = bone == null ? 0.2 : bone.boxSize().x * bone.boxSize().y * bone.boxSize().z / 4096.0;
            Vector3d at = body.logicalPose().position();
            float volume = (float) Mth.clamp(impact / 8.0 * (0.5 + Math.sqrt(mass)), 0.25, 1.2);
            float pitch = (float) Mth.clamp(1.25 - Math.sqrt(mass) * 0.6, 0.55, 1.25);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_FALL, SoundSource.NEUTRAL, volume, pitch);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_FALL, SoundSource.NEUTRAL, volume * 0.6F, pitch * 0.8F);
            if (impact >= SPLAT && Blood.bloody(carcass)) {
                Blood.burst(level, new Vector3d(at), 6);
                Blood.stain(level, new Vector3d(at), 1);
            }
            carcass.thuds++;
            carcass.quietTicks = QUIET_TICKS;
        }
    }
}
