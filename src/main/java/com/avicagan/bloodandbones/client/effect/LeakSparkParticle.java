package com.avicagan.bloodandbones.client.effect;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A grey spark: what leaks out of something with no blood in it, and what a Bleeding drop is in bloodless mode. It
 * spits out, glints, falls and winks out, drawn with vanilla's firework spark frames.
 */
public class LeakSparkParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    LeakSparkParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = dx + (this.random.nextDouble() - 0.5) * 0.08;
        this.yd = dy + this.random.nextDouble() * 0.06;
        this.zd = dz + (this.random.nextDouble() - 0.5) * 0.08;
        this.gravity = 0.7F;
        this.friction = 0.9F;
        this.lifetime = 12 + this.random.nextInt(10);
        this.quadSize = 0.06F + this.random.nextFloat() * 0.04F;
        this.hasPhysics = true;
        float grey = 0.5F + this.random.nextFloat() * 0.25F;
        setColor(grey, grey, grey * 1.05F);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
    }

    /** A glint: it lights itself. */
    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            // a mob effect's swirl asks with a speed of 1 each way: that is its colour, not a speed
            boolean swirl = dx == 1.0 && dy == 1.0 && dz == 1.0;
            return new LeakSparkParticle(level, x, y, z, swirl ? 0.0 : dx, swirl ? 0.02 : dy, swirl ? 0.0 : dz, sprites);
        }
    }
}
