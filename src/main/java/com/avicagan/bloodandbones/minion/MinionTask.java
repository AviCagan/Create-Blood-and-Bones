package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.BloodAndBones;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The tasks a minion can be given (docs/NEXT.md 1.1): sixteen, in the order a task screen lists them, fight, tend, fetch
 * and work. Any task can go to any minion; its build decides how well it does it ({@link MinionFitness}). What is code
 * here is what a task is and the tests of its body a datapack cannot change (something to strike with, a detonating organ,
 * something to hold its work with); what can be retuned is its {@link Data}, from {@code data/<ns>/minion_task/<task>.json}
 * over the defaults below, which are today's constants, so a missing file changes nothing.
 * <p>
 * Each task's goals, the task a minion wakes to and the task screen are {@link MinionTasks}'s; how well a minion does each
 * task is {@link MinionFitness}'s.
 */
public enum MinionTask {
    IDLE("idle", Kind.NONE, Need.NONE, null),
    GUARD("guard", Kind.FIGHT, Need.STRIKE, null),
    SENTRY("sentry", Kind.FIGHT, Need.STRIKE, null),
    HUNTER("hunter", Kind.FIGHT, Need.STRIKE, null),
    SAPPER("sapper", Kind.FIGHT, Need.DETONATOR, null),
    SURGEON("surgeon", Kind.TEND, Need.GRIP, "hand"),
    MEDIC("medic", Kind.TEND, Need.GRIP, "throw"),
    HERDER("herder", Kind.TEND, Need.GRIP, "bait"),
    TENDER("tender", Kind.TEND, Need.NONE, null),
    COURIER("courier", Kind.FETCH, Need.NONE, null),
    HAULER("hauler", Kind.FETCH, Need.NONE, null),
    FARMER("farmer", Kind.WORK, Need.GRIP, "pick"),
    FISHER("fisher", Kind.WORK, Need.GRIP, "catch"),
    BUTCHER("butcher", Kind.WORK, Need.GRIP, "blade"),
    BARTERER("barterer", Kind.WORK, Need.GRIP, "pick"),
    DIGGER("digger", Kind.WORK, Need.GRIP, "nose");

