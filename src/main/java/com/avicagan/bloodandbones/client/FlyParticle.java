package com.avicagan.bloodandbones.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A fly: darts about the spot it was born over, never straying far, then goes. */
public class FlyParticle extends TextureSheetParticle {
    private final double homeX;
    private final double homeY;
    private final double homeZ;

    protected FlyParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
        this.gravity = 0.0F;
        this.friction = 0.82F;
        this.hasPhysics = false;
        this.lifetime = 60 + this.random.nextInt(80);
        this.quadSize = 0.055F + this.random.nextFloat() * 0.02F;
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        // jittery darting, pulled back towards home
        this.xd += (this.random.nextDouble() - 0.5) * 0.09 + (homeX - this.x) * 0.03;
        this.yd += (this.random.nextDouble() - 0.5) * 0.06 + (homeY - this.y) * 0.03;
        this.zd += (this.random.nextDouble() - 0.5) * 0.09 + (homeZ - this.z) * 0.03;
        super.tick();
        if (this.age > this.lifetime - 10) {
            this.alpha = Math.max(0.0F, (this.lifetime - this.age) / 10.0F);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new FlyParticle(level, x, y, z, sprites);
        }
    }
}
