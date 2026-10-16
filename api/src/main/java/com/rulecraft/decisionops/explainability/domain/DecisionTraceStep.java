package com.rulecraft.decisionops.explainability.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "decision_trace_step")
public class DecisionTraceStep {
    @Id private UUID id;
    @Column(name = "trace_id", nullable = false, insertable = false, updatable = false) private UUID traceId;
    @Column(nullable = false) private int position;
    @Column(name = "node_key", nullable = false, length = 120) private String nodeKey;
    @Column(name = "node_name", nullable = false, length = 160) private String nodeName;
    @Column(nullable = false, length = 16) private String implementation;
    @Column(name = "hit_policy", length = 16) private String hitPolicy;
    @Column(name = "matched_rule_id", length = 80) private String matchedRuleId;
    @Column(name = "input_keys_json", nullable = false, columnDefinition = "text") private String inputKeysJson;
    @Column(name = "result_json", nullable = false, columnDefinition = "text") private String resultJson;

    protected DecisionTraceStep() {}

    public DecisionTraceStep(int position, String nodeKey, String nodeName, String implementation,
            String hitPolicy, String matchedRuleId, String inputKeysJson, String resultJson) {
        this.id = UUID.randomUUID();
        this.position = position;
        this.nodeKey = nodeKey;
        this.nodeName = nodeName;
        this.implementation = implementation;
        this.hitPolicy = hitPolicy;
        this.matchedRuleId = matchedRuleId;
        this.inputKeysJson = inputKeysJson;
        this.resultJson = resultJson;
    }

    public int getPosition() { return position; }
    public String getNodeKey() { return nodeKey; }
    public String getNodeName() { return nodeName; }
    public String getImplementation() { return implementation; }
    public String getHitPolicy() { return hitPolicy; }
    public String getMatchedRuleId() { return matchedRuleId; }
    public String getInputKeysJson() { return inputKeysJson; }
    public String getResultJson() { return resultJson; }
}
