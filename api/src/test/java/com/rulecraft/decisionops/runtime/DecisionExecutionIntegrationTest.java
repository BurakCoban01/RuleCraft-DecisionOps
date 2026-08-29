package com.rulecraft.decisionops.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rulecraft.decisionops.catalog.CatalogService;
import com.rulecraft.decisionops.catalog.CatalogService.*;
import com.rulecraft.decisionops.catalog.domain.BuiltinType;
import com.rulecraft.decisionops.expressions.DecisionExpressionService;
import com.rulecraft.decisionops.expressions.DecisionExpressionService.ExpressionCommand;
import com.rulecraft.decisionops.graph.DecisionGraphService;
import com.rulecraft.decisionops.graph.DecisionGraphService.*;
import com.rulecraft.decisionops.graph.domain.*;
import com.rulecraft.decisionops.identity.domain.*;
import com.rulecraft.decisionops.identity.persistence.UserAccountRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.ProjectService;
import com.rulecraft.decisionops.projects.domain.ProjectRole;
import com.rulecraft.decisionops.runtime.DecisionExecutionService.EvaluationCommand;
import com.rulecraft.decisionops.tables.DecisionTableService;
import com.rulecraft.decisionops.tables.DecisionTableService.*;
import com.rulecraft.decisionops.tables.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class DecisionExecutionIntegrationTest {
    @Autowired DecisionExecutionService executions;
    @Autowired DecisionExpressionService expressions;
    @Autowired DecisionTableService tables;
    @Autowired DecisionGraphService graphs;
    @Autowired CatalogService catalog;
    @Autowired ProjectService projects;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired Clock clock;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    RuleCraftPrincipal admin;

    @BeforeEach void actor(){admin=new RuleCraftPrincipal(users.findForLogin("acme-rules","admin@example.test").orElseThrow(),"acme-rules");}

    @Test void evaluatesExpressionThenFirstTableWithVersionQualifiedResults(){
        var fixture=fixture();
        var auditBefore=jdbc.sql("select count(*) from audit_event where action = 'DECISION_SANDBOX_EVALUATED'").query(Long.class).single();
        var approved=executions.evaluate(admin,fixture.projectId(),fixture.serviceId(),command("750"));
        assertThat(approved.graphVersion()).isEqualTo(fixture.graphVersion());
        assertThat(approved.outputs().get("decision").asString()).isEqualTo("APPROVE");
        assertThat(approved.decisions()).extracting("nodeKey").containsExactly("threshold","outcome");
        assertThat(approved.decisions()).extracting("implementation").containsExactly("EXPRESSION","TABLE");
        assertThat(approved.decisions()).allSatisfy(result->assertThat(result.implementationVersion()).isPositive());
        assertThat(approved.decisions().get(1).matchedRuleId()).isNotNull();

        var declined=executions.evaluate(admin,fixture.projectId(),fixture.serviceId(),command("500"));
        assertThat(declined.outputs().get("decision").asString()).isEqualTo("DECLINE");
        assertThat(jdbc.sql("select count(*) from audit_event where action = 'DECISION_SANDBOX_EVALUATED'").query(Long.class).single()).isEqualTo(auditBefore+2);
    }

    @Test void rejectsMissingUnknownAndMismatchedInputs(){
        var fixture=fixture();
        assertCode("RC-EXEC-INPUT-MISSING",()->executions.evaluate(admin,fixture.projectId(),fixture.serviceId(),new EvaluationCommand(Map.of())));
        assertCode("RC-EXEC-INPUT-UNKNOWN",()->executions.evaluate(admin,fixture.projectId(),fixture.serviceId(),new EvaluationCommand(Map.of("score",mapper.readTree("700"),"extra",mapper.readTree("true")))));
        assertCode("RC-EXEC-INPUT-TYPE",()->executions.evaluate(admin,fixture.projectId(),fixture.serviceId(),new EvaluationCommand(Map.of("score",mapper.readTree("\"high\"")))));
        assertCode("RC-EXEC-INPUT-LIMIT",()->executions.evaluate(admin,fixture.projectId(),fixture.serviceId(),new EvaluationCommand(Map.of("score",mapper.valueToTree("x".repeat(12_001))))));
    }

    @Test void exposesStableRuntimeAndNoMatchFailures(){
        var fixture=fixture();
        var expression=expressions.find(admin,fixture.projectId(),fixture.serviceId(),fixture.thresholdNodeId()).orElseThrow();
        expressions.update(admin,fixture.projectId(),fixture.serviceId(),fixture.thresholdNodeId(),new ExpressionCommand(expression.version(),"10 / (score - score) > 1"));
        assertCode("RC-EXEC-DIVIDE-BY-ZERO",()->executions.evaluate(admin,fixture.projectId(),fixture.serviceId(),command("700")));

        var current=expressions.find(admin,fixture.projectId(),fixture.serviceId(),fixture.thresholdNodeId()).orElseThrow();
        expressions.update(admin,fixture.projectId(),fixture.serviceId(),fixture.thresholdNodeId(),new ExpressionCommand(current.version(),"score >= 700"));
        var table=tables.find(admin,fixture.projectId(),fixture.serviceId(),fixture.outcomeNodeId()).orElseThrow();
        tables.update(admin,fixture.projectId(),fixture.serviceId(),fixture.outcomeNodeId(),new TableCommand(table.version(),HitPolicy.UNIQUE,List.of(equalsRule(fixture.approvedPortId(),"true","\"APPROVE\""))));
        assertCode("RC-EXEC-NO-MATCH",()->executions.evaluate(admin,fixture.projectId(),fixture.serviceId(),command("500")));
    }

    @Test void viewerCanEvaluateAndOutsiderCannotDiscoverProject(){
        var fixture=fixture();var now=Instant.now(clock);
        var viewerAccount=users.save(new UserAccount(admin.tenantId(),"runtime-viewer"+UUID.randomUUID()+"@example.test","Rita Runtime Viewer",passwordEncoder.encode("CorrectHorseBattery90!"),TenantRole.MEMBER,now));
        projects.upsertMember(admin,fixture.projectId(),viewerAccount.getId(),ProjectRole.VIEWER);
        var viewer=new RuleCraftPrincipal(viewerAccount,"acme-rules");
        assertThat(executions.evaluate(viewer,fixture.projectId(),fixture.serviceId(),command("750")).outputs()).containsKey("decision");
        var outsiderAccount=users.save(new UserAccount(admin.tenantId(),"runtime-outsider"+UUID.randomUUID()+"@example.test","Owen Outsider",passwordEncoder.encode("CorrectHorseBattery91!"),TenantRole.MEMBER,now));
        var outsider=new RuleCraftPrincipal(outsiderAccount,"acme-rules");
        assertCode("RC-PROJECT-NOT-FOUND",()->executions.evaluate(outsider,fixture.projectId(),fixture.serviceId(),command("750")));
    }

    private Fixture fixture(){
        var project=projects.create(admin,key("P"),"Execution sandbox",null);
        var service=catalog.createService(admin,project.id(),new DecisionServiceCommand(key("SVC"),"Eligibility runtime",null,List.of(new FieldCommand("score","Score",builtin(BuiltinType.NUMBER),true)),List.of(new FieldCommand("decision","Decision",builtin(BuiltinType.TEXT),true)),null));
        var graph=graphs.initialize(admin,project.id(),service.id());
        var thresholdId=UUID.randomUUID();var score=port(GraphPortDirection.INPUT,"score",BuiltinType.NUMBER,0);var approvedOut=port(GraphPortDirection.OUTPUT,"approved",BuiltinType.BOOLEAN,0);
        var outcomeId=UUID.randomUUID();var approvedIn=port(GraphPortDirection.INPUT,"approved",BuiltinType.BOOLEAN,0);var decisionOut=port(GraphPortDirection.OUTPUT,"decision",BuiltinType.TEXT,0);
        var inputNode=graph.nodes().stream().filter(node->node.kind()==GraphNodeKind.INPUT).findFirst().orElseThrow();var inputOut=inputNode.ports().getFirst();
        var outputNode=graph.nodes().stream().filter(node->node.kind()==GraphNodeKind.OUTPUT).findFirst().orElseThrow();var outputIn=outputNode.ports().getFirst();
        var nodes=new ArrayList<NodeCommand>();graph.nodes().forEach(node->nodes.add(command(node)));
        nodes.add(new NodeCommand(thresholdId,"threshold","Threshold",GraphNodeKind.DECISION,null,260,100,List.of(score,approvedOut)));
        nodes.add(new NodeCommand(outcomeId,"outcome","Outcome",GraphNodeKind.DECISION,null,520,100,List.of(approvedIn,decisionOut)));
        var edges=List.of(edge(inputOut.id(),score.id()),edge(approvedOut.id(),approvedIn.id()),edge(decisionOut.id(),outputIn.id()));
        var savedGraph=graphs.update(admin,project.id(),service.id(),new GraphCommand(graph.version(),nodes,edges));
        var expression=expressions.initialize(admin,project.id(),service.id(),thresholdId);
        expressions.update(admin,project.id(),service.id(),thresholdId,new ExpressionCommand(expression.version(),"score >= 700"));
        var table=tables.initialize(admin,project.id(),service.id(),outcomeId);
        tables.update(admin,project.id(),service.id(),outcomeId,new TableCommand(table.version(),HitPolicy.FIRST,List.of(equalsRule(approvedIn.id(),"true","\"APPROVE\""),anyRule(approvedIn.id(),"\"DECLINE\""))));
        return new Fixture(project.id(),service.id(),thresholdId,outcomeId,approvedIn.id(),savedGraph.version());
    }

    private EvaluationCommand command(String score){return new EvaluationCommand(Map.of("score",mapper.readTree(score)));}
    private CatalogService.TypeRefCommand builtin(BuiltinType type){return new CatalogService.TypeRefCommand(type,null);}
    private PortCommand port(GraphPortDirection direction,String key,BuiltinType type,int position){return new PortCommand(UUID.randomUUID(),direction,key,key,new DecisionGraphService.TypeRefCommand(type,null),true,position);}
    private NodeCommand command(GraphNodeView node){return new NodeCommand(node.id(),node.nodeKey(),node.name(),node.kind(),node.serviceFieldKey(),node.positionX(),node.positionY(),node.ports().stream().map(port->new PortCommand(port.id(),port.direction(),port.portKey(),port.label(),port.type(),port.required(),port.position())).toList());}
    private EdgeCommand edge(UUID source,UUID target){return new EdgeCommand(UUID.randomUUID(),source,target);}
    private static RuleCommand equalsRule(UUID portId,String input,String output){return new RuleCommand(UUID.randomUUID(),true,null,List.of(new InputEntryCommand(UUID.randomUUID(),portId,InputOperator.EQUALS,input)),output);}
    private static RuleCommand anyRule(UUID portId,String output){return new RuleCommand(UUID.randomUUID(),true,null,List.of(new InputEntryCommand(UUID.randomUUID(),portId,InputOperator.ANY,null)),output);}
    private String key(String prefix){return prefix+UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase();}
    private void assertCode(String code,Runnable action){assertThatThrownBy(action::run).isInstanceOf(DomainProblemException.class).extracting("code").isEqualTo(code);}
    private record Fixture(UUID projectId,UUID serviceId,UUID thresholdNodeId,UUID outcomeNodeId,UUID approvedPortId,long graphVersion){}
}
