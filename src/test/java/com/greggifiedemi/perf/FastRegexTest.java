package com.greggifiedemi.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** {@link FastRegex} must answer exactly like {@code Pattern.matcher(input).find()}. */
class FastRegexTest {

    /** TerraFirmaGreg 0.13.17's own EMI recipe filters, plus edge cases of the rewrite. */
    private static final List<String> PATTERNS = List.of(
            ".+crafting/tipped_arrow/.+", ".+wireless_ammo_box.*", "create:create.toolbox.color.block.create.[^*]+",
            "emi:/crafting/repairing/[^*]+", "gtceu.+potion_.+", "sophisticatedbackpacks:single_color_[^*]+",
            ".*", ".+", ".*a", ".+a", ".*?a", ".+?a", ".*+a", ".++a", ".*a$", ".+^a", ".*(a|b)c", ".*a|b", ".+[|]x",
            ".*\\|x", ".+(?:x)y", ".*\\Qa|b\\E", ".+[]a]b", ".+[^]a]b", ".*(?=ab)", ".+\\bz", ".*(?i)AB", ".+\\d{2}",
            "(.+)a", "a.+");

    private static final List<String> INPUTS = List.of(
            "", "a", "b", "ab", "xab", "minecraft:crafting/tipped_arrow/poison", "tfg:wireless_ammo_box_red",
            "ae2:wireless_ammo_box", "gtceu:brewery/potion_swiftness", "gtceu:potion_", "create:create.toolbox.color.block.create.red",
            "x|y", "a|bx", "]ab", "xyz", "11", "zz", "a\nb", "line\rbreak", "x ab", "ABab");

    @Test
    void matchesFindOnFixedCases() {
        for (String regex : PATTERNS) {
            for (int flags : new int[] { 0, Pattern.DOTALL, Pattern.CASE_INSENSITIVE }) {
                final Pattern pattern = Pattern.compile(regex, flags);

                for (String input : INPUTS) {
                    assertEquals(pattern.matcher(input).find(), FastRegex.matcher(pattern, input).find(),
                            () -> "/" + regex + "/ flags " + flags + " on '" + input + "'");
                }
            }
        }
    }

    @Test
    void matchesFindOnRandomCases() {
        final Random random = new Random(42);
        final String[] pieces = { ".+", ".*", "a", "b", "/", ":", "[ab]", "[^a]", "(a|b)", "x?", "b+", "\\d", "_", "." };
        final List<String> inputs = new ArrayList<>(INPUTS);

        for (int i = 0; i < 200; i++) {
            final StringBuilder input = new StringBuilder();
            for (int j = random.nextInt(12); j > 0; j--) {
                input.append("ab/:_x1\n".charAt(random.nextInt(8)));
            }
            inputs.add(input.toString());
        }

        for (int i = 0; i < 2000; i++) {
            final StringBuilder regex = new StringBuilder(random.nextBoolean() ? ".+" : ".*");
            for (int j = random.nextInt(5); j > 0; j--) {
                regex.append(pieces[random.nextInt(pieces.length)]);
            }
            final Pattern pattern = Pattern.compile(regex.toString(), random.nextInt(4) == 0 ? Pattern.DOTALL : 0);

            for (String input : inputs) {
                assertEquals(pattern.matcher(input).find(), FastRegex.matcher(pattern, input).find(),
                        () -> "/" + pattern + "/ on '" + input + "'");
            }
        }
    }

    @Test
    void onlySafePatternsAreRewritten() {
        assertTrue(FastRegex.isEligible(Pattern.compile(".+wireless_ammo_box.*")));
        assertTrue(FastRegex.isEligible(Pattern.compile(".*(a|b)c")));
        assertFalse(FastRegex.isEligible(Pattern.compile(".*a|b")), "top-level alternation");
        assertFalse(FastRegex.isEligible(Pattern.compile(".*\\Qa|b\\E")), "quoting");
        assertFalse(FastRegex.isEligible(Pattern.compile("gtceu.+potion_.+")), "no leading dot run");
        assertFalse(FastRegex.isEligible(Pattern.compile(".+\\Gx")), "\\G");
        assertFalse(FastRegex.isEligible(Pattern.compile(".+a", Pattern.LITERAL)), "literal");
        assertTrue(FastRegex.hasLineTerminator("a b"));
        assertFalse(FastRegex.hasLineTerminator("a b"));
    }

}
