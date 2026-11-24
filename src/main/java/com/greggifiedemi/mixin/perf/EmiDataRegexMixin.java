package com.greggifiedemi.mixin.perf;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.greggifiedemi.Config;
import com.greggifiedemi.perf.FastRegex;

import dev.emi.emi.data.EmiData;

/**
 * Every regex of EMI's data-pack filters (recipe filters, index stack filters) is matched in a lambda of
 * {@code EmiData}; this routes those matches through {@link FastRegex}, which gives identical answers in linear time.
 */
@Mixin(value = EmiData.class, remap = false)
public abstract class EmiDataRegexMixin {

    @Redirect(method = "*", at = @At(value = "INVOKE",
            target = "Ljava/util/regex/Pattern;matcher(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;"),
            require = 0)
    private static Matcher greggifiedemi$fastMatcher(Pattern pattern, CharSequence input) {
        return Config.emiFastRegexFilters() ? FastRegex.matcher(pattern, input) : pattern.matcher(input);
    }

}
