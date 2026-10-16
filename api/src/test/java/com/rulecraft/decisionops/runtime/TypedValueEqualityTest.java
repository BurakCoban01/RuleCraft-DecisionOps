package com.rulecraft.decisionops.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.rulecraft.decisionops.runtime.types.TypedValueEquality;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TypedValueEqualityTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void numericScaleDoesNotChangeEquality() {
        assertThat(TypedValueEquality.equal(mapper.readTree("700"), mapper.readTree("700.0"))).isTrue();
        assertThat(TypedValueEquality.equal(mapper.readTree("700.01"), mapper.readTree("700"))).isFalse();
        assertThat(TypedValueEquality.equal(mapper.readTree("\"700\""), mapper.readTree("700"))).isFalse();
    }

    @Test
    void nestedObjectValuesIgnoreFieldOrderAndNumericRepresentation() {
        assertThat(TypedValueEquality.equal(mapper.readTree("{\"a\":[1,2],\"b\":true}"),
                mapper.readTree("{\"b\":true,\"a\":[1.0,2.00]}"))).isTrue();
        assertThat(TypedValueEquality.equal(mapper.readTree("{\"a\":1}"),
                mapper.readTree("{\"b\":1}"))).isFalse();
    }

    @Test
    void arraysRemainOrderedAndMissingDiffersFromJsonNull() {
        assertThat(TypedValueEquality.equal(mapper.readTree("[1,2]"), mapper.readTree("[2,1]"))).isFalse();
        assertThat(TypedValueEquality.equal(null, mapper.readTree("null"))).isFalse();
        assertThat(TypedValueEquality.equal(mapper.readTree("null"), mapper.readTree("null"))).isTrue();
    }
}