    /** What sort of work a task is: a head's disposition scales each sort (docs/NEXT.md 1.2). Idle is none of them. */
    public enum Kind {
        NONE, FIGHT, TEND, FETCH, WORK;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Nullable
        public static Kind byKey(String key) {
            for (Kind kind : values()) {
                if (kind.key().equals(key)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** Where a task is centred: on home (a sentry's post, a surgeon's table), or on its maker ("with me"). */
    public enum Anchor {
        HOME, MAKER;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Nullable
        public static Anchor byKey(String key) {
            for (Anchor anchor : values()) {
                if (anchor.key().equals(key)) {
                    return anchor;
                }
            }
            return null;
        }
    }

    /**
     * The test of its body a task makes in code: none, something to strike with (an arm that is not folded, or a head to
     * bite with), a detonating organ, or something its grip table allows (a hand for the surgeon's blade...).
     */
    public enum Need {
        NONE, STRIKE, DETONATOR, GRIP
    }

    /** The stats a task can read (docs/NEXT.md 1.2), a closed list. */
    public enum Stat {
        PACE, SIGHT, HANDS, BLOW, RANGED, TOUGHNESS, CARRY, PULL;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Nullable
        public static Stat byKey(String key) {
            for (Stat stat : values()) {
                if (stat.key().equals(key)) {
                    return stat;
                }
            }
            return null;
        }
    }

    public final ResourceLocation id;
    /**
     * Its sort of work as shipped: the default of its data's {@link Data#kind}, which is what a disposition scales and the task
     * screen groups it under, so a datapack may move it.
     */
    public final Kind kind;
    public final Need need;
    /** What its reason says when its grip table leaves the body nothing to do it with ("cannot.hand"...), for a grip task. */
    @Nullable
    public final String gripReason;

    MinionTask(String path, Kind kind, Need need, @Nullable String gripReason) {
        this.id = BloodAndBones.asResource(path);
        this.kind = kind;
        this.need = need;
        this.gripReason = gripReason;
    }

    /** The task of this id, or null for none. */
    @Nullable
    public static MinionTask byId(@Nullable ResourceLocation id) {
        for (MinionTask task : values()) {
            if (task.id.equals(id)) {
                return task;
            }
        }
        return null;
    }

    /** Its name's key: "bloodandbones.minion.task.farmer". */
    public String nameKey() {
        return "bloodandbones.minion.task." + id.getPath();
    }

    /** The key of why a body cannot do it: "bloodandbones.minion.cannot.strike". */
    @Nullable
    public String cannotKey() {
        return switch (need) {
            case NONE -> null;
            case STRIKE -> "bloodandbones.minion.cannot.strike";
            case DETONATOR -> "bloodandbones.minion.cannot.detonator";
            case GRIP -> "bloodandbones.minion.cannot." + gripReason;
        };
    }

    /** Whether it is rated at all: Idle is shown as "-". */
    public boolean rated() {
        return this != IDLE;
    }

    // ---- the grip tables (docs/NEXT.md 1.2): what each thing a body holds with does for a sort of work; a grip missing
    // from a table cannot do that work at all

    public static final Map<String, Float> TOOL = grips("hand", 1.0F);
    public static final Map<String, Float> BLADE = grips("hand", 1.0F, "tentacle", 0.5F, "mouth", 0.35F);
    public static final Map<String, Float> PICKING = grips("hand", 1.0F, "paw", 0.75F, "claw", 0.75F, "tentacle", 0.6F, "hoof", 0.45F, "mouth", 0.35F);
    public static final Map<String, Float> FISHING = grips("hand", 0.35F, "paw", 0.6F, "claw", 0.6F, "tentacle", 0.6F, "mouth", 0.35F);
    /** Fishing with a rod in hand. */
    public static final Map<String, Float> FISHING_ROD = grips("hand", 1.0F, "paw", 0.6F, "claw", 0.6F, "tentacle", 0.6F, "mouth", 0.35F);
    public static final Map<String, Float> HOLDING = grips("hand", 1.0F, "paw", 1.0F, "claw", 1.0F, "tentacle", 1.0F, "wing", 0.6F, "mouth", 0.75F);
    public static final Map<String, Float> CARRYING = grips("hand", 1.0F, "paw", 1.0F, "claw", 1.0F, "tentacle", 1.0F, "hoof", 0.75F, "wing", 0.6F,
            "mouth", 0.75F, "none", 0.35F);
    public static final Map<String, Float> NOSE = grips("paw", 0.6F, "claw", 0.6F, "mouth", 1.0F);

    private static Map<String, Float> grips(Object... pairs) {
        Map<String, Float> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], (Float) pairs[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    /**
     * What a task needs in hand or carries to work, and what that changes: a butcher's blade, a fisher's rod, a herder's
     * bait, a medic's healing potions, a courier's sample. A missing one never shuts the task: a required one makes it wait
     * for it; one that is not required changes only how well it works.
     *
     * @param items    an item id or "#tag" it must be; empty: the task's own test in code (a Cleaver or Flensing Knife, a rod,
     *                 anything held as bait or sample, a bow, crossbow or trident, a healing potion)
     * @param required it waits until it has one
     * @param carried  it is among what it carries, not in its hand (a medic's potions)
     * @param grips    the grip table to use while it holds one (a rod in hand fishes as a player does), if other than the task's
     */
    public record Tool(Optional<String> items, boolean required, boolean carried, Optional<Map<String, Float>> grips) {
        /** A tool the task's own code tests for, required or not. */
        static Tool of(boolean required, boolean carried, @Nullable Map<String, Float> grips) {
            return new Tool(Optional.empty(), required, carried, Optional.ofNullable(grips));
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            items.ifPresent(i -> o.addProperty("items", i));
            o.addProperty("required", required);
            if (carried) {
                o.addProperty("carried", true);
            }
            grips.ifPresent(g -> o.add("grips", gripsJson(g)));
            return o;
        }

        Tool read(JsonObject o) {
            return new Tool(o.has("items") ? Optional.of(o.get("items").getAsString()) : items, o.has("required") ? o.get("required").getAsBoolean() : required,
                    o.has("carried") ? o.get("carried").getAsBoolean() : carried, o.has("grips") ? Optional.of(readGrips(o.get("grips"))) : grips);
        }
    }

    /**
     * What a datapack can retune of a task (docs/NEXT.md 1.6): its kind; the anchors it allows; its reach and the most its
     * maker may set it to; the stats its fitness reads (the second counts at half weight, a square root); its grip table;
     * its tool; whether its takings go into the container by home; the base numbers its levers scale; and, for the surgeon,
     * whether only a head whose data says {@code "surgeon": true} may do the ritual's cutting (docs/NEXT.md 1.5; false by
     * default: any minion with a hand may cut, and its fitness sets the stump's price).
     */
    public record Data(Kind kind, List<Anchor> anchors, int reach, int maxReach, Optional<Stat> main, Optional<Stat> second, Map<String, Float> grips,
                       Optional<Tool> tool, boolean stores, Map<String, Float> numbers, boolean needsSurgeonHead) {
        /** One of its numbers, or the fallback if neither its file nor the code names it. */
        public float number(String name, float fallback) {
            return numbers.getOrDefault(name, fallback);
        }

        public boolean allows(Anchor anchor) {
            return anchors.contains(anchor);
        }

        /** Where it is set when asked to be set here: here if it allows it, else the first anchor it allows. */
        public Anchor anchorFor(Anchor at) {
            return allows(at) || anchors.isEmpty() ? at : anchors.get(0);
        }

        /** As a task file writes it. */
        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("kind", kind.key());
            JsonArray a = new JsonArray();
            anchors.forEach(anchor -> a.add(anchor.key()));
            o.add("anchors", a);
            o.addProperty("reach", reach);
            o.addProperty("max_reach", maxReach);
            main.ifPresent(s -> o.addProperty("main", s.key()));
            second.ifPresent(s -> o.addProperty("second", s.key()));
            if (!grips.isEmpty()) {
                o.add("grips", gripsJson(grips));
            }
            tool.ifPresent(t -> o.add("tool", t.toJson()));
            o.addProperty("stores", stores);
            JsonObject n = new JsonObject();
            numbers.forEach(n::addProperty);
            o.add("numbers", n);
            if (needsSurgeonHead) {
                o.addProperty("needs_surgeon_head", true);
            }
            return o;
        }

        /**
         * A task file read over these defaults: what it names takes the place of the default ("numbers" and the tool's fields
         * one by one, so a file can retune one number), the rest stays. A reach past the most is held to it.
         */
        public Data read(JsonObject o) {
            Kind k = o.has("kind") ? Optional.ofNullable(Kind.byKey(o.get("kind").getAsString())).orElseThrow(() -> new IllegalArgumentException("unknown kind " + o.get("kind")))
                    : kind;
            List<Anchor> as = anchors;
            if (o.has("anchors")) {
                as = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("anchors")) {
                    Anchor anchor = Anchor.byKey(e.getAsString());
                    if (anchor == null) {
                        throw new IllegalArgumentException("unknown anchor " + e);
                    }
                    as.add(anchor);
                }
                as = List.copyOf(as);
            }
            int most = o.has("max_reach") ? o.get("max_reach").getAsInt() : maxReach;
            int r = o.has("reach") ? o.get("reach").getAsInt() : reach;
            Map<String, Float> n = new LinkedHashMap<>(numbers);
            if (o.has("numbers")) {
                o.getAsJsonObject("numbers").entrySet().forEach(e -> n.put(e.getKey(), e.getValue().getAsFloat()));
            }
            return new Data(k, as, Math.max(1, Math.min(r, most)), Math.max(1, most), o.has("main") ? stat(o.get("main")) : main,
                    o.has("second") ? stat(o.get("second")) : second, o.has("grips") ? readGrips(o.get("grips")) : grips,
                    o.has("tool") ? Optional.of(tool.orElse(Tool.of(false, false, null)).read(o.getAsJsonObject("tool"))) : tool, o.has("stores") ? o.get("stores").getAsBoolean() : stores,
                    java.util.Collections.unmodifiableMap(n), o.has("needs_surgeon_head") ? o.get("needs_surgeon_head").getAsBoolean() : needsSurgeonHead);
        }

        private static Optional<Stat> stat(JsonElement e) {
            if (e.isJsonNull() || "none".equals(e.getAsString())) {
                return Optional.empty();
            }
            Stat stat = Stat.byKey(e.getAsString());
            if (stat == null) {
                throw new IllegalArgumentException("unknown stat " + e + ": one of pace, sight, hands, blow, ranged, toughness, carry, pull");
            }
            return Optional.of(stat);
        }
    }

    private static JsonObject gripsJson(Map<String, Float> grips) {
        JsonObject o = new JsonObject();
        grips.forEach(o::addProperty);
        return o;
    }

    private static Map<String, Float> readGrips(JsonElement json) {
        Map<String, Float> out = new LinkedHashMap<>();
        json.getAsJsonObject().entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsFloat()));
        return java.util.Collections.unmodifiableMap(out);
    }

