package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.TraitConditions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Which creatures a social effect works on, written in data as one word or a list of them; a creature is picked if any
 * one matches. A word may join several tests with "+", all of which must hold ("hostile+underwater").
 * <ul>
 *     <li>{@code any}: anything alive but the host</li>
 *     <li>{@code allies}: the host's own side: a player's minions and tamed animals, a minion's maker, its maker's other
 *     minions and pets, and team mates</li>
 *     <li>{@code others}: anything alive that is not on the host's side</li>
 *     <li>{@code hostile}: monsters, and any mob going for the host or its side; never one of its side</li>
 *     <li>{@code wounded}: under half its health</li>
 *     <li>{@code invisible}, {@code underwater}, {@code moving} (moving about, not creeping)</li>
 *     <li>an entity id ("minecraft:cat") or tag ("#minecraft:zombies")</li>
 *     <li>{@code family:<group>}: a mob in that mob group, any layer of it ("family:canid", "family:bloodandbones:golem")</li>
 * </ul>
 */
public record SocialFilter(List<String> words, List<List<Test>> tests) {
    /** One test of a word: a keyword, an id or tag, or a mob group. */
    public record Test(String keyword, @Nullable TraitConditions.IdOrTag<EntityType<?>> type, @Nullable ResourceLocation group) {
    }

    private static final List<String> KEYWORDS = List.of("any", "allies", "others", "hostile", "wounded", "invisible", "underwater", "moving");

    public static final Codec<SocialFilter> CODEC = Codec.withAlternative(Codec.STRING.listOf(), Codec.STRING.xmap(List::of, List::getFirst))
            .comapFlatMap(SocialFilter::parse, SocialFilter::words);

    public static final SocialFilter ANY = of("any");
    public static final SocialFilter ALLIES = of("allies");
    public static final SocialFilter OTHERS = of("others");
    public static final SocialFilter HOSTILE = of("hostile");

    public static SocialFilter of(String... words) {
        return parse(List.of(words)).getOrThrow();
    }

    private static DataResult<SocialFilter> parse(List<String> words) {
        List<List<Test>> tests = new ArrayList<>();
        for (String word : words) {
            List<Test> all = new ArrayList<>();
            for (String part : word.split("\\+")) {
                String term = part.trim();
                if (term.startsWith("family:")) {
                    String group = term.substring("family:".length());
                    ResourceLocation id = group.contains(":") ? ResourceLocation.tryParse(group) : BloodAndBones.asResource(group);
                    if (id == null) {
                        return DataResult.error(() -> "Not a mob group: " + term);
                    }
                    all.add(new Test("family", null, id));
                } else if (term.startsWith("#") || term.contains(":")) {
                    boolean tag = term.startsWith("#");
                    ResourceLocation id = ResourceLocation.tryParse(tag ? term.substring(1) : term);
                    if (id == null) {
                        return DataResult.error(() -> "Not an entity id or tag: " + term);
                    }
                    all.add(new Test("type", new TraitConditions.IdOrTag<>(Registries.ENTITY_TYPE, id, tag), null));
                } else if (KEYWORDS.contains(term)) {
                    all.add(new Test(term, null, null));
                } else {
                    return DataResult.error(() -> "Unknown creature filter '" + term + "': one of " + KEYWORDS + ", an entity id, a #tag or family:<group>");
                }
            }
            tests.add(List.copyOf(all));
        }
        return DataResult.success(new SocialFilter(List.copyOf(words), List.copyOf(tests)));
    }

    /** Whether the filter picks {@code other}, seen from {@code host}; never the host itself, nor anything dead. */
    public boolean test(LivingEntity host, LivingEntity other) {
        if (other == host || !other.isAlive()) {
            return false;
        }
        for (List<Test> all : tests) {
            boolean ok = true;
            for (Test test : all) {
                if (!passes(test, host, other)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return true;
            }
        }
        return false;
    }

    private static boolean passes(Test test, LivingEntity host, LivingEntity other) {
        return switch (test.keyword()) {
            case "any" -> true;
            case "allies" -> allied(host, other);
            case "others" -> !allied(host, other);
            case "hostile" -> hostile(host, other);
            case "wounded" -> other.getHealth() < other.getMaxHealth() * 0.5F;
            case "invisible" -> other.isInvisible();
            case "underwater" -> other.isInWater();
            case "moving" -> moving(other);
            case "type" -> test.type() != null && test.type().matches(other.getType().builtInRegistryHolder());
            case "family" -> other instanceof Mob && !(other instanceof MinionEntity) && PartsData.of(other.level())
                    .resolve(BuiltInRegistries.ENTITY_TYPE.getKey(other.getType()), other.isBaby()).layers().contains(test.group());
            default -> false;
        };
    }

    /**
     * Whether two creatures are on one side: the same team, or the same player behind them (a player, their minions and
     * their tamed animals are all on that player's side).
     */
    public static boolean allied(LivingEntity host, LivingEntity other) {
        if (other == host) {
            return false;
        }
        if (host.isAlliedTo(other)) {
            return true;
        }
        UUID side = side(host);
        return side != null && side.equals(side(other));
    }

    /** The player a creature answers to: a player themself, a minion's maker, a pet's owner; null for the rest. */
    @Nullable
    public static UUID side(LivingEntity entity) {
        if (entity instanceof Player player) {
            return player.getUUID();
        }
        if (entity instanceof MinionEntity minion) {
            return minion.makerId();
        }
        if (entity instanceof OwnableEntity pet) {
            return pet.getOwnerUUID();
        }
        return null;
    }

    /** A monster, or a mob going for the host or one of its side; never one of the host's own side. */
    public static boolean hostile(LivingEntity host, LivingEntity other) {
        if (allied(host, other)) {
            return false;
        }
        if (other instanceof Enemy) {
            return true;
        }
        LivingEntity target = other instanceof Mob mob ? mob.getTarget() : null;
        return target != null && (target == host || allied(host, target));
    }

    /** Moving about, and not creeping (a sneaking player, or anything stepping carefully, goes unfelt). */
    public static boolean moving(LivingEntity other) {
        return !other.isSteppingCarefully() && other.position().distanceToSqr(other.xo, other.yo, other.zo) > 1.0E-4;
    }
}
