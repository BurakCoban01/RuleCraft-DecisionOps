package com.rulecraft.decisionops.versions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DecisionContentIdentityTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test void ignoresNestedObjectMemberOrderAcrossSerializationOrders() {
        var first = mapper.readTree("{\"service\":{\"name\":\"Policy\",\"key\":\"LENDING\"},\"rules\":[{\"result\":true,\"when\":700}]}");
        var reordered = mapper.readTree("{\"rules\":[{\"when\":700,\"result\":true}],\"service\":{\"key\":\"LENDING\",\"name\":\"Policy\"}}");
        assertThat(DecisionContentIdentity.fingerprint(mapper, first, null, null))
                .isEqualTo(DecisionContentIdentity.fingerprint(mapper, reordered, null, null));
    }

    @Test void preservesRuleOrderAndBehavioralChanges() {
        var first = mapper.readTree("{\"rules\":[700,650]}");
        var reordered = mapper.readTree("{\"rules\":[650,700]}");
        var changed = mapper.readTree("{\"rules\":[700,620]}");
        var identity = DecisionContentIdentity.fingerprint(mapper, first, null, null);
        assertThat(identity).isNotEqualTo(DecisionContentIdentity.fingerprint(mapper, reordered, first, identity));
        assertThat(identity).isNotEqualTo(DecisionContentIdentity.fingerprint(mapper, changed, first, identity));
    }

    @Test void retainsLegacyIdentityOnlyForStructurallyEquivalentContent() {
        var previous = mapper.readTree("{\"name\":\"Policy\",\"threshold\":700}");
        var current = mapper.readTree("{\"threshold\":700,\"name\":\"Policy\"}");
        var legacyFingerprint = "a".repeat(64);
        assertThat(DecisionContentIdentity.fingerprint(mapper, current, previous, legacyFingerprint)).isEqualTo(legacyFingerprint);
        var changed = mapper.readTree("{\"threshold\":620,\"name\":\"Policy\"}");
        assertThat(DecisionContentIdentity.fingerprint(mapper, changed, previous, legacyFingerprint)).isNotEqualTo(legacyFingerprint);
    }
}