    private static final List<Anchor> HOME = List.of(Anchor.HOME);
    private static final List<Anchor> HOME_OR_MAKER = List.of(Anchor.HOME, Anchor.MAKER);

    /**
     * Its data when no file retunes it: today's constants (docs/NEXT.md 1.1 and 1.2). Reach is how far from its anchor it
     * works; its maker may set it from 2 to twice that, to at most 32 for a task that looks for creatures or loose items
     * and 12 for one that reads blocks (the courier up to 32, as the scavenger fetched).
     */
    public Data defaults() {
        return switch (this) {
            case IDLE -> data(HOME_OR_MAKER, 4, 8, null, null, Map.of(), null, false, Map.of());
            case GUARD -> data(HOME_OR_MAKER, 16, 32, Stat.BLOW, Stat.TOUGHNESS, Map.of(), null, false, Map.of());
            // a sentry holds its post (home, where it stood when set) and shoots what comes within its range: vanilla's goals'
            // times between shots, and the spread of its aim (14 less 4 for each step of difficulty)
            case SENTRY -> data(HOME, 16, 32, Stat.RANGED, Stat.SIGHT, Map.of(), Tool.of(false, false, null), false,
                    numbers("bow_every", 20, "crossbow_min", 20, "crossbow_max", 40, "trident_every", 40, "spread", 14, "spread_per_difficulty", 4));
            case HUNTER -> data(HOME_OR_MAKER, 12, 24, Stat.BLOW, Stat.PACE, Map.of(), null, false, Map.of());
            case SAPPER -> data(HOME, 16, 32, Stat.PACE, Stat.TOUGHNESS, Map.of(), null, false, Map.of());
            // it keeps by its table (its home, or the nearest within its reach of where it stands) and tends a heart every 5 s, never faster than every 2; a
            // stump it cuts costs a bucket of blood to fit at 150% and over, two from 75%, three below (docs/NEXT.md 1.5)
            case SURGEON -> data(HOME, 6, 12, Stat.HANDS, Stat.SIGHT, TOOL, null, false, numbers("tend_every", 100, "tend_least", 40,
                    "one_bucket_from", MinionFitness.CLEAN_CUT, "two_buckets_from", MinionFitness.FAIR_CUT));
            case MEDIC -> data(HOME_OR_MAKER, 16, 32, Stat.HANDS, Stat.SIGHT, PICKING, Tool.of(true, true, null), false,
                    numbers("throw_every", 60, "throw_least", 20, "spread", 8));
            // it keeps its herd within 8 of home, looks out to 20 for strays, and gives up on one after 30 s
            case HERDER -> data(HOME, 8, 16, Stat.PACE, Stat.SIGHT, HOLDING, Tool.of(true, false, null), false,
                    numbers("search", 20, "wait", 600));
            // it looks round every second, and keeps a cradle stocked with up to 16 brass sheets
            case TENDER -> data(HOME, 8, 12, Stat.CARRY, Stat.PACE, CARRYING, null, false, numbers("look_every", 20, "sheets", 16));
            // a sample in hand narrows what it fetches, held as anything holds one; it looks round every second, as the
            // scavenger did (a one-in-ten chance each time its goal was asked, which is every other tick)
            case COURIER -> data(HOME_OR_MAKER, 10, 32, Stat.CARRY, Stat.PACE, CARRYING, Tool.of(false, false, HOLDING), true,
                    numbers("look_every", 20));
            // it tows at most 90% slower than it walks, and never with less slowdown than a player
            case HAULER -> data(HOME, 24, 32, Stat.PULL, Stat.PACE, Map.of(), null, false, numbers("slowdown_most", 0.9F));
            // it looks for ripe crops every second, as the farmer job did
            case FARMER -> data(HOME, 8, 12, Stat.HANDS, Stat.SIGHT, PICKING, null, true, numbers("look_every", 20));
            // 30 to 60 s between catches, never under a sixth of that (Lure's floor); a rod in hand fishes as a player's does
            case FISHER -> data(HOME, 8, 12, Stat.HANDS, Stat.SIGHT, FISHING, Tool.of(false, false, FISHING_ROD), true,
                    numbers("catch_min", 600, "catch_max", 1200, "catch_least", 1.0F / 6.0F));
            // a stroke every 0.75 s, never faster than every 0.3 s
            case BUTCHER -> data(HOME, 6, 12, Stat.HANDS, Stat.BLOW, BLADE, Tool.of(true, false, null), true,
                    numbers("stroke", 15, "stroke_least", 6));
            case BARTERER -> data(HOME, 6, 12, Stat.HANDS, null, PICKING, null, true, numbers("admire", 120));
            // 1 to 2 minutes between finds
            case DIGGER -> data(HOME, 6, 12, Stat.HANDS, null, NOSE, null, true, numbers("dig_min", 1200, "dig_max", 2400));
        };
    }

