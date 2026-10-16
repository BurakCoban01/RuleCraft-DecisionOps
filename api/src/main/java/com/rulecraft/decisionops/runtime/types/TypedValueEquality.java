package com.rulecraft.decisionops.runtime.types;

import tools.jackson.databind.JsonNode;

/** Equality for typed JSON values, independent of numeric representation and object field order. */
public final class TypedValueEquality {
    private TypedValueEquality() {}

    public static boolean equal(JsonNode left, JsonNode right) {
        if (left == null || right == null) return left == right;
        if (left.isNumber() && right.isNumber()) {
            return left.decimalValue().compareTo(right.decimalValue()) == 0;
        }
        if (left.isArray() && right.isArray()) {
            if (left.size() != right.size()) return false;
            for (int index = 0; index < left.size(); index++) {
                if (!equal(left.get(index), right.get(index))) return false;
            }
            return true;
        }
        if (left.isObject() && right.isObject()) {
            return left.size() == right.size() && left.propertyNames().stream()
                    .allMatch(key -> right.has(key) && equal(left.get(key), right.get(key)));
        }
        return left.equals(right);
    }
}
