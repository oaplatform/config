package com.typesafe.config.impl;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for the fork-only single-quoted string syntax (see HOCON.md,
 * "Single-quoted strings"). Not part of upstream lightbend/config.
 */
public class SingleQuotedStringParserTest {

    private Config parseConf(String s) {
        return ConfigFactory.parseString(s,
                ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF)).resolve();
    }

    private Config parseJson(String s) {
        return ConfigFactory.parseString(s,
                ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON)).resolve();
    }

    @Test
    public void userExampleFlatVersusNestedKeys() {
        Config c = parseConf(
                "\"a.b\" = ab\n" +
                "'a.d' = ad\n" +
                "a.b.c = d\n");
        assertEquals("ab", c.getString("\"a.b\""));
        assertEquals("ad", c.getString("\"a.d\""));
        assertEquals("d", c.getString("a.b.c"));
    }

    @Test
    public void singleQuotedStringAsValue() {
        Config c = parseConf("foo = 'bar'\n");
        assertEquals("bar", c.getString("foo"));
    }

    @Test
    public void singleQuotedStringEscapes() {
        Config c = parseConf(
                "a = 'line1\\nline2'\n" +
                "b = 'a\\'b'\n" +
                "c = 'back\\\\slash'\n");
        assertEquals("line1\nline2", c.getString("a"));
        assertEquals("a'b", c.getString("b"));
        assertEquals("back\\slash", c.getString("c"));
    }

    @Test
    public void tripleSingleQuotedStringIsRaw() {
        Config c = parseConf("a = '''raw \\n text'''\n");
        assertEquals("raw \\n text", c.getString("a"));
    }

    @Test
    public void escapedSingleQuoteInsideDoubleQuotedString() {
        Config c = parseConf("a = \"a\\'b\"\n");
        assertEquals("a'b", c.getString("a"));
    }

    @Test
    public void jsonFlavorUnaffectedByApostrophe() {
        Config c = parseJson("{ \"a\": \"it's fine\" }");
        assertEquals("it's fine", c.getString("a"));
    }

    @Test
    public void unterminatedSingleQuotedStringIsAnError() {
        try {
            parseConf("a = 'unterminated\n");
            fail("expected a parse error for an unterminated single-quoted string");
        } catch (ConfigException.Parse expected) {
            // expected
        }
    }

    @Test
    public void singleQuotedKeyAlongsideBlockArray() {
        Config c = parseConf(
                "'a.d' = ad\n" +
                "arr:\n" +
                "  - item1\n" +
                "  - item2\n");
        assertEquals("ad", c.getString("\"a.d\""));
        assertEquals(java.util.Arrays.asList("item1", "item2"), c.getStringList("arr"));
    }
}
