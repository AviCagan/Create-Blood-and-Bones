package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * projectile (docs/PARTS-AND-TRAITS.md section 5.4, row 19): vanilla's own projectiles, fired by the host. A player's key
 * fires them along their look; a minion fires them at its target, from an organ (an activate entry with a range) or as
 * its ranged attack (a passive entry: its arms or organ let it keep its distance and shoot, see {@link MinionRangedGoal}).
 * On a hurt or attack entry they fly at whoever else is in it.
 * <p>
 * Kinds (section 5.6): arrow (tipped with {@code potion}), snowball, egg, small_fireball, large_fireball (its blast
 * {@code power}), wither_skull, llama_spit, shulker_bullet (homes on the target, or on what a player looks at), wind_charge,
 * trident (throws what the host holds: nothing without one) and splash_potion (one from the host's inventory, else
 * {@code potion} if set). {@code damage} is an arrow's base damage, and what any other kind does to what it hits in place
 * of its own. A minion's blasts and fires never break or light blocks.
 *
 * @param count  how many at once
 * @param spread how far they stray (vanilla's inaccuracy)
 * @param speed  blocks a tick they leave at (each kind has its own if not given)
 */
public record ProjectileEffect(Kind kind, int count, float spread, Optional<Float> speed, Optional<LevelBasedValue> damage,
                               Optional<Holder<Potion>> potion, LevelBasedValue power) implements TraitEffect.Effect {
    public static final MapCodec<ProjectileEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Kind.CODEC.fieldOf("kind").forGetter(ProjectileEffect::kind),
            Codec.intRange(1, 16).optionalFieldOf("count", 1).forGetter(ProjectileEffect::count),
            Codec.FLOAT.optionalFieldOf("spread", 1.0F).forGetter(ProjectileEffect::spread),
            Codec.FLOAT.optionalFieldOf("speed").forGetter(ProjectileEffect::speed),
            LevelBasedValue.CODEC.optionalFieldOf("damage").forGetter(ProjectileEffect::damage),
            Potion.CODEC.optionalFieldOf("potion").forGetter(ProjectileEffect::potion),
            LevelBasedValue.CODEC.optionalFieldOf("power", LevelBasedValue.constant(1.0F)).forGetter(ProjectileEffect::power)
    ).apply(i, ProjectileEffect::new));

    /** The projectiles it can fire: how fast each leaves by default, whether it falls (and so is aimed a little high), its sound. */
    public enum Kind implements StringRepresentable {
        ARROW(1.6F, true, SoundEvents.SKELETON_SHOOT),
        SNOWBALL(1.5F, true, SoundEvents.SNOW_GOLEM_SHOOT),
        EGG(1.5F, true, SoundEvents.EGG_THROW),
        SMALL_FIREBALL(1.2F, false, SoundEvents.BLAZE_SHOOT),
        LARGE_FIREBALL(1.0F, false, SoundEvents.GHAST_SHOOT),
        WITHER_SKULL(1.0F, false, SoundEvents.WITHER_SHOOT),
        LLAMA_SPIT(1.5F, true, SoundEvents.LLAMA_SPIT),
        SHULKER_BULLET(0.0F, false, SoundEvents.SHULKER_SHOOT),
        WIND_CHARGE(1.5F, false, SoundEvents.BREEZE_SHOOT),
        TRIDENT(1.6F, true, SoundEvents.DROWNED_SHOOT),
        SPLASH_POTION(0.75F, true, SoundEvents.WITCH_THROW);

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        final float speed;
        final boolean falls;
        final SoundEvent sound;

        Kind(float speed, boolean falls, SoundEvent sound) {
            this.speed = speed;
            this.falls = falls;
            this.sound = sound;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    @Override
    public void run(TraitContext ctx) {
        LivingEntity host = ctx.host();
        ServerLevel level = ctx.level();
        LivingEntity target = ctx.other() != null && ctx.other() != host && ctx.other().isAlive() ? ctx.other() : null;
        if (target == null && kind == Kind.SHULKER_BULLET) {
            // a bolt needs something to home on: what the player looks at
            target = RangedAim.lookedAt(host, Math.max(16.0F, ctx.facet().range()));
        }
        ItemStack ammo = switch (kind) {
            case TRIDENT -> host.getMainHandItem().getItem() instanceof TridentItem || host.getOffhandItem().getItem() instanceof TridentItem
                    ? new ItemStack(Items.TRIDENT) : ItemStack.EMPTY;
            case SPLASH_POTION -> potionFrom(host);
            default -> ItemStack.EMPTY;
        };
        if ((kind == Kind.TRIDENT || kind == Kind.SPLASH_POTION) && ammo.isEmpty()) {
            return;
        }
        int shots = kind == Kind.TRIDENT || kind == Kind.SPLASH_POTION ? 1 : count;
        Vec3 from = new Vec3(host.getX(), host.getEyeY() - 0.1, host.getZ());
        float velocity = speed.orElse(kind.speed);
        for (int n = 0; n < shots; n++) {
            Vec3 dir = aim(host, target, from);
            Projectile shot = make(ctx, level, host, target, dir, ammo);
            if (shot == null) {
                continue;
            }
            if (kind != Kind.SHULKER_BULLET) {
                shot.setPos(from.x, from.y, from.z);
                shot.shoot(dir.x, dir.y, dir.z, velocity, spread);
            }
            RangedEffects.markShot(shot, kind == Kind.ARROW ? null : damage.map(ctx::scaled).orElse(null));
            level.addFreshEntity(shot);
        }
        float pitch = 1.0F / (host.getRandom().nextFloat() * 0.4F + 0.8F);
        level.playSound(null, host.getX(), host.getY(), host.getZ(), kind.sound, host.getSoundSource(), 1.0F, pitch);
        // the heave of whatever gland or gullet it came out of; a brass minion's hisses
        boolean brass = host instanceof MinionEntity minion && minion.cybernetic();
        level.playSound(null, host.getX(), host.getY(), host.getZ(), brass ? SoundEvents.PISTON_CONTRACT : SoundEvents.SLIME_SQUISH_SMALL, host.getSoundSource(),
                0.5F, brass ? 1.5F : 0.5F + host.getRandom().nextFloat() * 0.2F);
    }

    /** At the target (a little high for what falls, as a skeleton aims), or along the host's look. */
    private Vec3 aim(LivingEntity host, @Nullable LivingEntity target, Vec3 from) {
        if (target == null) {
            return host.getViewVector(1.0F);
        }
        double dx = target.getX() - from.x;
        double dz = target.getZ() - from.z;
        double dy = (kind.falls ? target.getY(0.3333) : target.getY(0.5)) - from.y;
        double across = Math.sqrt(dx * dx + dz * dz);
        return new Vec3(dx, kind.falls ? dy + across * 0.2 : dy, dz);
    }

    @Nullable
    private Projectile make(TraitContext ctx, ServerLevel level, LivingEntity host, @Nullable LivingEntity target, Vec3 dir, ItemStack ammo) {
        return switch (kind) {
            case ARROW -> {
                ItemStack arrows = potion.map(p -> PotionContents.createItemStack(Items.TIPPED_ARROW, p)).orElseGet(() -> new ItemStack(Items.ARROW));
                Arrow arrow = new Arrow(level, host, arrows, null);
                // an innate shot: nothing to pick up afterwards
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                damage.ifPresent(d -> arrow.setBaseDamage(ctx.scaled(d)));
                yield arrow;
            }
            case SNOWBALL -> new Snowball(level, host);
            case EGG -> new ThrownEgg(level, host);
            case SMALL_FIREBALL -> new SmallFireball(level, host, dir.normalize());
            case LARGE_FIREBALL -> new LargeFireball(level, host, dir.normalize(), Math.max(0, ctx.levelled(power)));
            case WITHER_SKULL -> new WitherSkull(level, host, dir.normalize());
            case LLAMA_SPIT -> {
                LlamaSpit spit = new LlamaSpit(EntityType.LLAMA_SPIT, level);
                spit.setOwner(host);
                yield spit;
            }
            case SHULKER_BULLET -> target == null ? null : new ShulkerBullet(level, host, target, Direction.Axis.Y);
            case WIND_CHARGE -> {
                WindCharge charge = new WindCharge(EntityType.WIND_CHARGE, level);
                charge.setOwner(host);
                yield charge;
            }
            case TRIDENT -> {
                ThrownTrident trident = new ThrownTrident(level, host, ammo);
                trident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                yield trident;
            }
            case SPLASH_POTION -> {
                ThrownPotion thrown = new ThrownPotion(level, host);
                thrown.setItem(ammo);
                yield thrown;
            }
        };
    }

    /**
     * One splash or lingering potion out of the host's inventory (a player's, a minion's, or its hands), used up unless the
     * host is a creative player; else one of {@code potion}; else nothing.
     */
    private ItemStack potionFrom(LivingEntity host) {
        Container inventory = host instanceof Player player ? player.getInventory() : host instanceof MinionEntity minion ? minion.inventory : null;
        boolean free = host instanceof Player player && player.hasInfiniteMaterials();
        if (inventory != null) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
                    ItemStack one = stack.copyWithCount(1);
                    if (!free) {
                        stack.shrink(1);
                        inventory.setChanged();
                    }
                    return one;
                }
            }
        }
        for (ItemStack held : new ItemStack[]{host.getMainHandItem(), host.getOffhandItem()}) {
            if (held.is(Items.SPLASH_POTION) || held.is(Items.LINGERING_POTION)) {
                ItemStack one = held.copyWithCount(1);
                if (!free) {
                    held.shrink(1);
                }
                return one;
            }
        }
        return potion.map(p -> PotionContents.createItemStack(Items.SPLASH_POTION, p)).orElse(ItemStack.EMPTY);
    }
}
