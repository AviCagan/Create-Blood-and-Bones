package com.avicagan.bloodandbones.config;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words bloodless mode shows in place of bloody ones, in this mod's names and descriptions: blood
 * becomes essence, bleeding draining, bloody stained, a minion a construct, stitches rivets, a carcass a wreck,
 * severed detached, flesh plated. A translation can set its own text for any key under
 * {@code bloodless.<key>} instead; this is only the fallback, and only knows English.
 */
public final class BloodlessWords {
    private record Swap(Pattern pattern, String with) {
    }

    private static final List<Swap> SWAPS = List.of(
            swap("bloodied", "stained"),
            swap("bloody", "stained"),
            swap("blood", "essence"),
            swap("bleeding", "draining"),
            swap("bleeds", "drains"),
            swap("bleed", "drain"),
            swap("bled", "drained"),
            swap("guts", "cords"),
            swap("gut", "cord"),
            swap("gory", "messy"),
            swap("gore", "mess"),
            // a minion is a construct, its seams rivets (docs/PARTS-AND-TRAITS.md section 7.10)
            swap("minions", "constructs"),
            swap("minion", "construct"),
            swap("stitched", "riveted"),
            swap("stitching", "riveting"),
            swap("stitch", "rivet"),
            // a dead body is a wreck, as a working one is a construct (brief rule 4: "constructs not corpses");
            // its armour is plated, as the armour's own bloodless names have it
            swap("carcass armour", "plated armour"),
            swap("carcass helmet", "plated helmet"),
            swap("carcass chestplate", "plated chestplate"),
            swap("carcass leggings", "plated leggings"),
            swap("carcass boots", "plated boots"),
            swap("carcasses", "wrecks"),
            swap("carcass", "wreck"),
            // parts come off, not away: "replacement" not "amputation" (brief rule 4)
            swap("severed", "detached"),
            swap("severing", "detaching"),
            swap("severs", "detaches"),
            swap("sever", "detach"),
            swap("amputations", "replacements"),
            swap("amputation", "replacement"),
            swap("stumps", "sockets"),
            swap("stump", "socket"),
            // clean plating, not grafted flesh; organic parts wear, they do not rot
            swap("flesh", "plated"),
            swap("necrosis", "wear"));
    /** Phrases left as they are: the names of vanilla things, which bloodless mode does not rename. */
    private static final List<String> KEPT = List.of("Rotten Flesh", "rotten flesh");

    private BloodlessWords() {
    }

    /** One word, matched whole (so "bloodless" is left alone), in lower case or with a capital. */
    private static Swap swap(String word, String with) {
        return new Swap(Pattern.compile("(?<![A-Za-z])([" + Character.toUpperCase(word.charAt(0)) + word.charAt(0) + "])"
                + word.substring(1) + "(?![A-Za-z])"), with);
    }

    public static String soften(String text) {
        // the mod's own name stays as it is, and so do vanilla things' names
        String out = text.replace(MOD_NAME, NAME_MARK);
        for (int i = 0; i < KEPT.size(); i++) {
            out = out.replace(KEPT.get(i), "\u0000kept" + i + "\u0000");
        }
        for (Swap swap : SWAPS) {
            Matcher matcher = swap.pattern().matcher(out);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String with = Character.isUpperCase(matcher.group(1).charAt(0))
                        ? Character.toUpperCase(swap.with().charAt(0)) + swap.with().substring(1) : swap.with();
                matcher.appendReplacement(sb, Matcher.quoteReplacement(with));
            }
            matcher.appendTail(sb);
            out = sb.toString();
        }
        for (int i = 0; i < KEPT.size(); i++) {
            out = out.replace("\u0000kept" + i + "\u0000", KEPT.get(i));
        }
        return out.replace(NAME_MARK, MOD_NAME);
    }

    /**
     * Words bloodless mode never shows: a test checks every reworded line of the mod against them (their own
     * {@code bloodless.} text, or the rewording above). "Bloodless" itself and the mod's name are fine.
     */
    public static final List<String> NEVER = List.of("blood", "bloody", "bloodied", "bleed", "bleeds", "bleeding", "bled", "gore", "gory", "gut", "guts",
            "carcass", "carcasses", "flesh",
            "sever", "severed", "amputation", "amputate", "stump", "necrosis", "sinew", "maggot", "maggots", "corpse", "stitch", "stitched");

    /** The first word of {@link #NEVER} this text still shows, whole words only, ignoring the kept names; or null. */
    @org.jetbrains.annotations.Nullable
    public static String gory(String text) {
        String out = text.replace(MOD_NAME, "");
        for (String kept : KEPT) {
            out = out.replace(kept, "");
        }
        for (String word : NEVER) {
            if (Pattern.compile("(?<![A-Za-z])" + word + "(?![A-Za-z])", Pattern.CASE_INSENSITIVE).matcher(out).find()) {
                return word;
            }
        }
        return null;
    }

    private static final String MOD_NAME = "Blood & Bones";
    private static final String NAME_MARK = "\u0000name\u0000";

    /**
     * Whether bloodless mode rewords this translation key: this mod's own text, but not its name, nor the
     * settings and game rule that describe bloodless mode itself.
     */
    public static boolean reworded(String key) {
        return key.contains("bloodandbones") && !key.startsWith("itemGroup.") && !key.startsWith("bloodless.")
                && !key.startsWith("bloodandbones.configuration.") && !key.startsWith("gamerule.");
    }
}
