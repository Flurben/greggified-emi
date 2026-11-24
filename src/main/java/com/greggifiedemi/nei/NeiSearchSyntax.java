package com.greggifiedemi.nei;

/**
 * NEI's search prefixes on top of EMI's search (NEI's SearchTokenParser defaults): {@code @mod}, {@code #tooltip},
 * {@code $ore dictionary} (tags), {@code &identifier}. EMI uses {@code $} for tooltips and {@code #} for tags, so the
 * two are swapped before EMI parses the query; {@code &} gets its own query (see
 * {@link com.greggifiedemi.mixin.CompiledQueryMixin}). Negation ({@code -}), alternatives ({@code |}), quotes and
 * {@code /regex/} are already the same in both.
 */
public final class NeiSearchSyntax {

    /** Prefix that marks an identifier query (NEI's {@code &}). */
    public static final char IDENTIFIER = '&';

    private NeiSearchSyntax() {}

    /**
     * Swaps NEI's {@code #} and {@code $} prefixes for EMI's at the start of every token (after an optional
     * {@code -}). Text inside quotes or a {@code /regex/} is left alone.
     */
    public static String toEmi(String query) {
        if (query.indexOf('#') < 0 && query.indexOf('$') < 0) {
            return query;
        }

        final char[] chars = query.toCharArray();
        boolean tokenStart = true;
        char quote = 0;

        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];

            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                tokenStart = false;
                continue;
            }

            if (Character.isWhitespace(c) || c == '|') {
                tokenStart = true;
                continue;
            }

            if (tokenStart && c == '-') {
                // negation keeps the token open for a prefix
                continue;
            }

            if (tokenStart && (c == '#' || c == '$')) {
                chars[i] = c == '#' ? '$' : '#';
                tokenStart = false;
                continue;
            }

            if (c == '"' || c == '/' && tokenStart) {
                quote = c;
            }

            tokenStart = false;
        }

        return new String(chars);
    }

    /** Whether an EMI token (without its negation) is an NEI identifier query such as {@code &minecraft:stone}. */
    public static boolean isIdentifierToken(String token) {
        return token.length() > 1 && token.charAt(0) == IDENTIFIER;
    }

}
