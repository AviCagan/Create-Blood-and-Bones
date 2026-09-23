package com.avicagan.bloodandbones.config;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words bloodless mode shows in place of bloody ones, in this mod's names and descriptions: blood
 * becomes essence, bleeding draining, bloody stained. A translation can set its own text for any key under
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
            swap("gory", "messy"),
            swap("gore", "mess"));

    private BloodlessWords() {
    }

    /** One word, matched whole (so "bloodless" is left alone), in lower case or with a capital. */
    private static Swap swap(String word, String with) {
        return new Swap(Pattern.compile("(?<![A-Za-z])([" + Character.toUpperCase(word.charAt(0)) + word.charAt(0) + "])"
                + word.substring(1) + "(?![A-Za-z])"), with);
    }

    public static String soften(String text) {
        String out = text;
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
        return out;
    }

    /** Whether bloodless mode rewords this translation key: this mod's own text, but not its name. */
    public static boolean reworded(String key) {
        return key.contains("bloodandbones") && !key.startsWith("itemGroup.") && !key.startsWith("bloodless.");
    }
}
