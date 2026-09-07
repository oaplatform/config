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
 * Tests for the fork-only YAML-style anchor ("&name"), alias ("*name"), and merge-key
 * ("<<: *name") syntax (see HOCON.md, "Anchors, aliases, and merge keys (YAML-style)"). Not
 * part of upstream lightbend/config.
 */
public class AnchorAliasParserTest {

    private Config parseConf(String s) {
        return ConfigFactory.parseString(s,
                ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF)).resolve();
    }

    private Config parseJson(String s) {
        return ConfigFactory.parseString(s,
                ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON)).resolve();
    }

    @Test
    public void basicScalarAnchorAndAlias() {
        Config c = parseConf(
            """
                a: &x 42
                b = *x
                """ );
        assertEquals(42, c.getInt("a"));
        assertEquals(42, c.getInt("b"));
    }

    @Test
    public void anchorOnObjectAndAliasReuse() {
        Config c = parseConf(
            """
                defaults: &base
                  timeout: 30
                  retries: 3

                other: *base
                """ );
        assertEquals(30, c.getInt("defaults.timeout"));
        assertEquals(3, c.getInt("defaults.retries"));
        assertEquals(30, c.getInt("other.timeout"));
        assertEquals(3, c.getInt("other.retries"));
    }

    @Test
    public void anchorOnArrayAndAliasReuse() {
        Config c = parseConf(
            """
                a: &nums [1, 2, 3]
                b = *nums
                """ );
        assertEquals(java.util.Arrays.asList(1, 2, 3), c.getIntList("a"));
        assertEquals(java.util.Arrays.asList(1, 2, 3), c.getIntList("b"));
    }

    @Test
    public void mergeKeyBasic() {
        Config c = parseConf(
            """
                defaults: &base_settings
                  timeout: 30
                  retries: 3

                production:
                  <<: *base_settings
                  host: "example.com"
                """ );
        assertEquals(30, c.getInt("production.timeout"));
        assertEquals(3, c.getInt("production.retries"));
        assertEquals("example.com", c.getString("production.host"));
    }

    @Test
    public void explicitKeyBeatsMergedKeyRegardlessOfOrder() {
        Config c = parseConf(
            """
                defaults: &base
                  timeout: 30

                production:
                  timeout: 99
                  <<: *base
                """ );
        // explicit "timeout: 99" wins even though "<<:" appears after it
        assertEquals(99, c.getInt("production.timeout"));
    }

    @Test
    public void mergeKeyIsRecursiveIntoNestedObjects() {
        Config c = parseConf(
            """
                defaults: &base
                  db:
                    host: a
                    port: 5432

                prod:
                  <<: *base
                  db:
                    host: b
                """ );
        // recursive (withFallback-based) merge: prod.db.port survives from the anchor even
        // though prod.db.host is locally overridden - a deliberate divergence from strict
        // (shallow) YAML merge-key semantics, see HOCON.md
        assertEquals("b", c.getString("prod.db.host"));
        assertEquals(5432, c.getInt("prod.db.port"));
    }

    @Test
    public void mergeKeyListPriorityOrder() {
        Config c = parseConf(
            """
                a: &a
                  x: 1
                  y: 1
                b: &b
                  y: 2
                  z: 2

                merged:
                  <<: [*a, *b]
                """ );
        assertEquals(1, c.getInt("merged.x"));
        // *a is listed first, so it wins over *b for the shared key "y"
        assertEquals(1, c.getInt("merged.y"));
        assertEquals(2, c.getInt("merged.z"));
    }

    @Test
    public void anchorNestedInsideBlockObjectField() {
        Config c = parseConf(
            """
                a:
                  b: &x 1
                  c = *x
                """ );
        assertEquals(1, c.getInt("a.b"));
        assertEquals(1, c.getInt("a.c"));
    }

    @Test
    public void anchorNestedInsideBlockArrayElement() {
        Config c = parseConf(
            """
                arr:
                  - k1: &x 1
                  - k2 = *x
                """ );
        java.util.List<? extends Config> list = c.getConfigList("arr");
        assertEquals(1, list.get(0).getInt("k1"));
        assertEquals(1, list.get(1).getInt("k2"));
    }

    @Test
    public void aliasAsWholeArrayElement() {
        Config c = parseConf(
            """
                x: &v 7
                arr = [1, *v, 3]
                """ );
        assertEquals(java.util.Arrays.asList(1, 7, 3), c.getIntList("arr"));
    }

    @Test
    public void anchorOnArrayElement() {
        Config c = parseConf(
            """
                arr = [&v 1, 2, 3]
                b = *v
                """ );
        assertEquals(java.util.Arrays.asList(1, 2, 3), c.getIntList("arr"));
        assertEquals(1, c.getInt("b"));
    }

    @Test
    public void undefinedAliasIsAnError() {
        try {
            parseConf(
                """
                    a = *nope
                    """ );
            fail("expected a parse error for an undefined anchor reference");
        } catch (ConfigException.Parse expected) {
            // expected
        }
    }

    @Test
    public void nonObjectMergeKeyValueIsAnError() {
        try {
            parseConf(
                """
                    a:
                      <<: 5
                    """ );
            fail("expected a parse error for a non-object merge key value");
        } catch (ConfigException.Parse expected) {
            // expected
        }
    }

    @Test
    public void redefiningAnchorNameLastDefinitionWins() {
        Config c = parseConf(
            """
                a: &x 1
                b: &x 2
                c = *x
                """ );
        assertEquals(2, c.getInt("c"));
    }

    @Test
    public void jsonModeUnaffected() {
        try {
            parseJson( """
                { "a": &x 1 }""" );
            fail("expected a JSON parse error for a bare '&' token");
        } catch (ConfigException.Parse expected) {
            // expected
        }
    }
}