    /**
     * A task file's data as the goals can do it (docs/NEXT.md 1.6): its anchors only those the task's goals work from, since
     * a file can take an anchor away but not teach a goal a new one (only Idle, Guard, Hunter, Medic and Courier work round
     * their maker). One it cannot do is left out, and logged; with none left, the task's own stand.
     */
    public Data checked(Data data, ResourceLocation file) {
        List<Anchor> allowed = defaults().anchors();
        List<Anchor> kept = data.anchors().stream().filter(allowed::contains).toList();
        if (kept.size() < data.anchors().size()) {
            BloodAndBones.LOGGER.warn("Minion task file {}: {} is done only {}; left out {}", file, id, allowed,
                    data.anchors().stream().filter(a -> !allowed.contains(a)).toList());
        }
        if (kept.isEmpty()) {
            BloodAndBones.LOGGER.warn("Minion task file {} allows {} nowhere it can be done; it keeps {}", file, id, allowed);
            kept = allowed;
        }
        return kept.equals(data.anchors()) ? data : new Data(data.kind(), kept, data.reach(), data.maxReach(), data.main(), data.second(), data.grips(),
                data.tool(), data.stores(), data.numbers(), data.needsSurgeonHead());
    }

    private Data data(List<Anchor> anchors, int reach, int most, @Nullable Stat main, @Nullable Stat second, Map<String, Float> grips, @Nullable Tool tool,
                      boolean stores, Map<String, Float> numbers) {
        return new Data(kind, anchors, reach, most, Optional.ofNullable(main), Optional.ofNullable(second), grips, Optional.ofNullable(tool), stores, numbers, false);
    }

    private static Map<String, Float> numbers(Object... pairs) {
        Map<String, Float> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], ((Number) pairs[i + 1]).floatValue());
        }
        return java.util.Collections.unmodifiableMap(out);
    }
}
