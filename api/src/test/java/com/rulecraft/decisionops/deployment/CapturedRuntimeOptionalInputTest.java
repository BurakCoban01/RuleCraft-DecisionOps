package com.rulecraft.decisionops.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rulecraft.decisionops.expressions.language.ExpressionLanguage;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CapturedRuntimeOptionalInputTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CapturedDecisionRuntime runtime = new CapturedDecisionRuntime(mapper,
            new ExpressionLanguage(null, null, null, mapper));
    private static final String ARTIFACT = """
            {"content":{"service":{},"types":{},
              "inputs":{"unused":{"type":"NUMBER","required":false}},
              "outputs":{"approved":{"type":"BOOLEAN","required":true}},
              "nodes":{
                "input":{"kind":"INPUT","serviceFieldKey":"unused","ports":{"OUTPUT:unused":{"type":"NUMBER","required":false,"position":0}}},
                "decision":{"kind":"DECISION","name":"Constant result","ports":{"INPUT:unused":{"type":"NUMBER","required":false,"position":0},"OUTPUT:approved":{"type":"BOOLEAN","required":true,"position":0}}},
                "output":{"kind":"OUTPUT","serviceFieldKey":"approved","ports":{"INPUT:approved":{"type":"BOOLEAN","required":true,"position":0}}}},
              "edges":{"input.unused -> decision.unused":true,"decision.approved -> output.approved":true},
              "implementations":{"decision":{"kind":"EXPRESSION","source":"true","language":"RCEL-1"}}}}
            """;

    @Test
    void missingOptionalInputDoesNotBlockAResolvedDependency() {
        var result = runtime.evaluate(ARTIFACT, Map.of());
        assertThat(result.outputs().get("approved").asBoolean()).isTrue();
        assertThat(result.decisions()).hasSize(1);
        assertThat(result.decisions().getFirst().inputKeys()).isEmpty();
    }

    @Test
    void aPresentOptionalInputStillRequiresItsDeclaredType() {
        assertThatThrownBy(() -> runtime.evaluate(ARTIFACT, Map.of("unused", mapper.valueToTree("invalid"))))
                .isInstanceOf(DomainProblemException.class)
                .extracting("code").isEqualTo("RC-DEPLOY-INPUT-TYPE");
    }
}
