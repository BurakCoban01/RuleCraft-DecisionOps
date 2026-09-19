package com.rulecraft.decisionops.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rulecraft.decisionops.catalog.CatalogService;
import com.rulecraft.decisionops.catalog.CatalogService.DecisionServiceCommand;
import com.rulecraft.decisionops.catalog.CatalogService.EnumValueCommand;
import com.rulecraft.decisionops.catalog.CatalogService.FieldCommand;
import com.rulecraft.decisionops.catalog.CatalogService.TypeCommand;
import com.rulecraft.decisionops.catalog.domain.BuiltinType;
import com.rulecraft.decisionops.graph.DecisionGraphService.*;
import com.rulecraft.decisionops.graph.domain.*;
import com.rulecraft.decisionops.identity.domain.TenantRole;
import com.rulecraft.decisionops.identity.domain.UserAccount;
import com.rulecraft.decisionops.identity.persistence.UserAccountRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.ProjectService;
import com.rulecraft.decisionops.projects.domain.ProjectRole;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class DecisionGraphIntegrationTest {
    @Autowired DecisionGraphService graphs;
    @Autowired CatalogService catalog;
    @Autowired ProjectService projects;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired Clock clock;
    @Autowired JdbcClient jdbc;
    RuleCraftPrincipal admin;

    @BeforeEach void actor(){admin=new RuleCraftPrincipal(users.findForLogin("acme-rules","admin@example.test").orElseThrow(),"acme-rules");}

    @Test
    void initializesContractAndPersistsValidTypedDag(){
        var fixture=fixture();var graph=graphs.initialize(admin,fixture.projectId(),fixture.serviceId());
        assertThat(graph.validationStatus()).isEqualTo(GraphValidationStatus.INVALID);
        assertThat(graph.nodes()).extracting(GraphNodeView::kind).containsExactly(GraphNodeKind.INPUT,GraphNodeKind.OUTPUT);

        var input=graph.nodes().stream().filter(n->n.kind()==GraphNodeKind.INPUT).findFirst().orElseThrow();
        var output=graph.nodes().stream().filter(n->n.kind()==GraphNodeKind.OUTPUT).findFirst().orElseThrow();
        var decisionId=UUID.randomUUID();var decisionIn=port(decisionId,GraphPortDirection.INPUT,"score",BuiltinType.NUMBER,true);
        var decisionOut=port(decisionId,GraphPortDirection.OUTPUT,"approved",BuiltinType.BOOLEAN,true);
        var decision=new NodeCommand(decisionId,"eligibilityDecision","Eligibility decision",GraphNodeKind.DECISION,null,520,180,List.of(decisionIn,decisionOut));
        var nodes=new ArrayList<NodeCommand>();graph.nodes().forEach(n->nodes.add(command(n)));nodes.add(decision);
        var saved=graphs.update(admin,fixture.projectId(),fixture.serviceId(),new GraphCommand(graph.version(),nodes,List.of(
                new EdgeCommand(UUID.randomUUID(),input.ports().getFirst().id(),decisionIn.id()),
                new EdgeCommand(UUID.randomUUID(),decisionOut.id(),output.ports().getFirst().id()))));

        assertThat(saved.validationStatus()).isEqualTo(GraphValidationStatus.VALID);
        assertThat(saved.issues()).isEmpty();assertThat(saved.edges()).hasSize(2);
        assertThat(jdbc.sql("select count(*) from audit_event where action = 'DECISION_GRAPH_UPDATED'").query(Long.class).single()).isPositive();
        assertCode("RC-GRAPH-STALE",()->graphs.update(admin,fixture.projectId(),fixture.serviceId(),new GraphCommand(graph.version(),nodes,List.of())));

        var service=catalog.getService(admin,fixture.projectId(),fixture.serviceId());
        catalog.updateService(admin,fixture.projectId(),fixture.serviceId(),new DecisionServiceCommand(service.serviceKey(),service.name(),null,
                List.of(new FieldCommand("revisedScore","Revised score",new CatalogService.TypeRefCommand(BuiltinType.NUMBER,null),true)),service.outputs().stream()
                        .map(field->new FieldCommand(field.fieldKey(),field.label(),new CatalogService.TypeRefCommand(field.type().builtinType(),field.type().customTypeId()),field.required())).toList(),service.version()));
        var drift=graphs.get(admin,fixture.projectId(),fixture.serviceId());
        assertThat(drift.issues()).extracting(ValidationIssue::code).contains("RC-GRAPH-CONTRACT-DRIFT");
        var synchronizedGraph=graphs.synchronizeContract(admin,fixture.projectId(),fixture.serviceId(),drift.version());
        assertThat(synchronizedGraph.nodes()).extracting(GraphNodeView::kind).contains(GraphNodeKind.DECISION);
        assertThat(synchronizedGraph.issues()).extracting(ValidationIssue::code).doesNotContain("RC-GRAPH-CONTRACT-DRIFT");
    }

    @Test
    void rejectsTypeMismatchCyclesAndForeignProjectTypes(){
        var fixture=fixture();var graph=graphs.initialize(admin,fixture.projectId(),fixture.serviceId());
        var input=graph.nodes().stream().filter(n->n.kind()==GraphNodeKind.INPUT).findFirst().orElseThrow();
        var output=graph.nodes().stream().filter(n->n.kind()==GraphNodeKind.OUTPUT).findFirst().orElseThrow();
        var decisionId=UUID.randomUUID();var wrongIn=port(decisionId,GraphPortDirection.INPUT,"approved",BuiltinType.BOOLEAN,true);
        var decisionOut=port(decisionId,GraphPortDirection.OUTPUT,"approved",BuiltinType.BOOLEAN,true);
        var decision=new NodeCommand(decisionId,"mismatchedDecision","Mismatched decision",GraphNodeKind.DECISION,null,500,100,List.of(wrongIn,decisionOut));
        var nodes=new ArrayList<NodeCommand>();graph.nodes().forEach(n->nodes.add(command(n)));nodes.add(decision);
        assertCode("RC-GRAPH-EDGE-TYPE",()->graphs.update(admin,fixture.projectId(),fixture.serviceId(),new GraphCommand(graph.version(),nodes,List.of(
                new EdgeCommand(UUID.randomUUID(),input.ports().getFirst().id(),wrongIn.id()),
                new EdgeCommand(UUID.randomUUID(),decisionOut.id(),output.ports().getFirst().id())))));

        var firstId=UUID.randomUUID();var firstIn=port(firstId,GraphPortDirection.INPUT,"value",BuiltinType.NUMBER,true);var firstOut=port(firstId,GraphPortDirection.OUTPUT,"result",BuiltinType.NUMBER,true);
        var secondId=UUID.randomUUID();var secondIn=port(secondId,GraphPortDirection.INPUT,"value",BuiltinType.NUMBER,true);var secondOut=port(secondId,GraphPortDirection.OUTPUT,"result",BuiltinType.NUMBER,true);
        var cycleNodes=new ArrayList<NodeCommand>();graph.nodes().forEach(n->cycleNodes.add(command(n)));
        cycleNodes.add(new NodeCommand(firstId,"firstDecision","First decision",GraphNodeKind.DECISION,null,400,100,List.of(firstIn,firstOut)));
        cycleNodes.add(new NodeCommand(secondId,"secondDecision","Second decision",GraphNodeKind.DECISION,null,700,100,List.of(secondIn,secondOut)));
        assertCode("RC-GRAPH-CYCLE",()->graphs.update(admin,fixture.projectId(),fixture.serviceId(),new GraphCommand(graph.version(),cycleNodes,List.of(
                new EdgeCommand(UUID.randomUUID(),firstOut.id(),secondIn.id()),new EdgeCommand(UUID.randomUUID(),secondOut.id(),firstIn.id())))));

        var other=projects.create(admin,key("P"),"Foreign types",null);
        var foreign=catalog.createType(admin,other.id(),new TypeCommand(key("TYPE"),"Foreign",null,com.rulecraft.decisionops.catalog.domain.TypeKind.ENUM,null,List.of(),List.of(new EnumValueCommand("A","A")),null));
        var foreignOut=new PortCommand(UUID.randomUUID(),GraphPortDirection.OUTPUT,"result","Result",new TypeRefCommand(null,foreign.id()),true,0);
        var foreignNode=new NodeCommand(UUID.randomUUID(),"foreignDecision","Foreign decision",GraphNodeKind.DECISION,null,500,100,List.of(foreignOut));
        nodes.add(foreignNode);
        assertCode("RC-GRAPH-TYPE-NOT-FOUND",()->graphs.update(admin,fixture.projectId(),fixture.serviceId(),new GraphCommand(graph.version(),nodes,List.of())));
    }

    @Test
    void viewerCanInspectButCannotInitializeOrUpdate(){
        var fixture=fixture();var graph=graphs.initialize(admin,fixture.projectId(),fixture.serviceId());var now=Instant.now(clock);
        var account=users.save(new UserAccount(admin.tenantId(),"graph-viewer"+UUID.randomUUID()+"@example.test","Gia Graph Viewer",
                passwordEncoder.encode("CorrectHorseBattery56!"),TenantRole.MEMBER,now));
        projects.upsertMember(admin,fixture.projectId(),account.getId(),ProjectRole.VIEWER);var viewer=new RuleCraftPrincipal(account,"acme-rules");
        assertThat(graphs.get(viewer,fixture.projectId(),fixture.serviceId()).id()).isEqualTo(graph.id());
        assertCode("RC-PROJECT-EDIT-FORBIDDEN",()->graphs.update(viewer,fixture.projectId(),fixture.serviceId(),new GraphCommand(graph.version(),List.of(),List.of())));
    }

    private Fixture fixture(){var project=projects.create(admin,key("P"),"Graph modeling",null);var service=catalog.createService(admin,project.id(),
            new DecisionServiceCommand(key("SVC"),"Eligibility",null,
                    List.of(new FieldCommand("score","Applicant score",new CatalogService.TypeRefCommand(BuiltinType.NUMBER,null),true)),
                    List.of(new FieldCommand("approved","Approved",new CatalogService.TypeRefCommand(BuiltinType.BOOLEAN,null),true)),null));
        return new Fixture(project.id(),service.id());}
    private PortCommand port(UUID nodeId,GraphPortDirection direction,String key,BuiltinType type,boolean required){return new PortCommand(UUID.randomUUID(),direction,key,key,
            new TypeRefCommand(type,null),required,0);}
    private NodeCommand command(GraphNodeView n){return new NodeCommand(n.id(),n.nodeKey(),n.name(),n.kind(),n.serviceFieldKey(),n.positionX(),n.positionY(),
            n.ports().stream().map(p->new PortCommand(p.id(),p.direction(),p.portKey(),p.label(),p.type(),p.required(),p.position())).toList());}
    private String key(String prefix){return prefix+UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase();}
    private void assertCode(String code,Runnable action){assertThatThrownBy(action::run).isInstanceOf(DomainProblemException.class).extracting("code").isEqualTo(code);}
    private record Fixture(UUID projectId,UUID serviceId){}
}
