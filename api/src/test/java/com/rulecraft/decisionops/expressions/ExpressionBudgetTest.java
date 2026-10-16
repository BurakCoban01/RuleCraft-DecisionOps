package com.rulecraft.decisionops.expressions;

import static org.assertj.core.api.Assertions.assertThat;

import com.rulecraft.decisionops.catalog.domain.BuiltinType;
import com.rulecraft.decisionops.expressions.language.ExpressionLanguage;
import com.rulecraft.decisionops.graph.DecisionGraphService.GraphPortView;
import com.rulecraft.decisionops.graph.DecisionGraphService.TypeRefCommand;
import com.rulecraft.decisionops.graph.domain.GraphPortDirection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ExpressionBudgetTest {
    private final ExpressionLanguage language = new ExpressionLanguage(null, null, null, new ObjectMapper());

    @Test
    void acceptsOrdinaryBoundedExpressions() {
        var result = language.evaluateCaptured("if true then 1 + 2 else 0", Map.of());
        assertThat(result.successful()).isTrue();
        assertThat(result.value().asInt()).isEqualTo(3);
    }

    @Test
    void rejectsDeepUnaryValidationWithStableDiagnostic() {
        var result = language.validate("not ".repeat(65) + "true", List.of(), output(BuiltinType.BOOLEAN));
        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting("code").containsExactly("RC-EXPR-DEPTH");
    }

    @Test
    void rejectsLeftDeepBinaryTreesBeforeRecursiveTypeChecking() {
        var result = language.validate("1 + ".repeat(128) + "1", List.of(), output(BuiltinType.NUMBER));
        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting("code").containsExactly("RC-EXPR-DEPTH");
    }

    @Test
    void capturedSyntaxUsesTheSameDepthBudget() {
        assertThat(language.validateCapturedSyntax("not ".repeat(65) + "true"))
                .extracting("code").containsExactly("RC-EXPR-DEPTH");
    }

    @Test
    void capturedEvaluationRejectsExcessDepthWithoutExecutingIt() {
        var result = language.evaluateCaptured("not ".repeat(65) + "true", Map.of());
        assertThat(result.successful()).isFalse();
        assertThat(result.issues()).extracting("code").containsExactly("RC-EXPR-DEPTH");
    }

    private GraphPortView output(BuiltinType type) {
        return new GraphPortView(UUID.randomUUID(), UUID.randomUUID(), GraphPortDirection.OUTPUT,
                "result", "Result", new TypeRefCommand(type, null), true, 0);
    }
}
