package com.avicagan.bloodandbones.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A scrap of meat thrown off a carcass: it arcs, lands with a wet bounce, lies there a while and goes. */
public class GibParticle extends TextureSheetParticle {
    private boolean bounced;

    protected GibParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 1.1F;
        this.friction = 0.97F;
        this.hasPhysics = true;
        this.lifetime = 80 + this.random.nextInt(60);
        this.quadSize = 0.07F + this.random.nextFloat() * 0.06F;
        this.roll = this.random.nextFloat() * 6.28F;
        this.oRoll = this.roll;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.onGround) {
            // tumbling through the air
            this.oRoll = this.roll;
            this.roll += 0.3F;
        } else {
            if (!bounced) {
                bounced = true;
                this.yd = 0.08;
            }
            this.xd *= 0.5;
            this.zd *= 0.5;
            this.oRoll = this.roll;
        }
        if (this.age > this.lifetime - 15) {
            this.alpha = Math.max(0.0F, (this.lifetime - this.age) / 15.0F);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            if (com.avicagan.bloodandbones.config.BBClientConfig.bloodless()) {
                return null;
            }
            return new GibParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
