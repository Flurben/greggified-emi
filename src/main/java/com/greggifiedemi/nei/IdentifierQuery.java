package com.greggifiedemi.nei;

import java.util.regex.Pattern;

import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.search.Query;

/** NEI's {@code &identifier} search: the stack's registry id ({@code namespace:path}) contains the text. */
public class IdentifierQuery extends Query {

    private final String text;

    public IdentifierQuery(String text) {
        this.text = text.toLowerCase();
    }

    @Override
    public boolean matches(EmiStack stack) {
        final ResourceLocation id = stack.getId();
        return id != null && (id.getPath().contains(this.text) || id.toString().contains(this.text));
    }

    /** {@code &/regex/}. */
    public static class Regex extends Query {

        private final Pattern pattern;

        public Regex(String regex) {
            Pattern compiled = null;

            try {
                compiled = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            } catch (RuntimeException ignored) {
                // an unfinished regex matches nothing, like EMI's own regex queries
            }

            this.pattern = compiled;
        }

        @Override
        public boolean matches(EmiStack stack) {
            final ResourceLocation id = stack.getId();
            return this.pattern != null && id != null && this.pattern.matcher(id.toString()).find();
        }
    }

}
