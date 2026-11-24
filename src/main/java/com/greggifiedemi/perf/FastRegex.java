package com.greggifiedemi.perf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Faster matching for EMI's data-pack filters (recipe and index filters in {@code EmiData}), which call
 * {@code pattern.matcher(id).find()} for every recipe or stack. Not an NEI feature: an EMI load-time fix, ported from
 * the owner's TFG performance fixes ({@code emiFastRegexFilters}).
 *
 * <p>A pattern such as {@code .+foo.*} makes {@code find()} quadratic: it retries the greedy {@code .+} from every start
 * index. For a pattern P = {@code .+Q} or {@code .*Q} (any quantifier flavour) without a top-level {@code |} and
 * without {@code \G}, and an input without line terminators (or a DOTALL pattern), "P matches starting at some index
 * i" implies "P matches starting at index 0", because the leading dot run also absorbs characters 0..i. So
 * {@code find()} gives the same answer as {@code ^(?:P)}, which is decided in one pass. Every other pattern is used as
 * it is. No Minecraft dependency, so the rewrite is unit-tested against the original.
 */
public final class FastRegex {

    private static final Pattern NOT_ELIGIBLE = Pattern.compile("");
    private static final Map<Pattern, Pattern> ANCHORED = new ConcurrentHashMap<>();
    private static final AtomicLong FAST = new AtomicLong();

    private FastRegex() {}

    /** A matcher whose {@code find()} answers exactly like {@code pattern.matcher(input).find()}. */
    public static Matcher matcher(Pattern pattern, CharSequence input) {
        final Pattern anchored = ANCHORED.computeIfAbsent(pattern, FastRegex::anchoredOrMarker);

        if (anchored != NOT_ELIGIBLE && ((pattern.flags() & Pattern.DOTALL) != 0 || !hasLineTerminator(input))) {
            FAST.incrementAndGet();
            return anchored.matcher(input);
        }

        return pattern.matcher(input);
    }

    /** How many matches took the anchored path since start (for tests and the pack testkit). */
    public static long fastMatches() {
        return FAST.get();
    }

    static boolean hasLineTerminator(CharSequence text) {
        for (int i = 0, n = text.length(); i < n; i++) {
            final char c = text.charAt(i);

            if (c == '\n' || c == '\r' || c == '\u0085' || c == ' ' || c == ' ') {
                return true;
            }
        }

        return false;
    }

    static Pattern anchoredOrMarker(Pattern pattern) {
        final String regex = pattern.pattern();

        if ((pattern.flags() & (Pattern.LITERAL | Pattern.COMMENTS)) != 0) {
            return NOT_ELIGIBLE;
        }

        if (!(regex.startsWith(".+") || regex.startsWith(".*")) || regex.contains("\\G")
                || hasTopLevelAlternation(regex)) {
            return NOT_ELIGIBLE;
        }

        try {
            return Pattern.compile("^(?:" + regex + ")", pattern.flags());
        } catch (RuntimeException e) {
            return NOT_ELIGIBLE;
        }
    }

    static boolean isEligible(Pattern pattern) {
        return anchoredOrMarker(pattern) != NOT_ELIGIBLE;
    }

    /** Conservative scan: any doubt (quoting, unbalanced groups or classes) counts as an alternation. */
    static boolean hasTopLevelAlternation(String regex) {
        int depth = 0;
        int classDepth = 0;

        for (int i = 0; i < regex.length(); i++) {
            final char c = regex.charAt(i);

            if (c == '\\') {
                if (i + 1 < regex.length() && regex.charAt(i + 1) == 'Q') {
                    return true;
                }
                i++;
            } else if (classDepth > 0) {
                if (c == '[') {
                    classDepth++;
                } else if (c == ']') {
                    classDepth--;
                }
            } else if (c == '[') {
                classDepth = 1;
                // a ']' right after '[' or '[^' is a literal
                if (i + 1 < regex.length() && regex.charAt(i + 1) == '^') {
                    i++;
                }
                if (i + 1 < regex.length() && regex.charAt(i + 1) == ']') {
                    i++;
                }
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (--depth < 0) {
                    return true;
                }
            } else if (c == '|' && depth == 0) {
                return true;
            }
        }

        return depth != 0 || classDepth != 0;
    }

}
