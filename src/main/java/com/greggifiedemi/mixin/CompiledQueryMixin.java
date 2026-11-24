package com.greggifiedemi.mixin;

import java.util.List;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.greggifiedemi.Config;
import com.greggifiedemi.nei.IdentifierQuery;
import com.greggifiedemi.nei.NeiSearchSyntax;

import dev.emi.emi.search.EmiSearch;
import dev.emi.emi.search.Query;

/** NEI's search prefixes in EMI's search (see {@link NeiSearchSyntax}). */
@Mixin(value = EmiSearch.CompiledQuery.class, remap = false)
public abstract class CompiledQueryMixin {

    @Shadow
    private static void addQuery(String s, boolean negated, List<Query> queries, Function<String, Query> normal,
            Function<String, Query> regex) {
        throw new AssertionError();
    }

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static String greggifiedemi$neiPrefixes(String query) {
        return Config.neiSearchSyntax() ? NeiSearchSyntax.toEmi(query) : query;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ldev/emi/emi/search/EmiSearch$CompiledQuery;"
            + "addQuery(Ljava/lang/String;ZLjava/util/List;Ljava/util/function/Function;Ljava/util/function/Function;)V"))
    private void greggifiedemi$identifierQuery(String s, boolean negated, List<Query> queries,
            Function<String, Query> normal, Function<String, Query> regex) {
        if (Config.neiSearchSyntax() && NeiSearchSyntax.isIdentifierToken(s)) {
            addQuery(s.substring(1), negated, queries, IdentifierQuery::new, IdentifierQuery.Regex::new);
        } else {
            addQuery(s, negated, queries, normal, regex);
        }
    }

}
