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
 * Tests for the fork-only YAML-style block-object syntax (see HOCON.md,
 * "Block objects (YAML-style)"). Not part of upstream lightbend/config.
 */
public class BlockObjectParserTest {

    private Config parseConf(String s) {
        return ConfigFactory.parseString(s,
                ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF)).resolve();
    }

    private Config parseJson(String s) {
        return ConfigFactory.parseString(s,
                ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON)).resolve();
    }

    @Test
    public void userExample() {
        Config c = parseConf(
            """
                a:
                  v:
                    c = 5
                    d = 6
                """ );
        assertEquals(5, c.getInt("a.v.c"));
        assertEquals(6, c.getInt("a.v.d"));
    }

    @Test
    public void simpleOneLevelBlockObject() {
        Config c = parseConf(
            """
                a:
                  b = 1
                  c = 2
                """ );
        assertEquals(1, c.getInt("a.b"));
        assertEquals(2, c.getInt("a.c"));
    }

    @Test
    public void deeperNesting() {
        Config c = parseConf(
            """
                a:
                  b:
                    c:
                      d = 1
                      e = 2
                    f = 3
                  g = 4
                """ );
        assertEquals(1, c.getInt("a.b.c.d"));
        assertEquals(2, c.getInt("a.b.c.e"));
        assertEquals(3, c.getInt("a.b.f"));
        assertEquals(4, c.getInt("a.g"));
    }

    @Test
    public void blockObjectNestedInsideDashArrayElementField() {
        Config c = parseConf(
            """
                arr:
                  - k1 = v
                    k2:
                      x = 1
                      y = 2
                  - k3 = v3
                """ );

        java.util.List<? extends Config> list = c.getConfigList("arr");
        assertEquals(2, list.size());
        assertEquals("v", list.get(0).getString("k1"));
        assertEquals(1, list.get(0).getInt("k2.x"));
        assertEquals(2, list.get(0).getInt("k2.y"));
        assertEquals("v3", list.get(1).getString("k3"));
    }

    @Test
    public void dashArrayNestedInsideBlockObjectField() {
        Config c = parseConf(
            """
                a:
                  items:
                    - x
                    - y
                  b = 1
                """ );
        assertEquals(java.util.Arrays.asList("x", "y"), c.getStringList("a.items"));
        assertEquals(1, c.getInt("a.b"));
    }

    @Test
    public void mixedSeparators() {
        Config c = parseConf(
            """
                a:
                  b: 1
                  c = 2
                  d { x: 1 }
                """ );
        assertEquals(1, c.getInt("a.b"));
        assertEquals(2, c.getInt("a.c"));
        assertEquals(1, c.getInt("a.d.x"));
    }

    @Test
    public void sameLineValueStillWorks() {
        Config c = parseConf( """
            a: foo
            """ );
        assertEquals("foo", c.getString("a"));
    }

    @Test
    public void shallowerSiblingLineTerminatesAndReturnsControl() {
        Config c = parseConf(
            """
                a:
                  b:
                    c = 1
                    d = 2

                  e = 3
                f = 4
                """ );
        assertEquals(1, c.getInt("a.b.c"));
        assertEquals(2, c.getInt("a.b.d"));
        assertEquals(3, c.getInt("a.e"));
        assertEquals(4, c.getInt("f"));
    }

    @Test
    public void inconsistentIndentationIsAnError() {
        try {
            parseConf(
                """
                    a:
                      b = 1
                        c = 2
                    """ );
            fail("expected a parse error for inconsistent indentation");
        } catch (ConfigException.Parse expected) {
            // expected
        }
    }

    @Test
    public void jsonModeUnaffected() {
        Config c = parseJson( """
            { "a": { "b": 1 } }""" );
        assertEquals(1, c.getInt("a.b"));
    }
}
