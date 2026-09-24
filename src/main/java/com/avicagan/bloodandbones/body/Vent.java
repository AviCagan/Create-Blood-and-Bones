package com.avicagan.bloodandbones.body;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Vent Arm: hold use with the empty hand and it sprays what is in the backtank ahead of you, a shot at a
 * time. What the spray does comes from the fluid, through a data map (`data_maps/fluid/vent_effects.json`,
 * keyed by fluid or fluid tag), so other mods' fluids can join: lava and fuels burn, water puts fires out,
 * liquid experience gives experience, milk clears effects, and anything else just spills (blood as blood).
 */
public final class Vent {
    /** mB a shot. */
    public static final int SHOT = 50;
    /** How far the spray reaches, and how wide its cone is (cosine of the half angle). */
    public static final double RANGE = 8.0;
    private static final double CONE = Math.cos(Math.toRadians(20.0));
    /** Millibuckets of liquid experience to a point, NeoForge's common rate. */
    public static final int XP_MB = 20;
    /** Game ticks between shots. */
    private static final int GAP = 3;
    private static final Map<Player, Long> LAST = new WeakHashMap<>();

    public enum Effect implements StringRepresentable {
        FIRE, SPLASH, EXPERIENCE, CLEANSE, SPILL;

        public static final Codec<Effect> CODEC = StringRepresentable.fromEnum(Effect::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record VentEffect(Effect effect) {
        public static final Codec<VentEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
                Effect.CODEC.fieldOf("effect").forGetter(VentEffect::effect)).apply(i, VentEffect::new));
    }

    public static final DataMapType<Fluid, VentEffect> EFFECTS = DataMapType.builder(BloodAndBones.asResource("vent_effects"), Registries.FLUID, VentEffect.CODEC).build();

    private Vent() {
    }

    public static Effect effectOf(Fluid fluid) {
        @Nullable VentEffect effect = fluid.builtInRegistryHolder().getData(EFFECTS);
        return effect == null ? Effect.SPILL : effect.effect();
    }

    /** Whether the main arm is a working Vent Arm with nothing in the hand. */
    public static boolean ready(Player player) {
        Body body = BodyEffects.body(player);
        BodyPart arm = BodyEffects.armFor(player, InteractionHand.MAIN_HAND);
        return !player.isSpectator() && player.isAlive() && player.getMainHandItem().isEmpty() && body.works(arm, player) && body.state(arm) == Body.State.IMPLANT
                && ((ImplantItem) body.implant(arm).getItem()).spec().ability() == ImplantSpec.Ability.VENT;
    }

    /**
     * One shot.
     *
     * @return what it did, or null for nothing (no Vent Arm, nothing in the tank, too soon)
     */
    @Nullable
    public static Effect spray(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !ready(player)) {
            return null;
        }
        Long last = LAST.get(player);
        if (last != null && level.getGameTime() - last < GAP) {
            return null;
        }
        FluidStack fluid = FluidBacktankItem.fluid(FluidBacktankItem.wornBy(player));
        if (fluid.isEmpty()) {
            return null;
        }
        Fluid kind = fluid.getFluid();
        int taken = BodyEffects.take(player, SHOT);
        if (taken <= 0) {
            return null;
        }
        LAST.put(player, level.getGameTime());
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        // other players only where the server allows fighting; never spectators
        boolean pvp = level.getServer().isPvpAllowed();
        List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.5),
                e -> e != player && e.isAlive() && !e.isSpectator() && (pvp || !(e instanceof Player))
                        && inCone(eye, look, e.getBoundingBox().getCenter()));
        Effect effect = effectOf(kind);
        switch (effect) {
            case FIRE -> {
                hit.forEach(e -> e.igniteForSeconds(5.0F));
                particles(level, eye, look, ParticleTypes.FLAME);
                level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.2F);
            }
            case SPLASH -> {
                hit.forEach(LivingEntity::clearFire);
                for (double d = 1.0; d <= RANGE; d += 0.5) {
                    BlockPos at = BlockPos.containing(eye.add(look.scale(d)));
                    if (level.getBlockState(at).getBlock() instanceof BaseFireBlock && level.mayInteract(player, at)) {
                        level.removeBlock(at, false);
                    }
                }
                particles(level, eye, look, ParticleTypes.SPLASH);
                level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.6F, 1.4F);
            }
            case EXPERIENCE -> {
                // the common rate for liquid experience (NeoForge's c:experience tag): 20 mB a point
                Vec3 at = eye.add(look.scale(2.0));
                int points = taken / XP_MB + (level.random.nextInt(XP_MB) < taken % XP_MB ? 1 : 0);
                if (points > 0) {
                    ExperienceOrb.award(level, at, points);
                }
                particles(level, eye, look, ParticleTypes.HAPPY_VILLAGER);
            }
            case CLEANSE -> {
                hit.forEach(LivingEntity::removeAllEffects);
                particles(level, eye, look, ParticleTypes.EFFECT);
            }
            case SPILL -> {
                boolean blood = kind.isSame(com.avicagan.bloodandbones.registry.BBFluids.blood());
                boolean soul = kind.isSame(com.avicagan.bloodandbones.registry.BBFluids.soulBlood());
                if (blood || soul) {
                    Vector3d from = new Vector3d(eye.x, eye.y - 0.2, eye.z);
                    com.avicagan.bloodandbones.carcass.Blood.spray(level, from, new Vector3d(look.x, look.y, look.z), 10, soul);
                    Vec3 land = eye.add(look.scale(3.0));
                    com.avicagan.bloodandbones.carcass.Blood.stain(level, new Vector3d(land.x, land.y, land.z), 2, soul);
                } else {
                    particles(level, eye, look, new BlockParticleOption(ParticleTypes.BLOCK, kind.defaultFluidState().createLegacyBlock()));
                }
                level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.5F, 1.3F);
            }
        }
        return effect;
    }

    private static boolean inCone(Vec3 eye, Vec3 look, Vec3 point) {
        Vec3 to = point.subtract(eye);
        double distance = to.length();
        return distance <= RANGE && distance > 1.0E-3 && to.normalize().dot(look) >= CONE;
    }

    private static void particles(ServerLevel level, Vec3 eye, Vec3 look, ParticleOptions particle) {
        for (double d = 1.0; d <= RANGE; d += 0.75) {
            Vec3 at = eye.add(look.scale(d)).add(0, -0.2, 0);
            level.sendParticles(particle, at.x, at.y, at.z, 3, d * 0.08, d * 0.08, d * 0.08, 0.02);
        }
    }

    /** The client: the use key is held, empty-handed, with a Vent Arm. */
    public record Payload() implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(BloodAndBones.asResource("vent"));
        public static final StreamCodec<ByteBuf, Payload> STREAM_CODEC = StreamCodec.unit(new Payload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
