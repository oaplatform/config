package com.typesafe.config.impl;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for the fork-only YAML-style block-array syntax (see HOCON.md,
 * "Block arrays (YAML-style)"). Not part of upstream lightbend/config.
 */
public class BlockArrayParserTest {

    private Config parseConf(String s) {
        return ConfigFactory.parseString(s,
                ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF)).resolve();
    }

    private Config parseJson(String s) {
        return ConfigFactory.parseString(s,
                ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON)).resolve();
    }

    @Test
    public void simpleStringArray() {
        Config c = parseConf(
            """
                my_array:
                  - item1
                  - item2
                """ );
        assertEquals(java.util.Arrays.asList("item1", "item2"), c.getStringList("my_array"));
    }

    @Test
    public void objectArrayFromUserExample() {
        Config c = parseConf(
            """
                my_array:
                  - item1
                  - item2
                my_object_array:
                  - field1.a = fg
                    field2 = 3
                """ );

        assertEquals(java.util.Arrays.asList("item1", "item2"), c.getStringList("my_array"));

        List<? extends Config> objs = c.getConfigList("my_object_array");
        assertEquals(1, objs.size());
        assertEquals("fg", objs.get(0).getString("field1.a"));
        assertEquals(3, objs.get(0).getInt("field2"));
    }

    @Test
    public void multipleElementsWithMultipleContinuationFields() {
        Config c = parseConf(
            """
                arr:
                  - a = 1
                    b = 2
                  - a = 3
                    b = 4
                """ );

        List<? extends Config> list = c.getConfigList("arr");
        assertEquals(2, list.size());
        assertEquals(1, list.get(0).getInt("a"));
        assertEquals(2, list.get(0).getInt("b"));
        assertEquals(3, list.get(1).getInt("a"));
        assertEquals(4, list.get(1).getInt("b"));
    }

    @Test
    public void dashArrayNestedInsideBraces() {
        Config c = parseConf(
            """
                outer {
                  arr:
                    - a
                    - b
                }
                """ );
        assertEquals(java.util.Arrays.asList("a", "b"), c.getStringList("outer.arr"));
    }

    @Test
    public void dashArrayNestedAsFieldValueInsideElement() {
        Config c = parseConf(
            """
                arr:
                  - k1 = v
                    k2:
                      - x
                      - y

                  - k3 = v3
                """ );

        List<? extends Config> list = c.getConfigList("arr");
        assertEquals(2, list.size());
        assertEquals("v", list.get(0).getString("k1"));
        assertEquals("v3", list.get(1).getString("k3"));
        assertEquals(java.util.Arrays.asList("x", "y"), list.get(0).getStringList("k2"));
    }

    @Test
    public void normalBracketArrayElsewhereUnaffected() {
        Config c = parseConf(
            """
                brackets: [1, 2, 3]
                dashes:
                  - a
                  - b
                """ );
        assertEquals(java.util.Arrays.asList(1, 2, 3), c.getIntList("brackets"));
        assertEquals(java.util.Arrays.asList("a", "b"), c.getStringList("dashes"));
    }

    @Test
    public void mixedTypeDashLines() {
        Config c = parseConf(
            """
                arr:
                  - 42
                  - true
                  - "quoted string"
                """ );
        List<? extends Object> raw = c.getAnyRefList("arr");
        assertEquals(42, ((Number) raw.get(0)).intValue());
        assertEquals(Boolean.TRUE, raw.get(1));
        assertEquals("quoted string", raw.get(2));
    }

    @Test
    public void noDashMeansNoBlockArray() {
        Config c = parseConf(
            """
                key: value
                other_field = 1
                """ );
        assertEquals("value", c.getString("key"));
        assertEquals(1, c.getInt("other_field"));
    }

    @Test
    public void jsonModeUnaffectedByDashLines() {
        // a JSON string value that happens to start with '-' must parse as a plain string,
        // never trigger block-array detection (which is CONF-only anyway)
        Config c = parseJson( """
            { "key": "-not-a-marker" }""" );
        assertEquals("-not-a-marker", c.getString("key"));
    }

    @Test
    public void dashWithoutSpaceIsNotAMarker() {
        // "-item1" (no space after the dash) on the next line is not a block-array marker,
        // so it falls through to ordinary (pre-existing) value concatenation, same as stock
        // HOCON's "a value may start on the next line" + adjacent-token-concatenation rules.
        Config c = parseConf(
            """
                a: foo
                b:
                -item1
                """ );
        assertEquals("foo", c.getString("a"));
        assertEquals("-item1", c.getString("b"));
    }

    @Test
    public void negativeNumberNextLineIsNotAMarker() {
        Config c = parseConf(
            """
                a: 1
                b: -5
                """ );
        assertEquals(1, c.getInt("a"));
        assertEquals(-5, c.getInt("b"));
    }

    @Test
    public void inconsistentIndentationBetweenDashAndContentIsAnError() {
        // continuation-looking line indented strictly between the dash column (2) and the
        // content column (4, "field1.a" starts there) - undefined, must be a parse error
        try {
            parseConf(
                """
                    arr:
                      - field1.a = fg
                       field2 = 3
                    """ );
            fail("expected a parse error for inconsistent indentation");
        } catch (ConfigException.Parse expected) {
            // expected
        }
    }

    @Test
    public void dashAtWrongColumnIsAnError() {
        // a '- ' marker at a column other than the established dashColumn (nested/mismatched
        // dash) is undefined behavior for v1, must be a parse error
        try {
            parseConf(
                """
                    arr:
                      - a
                        - b
                    """ );
            fail("expected a parse error for a dash at an unexpected column");
        } catch (ConfigException.Parse expected) {
            // expected
        }
    }

    @Test
    public void singleElementArray() {
        Config c = parseConf( """
            arr:
              - only
            """ );
        assertEquals(java.util.Collections.singletonList("only"), c.getStringList("arr"));
    }

    @Test
    public void blankLinesBetweenElementsAreAllowed() {
        Config c = parseConf(
            """
                arr:
                  - a
                
                  - b
                """ );
        assertEquals(java.util.Arrays.asList("a", "b"), c.getStringList("arr"));
    }

    @Test
    public void arrayAsLastContentWithNoTrailingNewline() {
        Config c = parseConf(
            """
                arr:
                  - a
                  - b""" );
        assertEquals(java.util.Arrays.asList("a", "b"), c.getStringList("arr"));
    }

    @Test
    public void bracketArrayAsDashElementValue() {
        Config c = parseConf(
            """
                arr:
                  - [1, 2]
                  - [3, 4]
                """ );
        List<? extends Object> raw = c.getAnyRefList("arr");
        assertEquals(java.util.Arrays.asList(1, 2), raw.get(0));
        assertEquals(java.util.Arrays.asList(3, 4), raw.get(1));
    }

    @Test
    public void inlineBraceObjectAsDashElementValue() {
        // distinct code path from "- key = value" block-mapping elements: here the whole
        // element is an ordinary value that happens to be a braced object literal.
        Config c = parseConf(
            """
                arr:
                  - { a: 1, b: 2 }
                  - { a: 3, b: 4 }
                """ );
        List<? extends Config> list = c.getConfigList("arr");
        assertEquals(2, list.size());
        assertEquals(1, list.get(0).getInt("a"));
        assertEquals(4, list.get(1).getInt("b"));
    }

    @Test
    public void tabIndentationWorksIfConsistent() {
        Config c = parseConf(
            """
                arr:
                \t- a
                \t- b
                """ );
        assertEquals(java.util.Arrays.asList("a", "b"), c.getStringList("arr"));
    }

    @Test
    public void mixedTabsAndSpacesCanMisalignColumns() {
        // documents a known limitation (see HOCON.md): indentation is a raw character count,
        // not tab-expanded, so a tab and a run of spaces occupying the same *visual* column
        // are not considered the same column by the parser.
        try {
            parseConf(
                """
                    arr:
                    \t- a
                      - b
                    """ );
            fail("expected a parse error when tab vs space indentation don't match by character count");
        } catch (ConfigException.Parse expected) {
            // expected
        }
    }

    @Test
    public void commentsOnDashLinesAndBetweenElements() {
        Config c = parseConf(
            """
                arr:
                  - item1 # trailing comment
                  # comment-only line between elements
                  - item2
                """ );
        assertEquals(java.util.Arrays.asList("item1", "item2"), c.getStringList("arr"));
    }

    // --- regression coverage for ordinary (non-block-array) object-field parsing, since
    // parseSingleField was extracted out of parseObject as part of this feature ---

    @Test
    public void duplicateObjectKeysStillMerge() {
        Config c = parseConf( """
            a: { x: 1 }
            a: { y: 2 }
            """ );
        assertEquals(1, c.getInt("a.x"));
        assertEquals(2, c.getInt("a.y"));
    }

    @Test
    public void jsonDuplicateKeysStillError() {
        try {
            parseJson( """
                { "a": 1, "a": 2 }""" );
            fail("expected a parse error for duplicate JSON keys");
        } catch (ConfigException.Parse expected) {
            // expected
        }
    }

    @Test
    public void plusEqualsStillAppends() {
        Config c = parseConf( """
            a = [1]
            a += 2
            """ );
        assertEquals(java.util.Arrays.asList(1, 2), c.getIntList("a"));
    }

    @Test
    public void omittedBraceObjectValueStillWorks() {
        Config c = parseConf( """
            a { b: 1 }
            """ );
        assertEquals(1, c.getInt("a.b"));
    }
}
