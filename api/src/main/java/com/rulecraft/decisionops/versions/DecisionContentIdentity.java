package com.rulecraft.decisionops.versions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Object member order is not a decision change; array order remains significant. */
final class DecisionContentIdentity {
    private DecisionContentIdentity() {}

    static String fingerprint(ObjectMapper mapper, Object content, JsonNode previousContent, String previousFingerprint) {
        JsonNode tree = mapper.valueToTree(content);
        // Existing snapshots and serving-artifact identities are immutable, including legacy hashes.
        if (previousFingerprint != null && tree.equals(previousContent)) return previousFingerprint;
        try {
            var bytes = mapper.writeValueAsString(canonical(mapper, tree)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JacksonException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Decision identity could not be computed.", failure);
        }
    }

    private static JsonNode canonical(ObjectMapper mapper, JsonNode value) {
        if (value.isObject()) {
            var result = mapper.createObjectNode();
            value.propertyNames().stream().sorted().forEach(key -> result.set(key, canonical(mapper, value.get(key))));
            return result;
        }
        if (value.isArray()) {
            var result = mapper.createArrayNode();
            value.values().forEach(item -> result.add(canonical(mapper, item)));
            return result;
        }
        return value;
    }
}
