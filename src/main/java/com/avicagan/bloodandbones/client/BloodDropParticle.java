package com.avicagan.bloodandbones.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A drop of blood. It falls, lands, spreads a little into a splat and lies there before fading.
 */
public class BloodDropParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private int restTicks;

    protected BloodDropParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0.9F;
        this.friction = 0.96F;
        this.lifetime = 60 + this.random.nextInt(40);
        this.quadSize = 0.045F + this.random.nextFloat() * 0.04F;
        this.hasPhysics = true;
        float shade = 0.75F + this.random.nextFloat() * 0.25F;
        setColor(shade, shade, shade);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.onGround) {
            // landed: spread into a splat and stay a while
            this.xd = 0.0;
            this.zd = 0.0;
            if (restTicks == 0) {
                this.quadSize *= 1.8F;
            }
            restTicks++;
            if (restTicks > 40) {
                this.alpha = Math.max(0.0F, this.alpha - 0.04F);
                if (this.alpha <= 0.0F) {
                    remove();
                }
            }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new BloodDropParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
