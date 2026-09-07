package com.typesafe.config.impl;

import java.util.Collection;

// YAML-style anchor tag ("&name value"), see HOCON.md "Anchors, aliases, and merge keys
// (YAML-style)". Fork-only extension. Wraps whatever value node the anchor tags (scalar,
// object, array, block-array, block-object, block-scalar, or even another alias); tagging is
// purely a side effect recorded by ConfigParser at resolution time (see ConfigParser.parseValue),
// so at the AST level this is just a thin pass-through wrapper around innerValue.
final class ConfigNodeAnchoredValue extends AbstractConfigNodeValue {
    final private String anchorName;
    final private AbstractConfigNodeValue innerValue;

    ConfigNodeAnchoredValue(String anchorName, AbstractConfigNodeValue innerValue) {
        this.anchorName = anchorName;
        this.innerValue = innerValue;
    }

    String anchorName() {
        return anchorName;
    }

    AbstractConfigNodeValue innerValue() {
        return innerValue;
    }

    @Override
    protected Collection<Token> tokens() {
        return innerValue.tokens();
    }
}
