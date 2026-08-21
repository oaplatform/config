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
 * Tests for the fork-only YAML-style block-scalar syntax (see HOCON.md,
 * "Block scalars (YAML-style)"). Not part of upstream lightbend/config.
 */
public class BlockScalarParserTest {

    private Config parseConf(String s) {
        return ConfigFactory.parseString(s,
                ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF)).resolve();
    }

    private Config parseJson(String s) {
        return ConfigFactory.parseString(s,
                ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON)).resolve();
    }

    @Test
    public void literalBasicClip() {
        Config c = parseConf(
            """
                a: |
                  line one
                  line two
                b = 1
                """ );
        assertEquals("line one\nline two\n", c.getString("a"));
        assertEquals(1, c.getInt("b"));
    }

    @Test
    public void literalStripChomping() {
        Config c = parseConf(
            """
                a: |-
                  line one
                  line two
                """ );
        assertEquals("line one\nline two", c.getString("a"));
    }

    @Test
    public void literalKeepChomping() {
        Config c = parseConf(
            """
                a: |+
                  line one

                b = 1
                """ );
        assertEquals("line one\n\n", c.getString("a"));
        assertEquals(1, c.getInt("b"));
    }

    @Test
    public void foldedBasicJoinsWithSpace() {
        Config c = parseConf(
            """
                a: >
                  folded
                  onto one line
                """ );
        assertEquals("folded onto one line\n", c.getString("a"));
    }

    @Test
    public void foldedBlankLineBecomesNewline() {
        Config c = parseConf(
            """
                a: >
                  para one
                  still one

                  para two
                """ );
        assertEquals("para one still one\n\npara two\n", c.getString("a"));
    }

    @Test
    public void foldedStripChomping() {
        Config c = parseConf(
            """
                a: >-
                  x
                  y
                """ );
        assertEquals("x y", c.getString("a"));
    }

    @Test
    public void emptyBlockScalarDoesNotSwallowSibling() {
        Config c = parseConf(
            """
                description: |
                name = foo
                """ );
        assertEquals("", c.getString("description"));
        assertEquals("foo", c.getString("name"));
    }

    @Test
    public void emptyBlockScalarAtEndOfFile() {
        Config c = parseConf(
            """
                a: |""" );
        assertEquals("", c.getString("a"));
    }

    @Test
    public void overhangIndentationPreservedLiteral() {
        Config c = parseConf(
            """
                a: |
                  base
                    nested
                  base again
                """ );
        assertEquals("base\n  nested\nbase again\n", c.getString("a"));
    }

    @Test
    public void inconsistentIndentationIsAnError() {
        try {
            parseConf(
                """
                    a: |
                      line one
                     line two
                    """ );
            fail("expected a parse error for inconsistent indentation");
        } catch (ConfigException.Parse expected) {
            // expected
        }
    }

    @Test
    public void nonHeaderFallbackStaysOrdinaryText() {
        Config c = parseConf(
            """
                a = | bar
                """ );
        assertEquals("| bar", c.getString("a"));
    }

    @Test
    public void nonHeaderFallbackNoSpace() {
        Config c = parseConf(
            """
                a = |bar
                """ );
        assertEquals("|bar", c.getString("a"));
    }

    @Test
    public void blockScalarNestedInsideBlockObjectField() {
        Config c = parseConf(
            """
                a:
                  b: |
                    x
                    y
                  c = 1
                """ );
        assertEquals("x\ny\n", c.getConfig("a").getString("b"));
        assertEquals(1, c.getInt("a.c"));
    }

    @Test
    public void blockScalarNestedInsideDashArrayElement() {
        Config c = parseConf(
            """
                arr:
                  - k1: |
                      x
                      y
                  - k2 = v2
                """ );
        java.util.List<? extends Config> list = c.getConfigList("arr");
        assertEquals("x\ny\n", list.get(0).getString("k1"));
        assertEquals("v2", list.get(1).getString("k2"));
    }

    @Test
    public void jsonModeUnaffected() {
        // a bare (unquoted) '|' is invalid JSON; confirms the block-scalar header
        // detection stays gated off in JSON mode rather than swallowing "}" into an
        // (empty) block scalar body.
        try {
            parseJson( """
                { "a": |
                }""" );
            fail("expected a JSON parse error for a bare '|' token");
        } catch (ConfigException.Parse expected) {
            // expected
        }
    }

    @Test
    public void jsonModeQuotedPipeUnaffected() {
        Config c = parseJson( """
            { "a": "|" }""" );
        assertEquals("|", c.getString("a"));
    }
}
