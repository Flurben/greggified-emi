package com.greggifiedemi.nei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** NEI's search prefixes mapped onto EMI's (# tooltip and $ tag are swapped between the two). */
class NeiSearchSyntaxTest {

    @Test
    void plainQueriesAreUntouched() {
        assertEquals("iron plate", NeiSearchSyntax.toEmi("iron plate"));
        assertEquals("@gregtech", NeiSearchSyntax.toEmi("@gregtech"));
        assertEquals("", NeiSearchSyntax.toEmi(""));
    }

    @Test
    void tooltipAndTagPrefixesAreSwapped() {
        assertEquals("$voltage", NeiSearchSyntax.toEmi("#voltage"));
        assertEquals("#forge:ingots", NeiSearchSyntax.toEmi("$forge:ingots"));
        assertEquals("iron $lv #forge:plates", NeiSearchSyntax.toEmi("iron #lv $forge:plates"));
    }

    @Test
    void negationKeepsThePrefix() {
        assertEquals("-$deprecated", NeiSearchSyntax.toEmi("-#deprecated"));
        assertEquals("-#forge:dusts", NeiSearchSyntax.toEmi("-$forge:dusts"));
    }

    @Test
    void alternativesStartNewTokens() {
        assertEquals("$a|#b", NeiSearchSyntax.toEmi("#a|$b"));
        assertEquals("$a | #b", NeiSearchSyntax.toEmi("#a | $b"));
    }

    @Test
    void onlyTheTokenStartIsSwapped() {
        assertEquals("a#b c$d", NeiSearchSyntax.toEmi("a#b c$d"));
        assertEquals("$a#b", NeiSearchSyntax.toEmi("#a#b"));
    }

    @Test
    void quotesAndRegexAreLeftAlone() {
        assertEquals("\"#not a prefix\"", NeiSearchSyntax.toEmi("\"#not a prefix\""));
        assertEquals("/#x|$y/", NeiSearchSyntax.toEmi("/#x|$y/"));
        assertEquals("\"a b\" $c", NeiSearchSyntax.toEmi("\"a b\" #c"));
    }

    @Test
    void identifierTokens() {
        assertTrue(NeiSearchSyntax.isIdentifierToken("&minecraft:stone"));
        assertTrue(NeiSearchSyntax.isIdentifierToken("&/gtceu:.*_plate/"));
        assertFalse(NeiSearchSyntax.isIdentifierToken("&"));
        assertFalse(NeiSearchSyntax.isIdentifierToken("stone"));
    }

}
