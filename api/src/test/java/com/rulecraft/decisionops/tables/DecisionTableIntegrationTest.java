package com.rulecraft.decisionops.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rulecraft.decisionops.catalog.CatalogService;
import com.rulecraft.decisionops.catalog.CatalogService.*;
import com.rulecraft.decisionops.catalog.domain.BuiltinType;
import com.rulecraft.decisionops.graph.DecisionGraphService;
import com.rulecraft.decisionops.graph.DecisionGraphService.*;
import com.rulecraft.decisionops.graph.domain.*;
import com.rulecraft.decisionops.identity.domain.*;
import com.rulecraft.decisionops.identity.persistence.UserAccountRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.ProjectService;
import com.rulecraft.decisionops.projects.domain.ProjectRole;
import com.rulecraft.decisionops.tables.DecisionTableService.*;
import com.rulecraft.decisionops.tables.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class DecisionTableIntegrationTest {
    @Autowired DecisionTableService tables;@Autowired DecisionGraphService graphs;@Autowired CatalogService catalog;@Autowired ProjectService projects;
    @Autowired UserAccountRepository users;@Autowired PasswordEncoder passwordEncoder;@Autowired Clock clock;@Autowired JdbcClient jdbc;
    RuleCraftPrincipal admin;
    @BeforeEach void actor(){admin=new RuleCraftPrincipal(users.findForLogin("acme-rules","admin@example.test").orElseThrow(),"acme-rules");}

    @Test void persistsTypedUniqueRulesAndSurvivesGraphLayoutSave(){
        var fixture=fixture();var table=tables.initialize(admin,fixture.projectId(),fixture.serviceId(),fixture.nodeId());
        var high=rule(fixture.inputPortId(),"700","true");var low=rule(fixture.inputPortId(),"500","false");
        var saved=tables.update(admin,fixture.projectId(),fixture.serviceId(),fixture.nodeId(),new TableCommand(table.version(),HitPolicy.UNIQUE,List.of(high,low)));
        assertThat(saved.validationStatus()).isEqualTo(TableValidationStatus.VALID);assertThat(saved.rules()).hasSize(2);
        var graph=graphs.get(admin,fixture.projectId(),fixture.serviceId());var moved=graph.nodes().stream().map(node->command(node,node.id().equals(fixture.nodeId())?node.positionX()+20:node.positionX())).toList();
        graphs.update(admin,fixture.projectId(),fixture.serviceId(),new GraphCommand(graph.version(),moved,graph.edges().stream().map(edge->new EdgeCommand(edge.id(),edge.sourcePortId(),edge.targetPortId())).toList()));
        assertThat(tables.find(admin,fixture.projectId(),fixture.serviceId(),fixture.nodeId())).isPresent();
        var afterMove=graphs.get(admin,fixture.projectId(),fixture.serviceId());var changedNodes=afterMove.nodes().stream().map(node->{var base=command(node,node.positionX());
            if(!node.id().equals(fixture.nodeId()))return base;var ports=new ArrayList<>(base.ports());ports.add(new PortCommand(UUID.randomUUID(),GraphPortDirection.INPUT,"region","Region",new DecisionGraphService.TypeRefCommand(BuiltinType.TEXT,null),true,1));
            return new NodeCommand(base.id(),base.nodeKey(),base.name(),base.kind(),base.serviceFieldKey(),base.positionX(),base.positionY(),ports);}).toList();
        graphs.update(admin,fixture.projectId(),fixture.serviceId(),new GraphCommand(afterMove.version(),changedNodes,afterMove.edges().stream().map(edge->new EdgeCommand(edge.id(),edge.sourcePortId(),edge.targetPortId())).toList()));
        var drifted=tables.find(admin,fixture.projectId(),fixture.serviceId(),fixture.nodeId()).orElseThrow();
        assertThat(drifted.validationStatus()).isEqualTo(TableValidationStatus.INVALID);assertThat(drifted.issues()).extracting(TableIssue::code).contains("RC-TABLE-SCHEMA-DRIFT");
        assertThat(drifted.rules()).allSatisfy(rule->assertThat(rule.inputs()).hasSize(2));
        var recovered=tables.update(admin,fixture.projectId(),fixture.serviceId(),fixture.nodeId(),new TableCommand(drifted.version(),drifted.hitPolicy(),drifted.rules().stream().map(this::command).toList()));
        assertThat(recovered.validationStatus()).isEqualTo(TableValidationStatus.VALID);
        assertThat(jdbc.sql("select count(*) from audit_event where action = 'DECISION_TABLE_UPDATED'").query(Long.class).single()).isPositive();
    }

    @Test void savesActionableTypeAndUniqueOverlapIssuesThenFirstResolvesOverlap(){
        var fixture=fixture();var table=tables.initialize(admin,fixture.projectId(),fixture.serviceId(),fixture.nodeId());
        var invalid=rule(fixture.inputPortId(),"\"not a number\"","true");var any=anyRule(fixture.inputPortId(),"false");
        var saved=tables.update(admin,fixture.projectId(),fixture.serviceId(),fixture.nodeId(),new TableCommand(table.version(),HitPolicy.UNIQUE,List.of(invalid,any)));
        assertThat(saved.validationStatus()).isEqualTo(TableValidationStatus.INVALID);
        assertThat(saved.issues()).extracting(TableIssue::code).contains("RC-TABLE-INPUT-TYPE","RC-TABLE-UNIQUE-OVERLAP");
        var first=tables.update(admin,fixture.projectId(),fixture.serviceId(),fixture.nodeId(),new TableCommand(saved.version(),HitPolicy.FIRST,List.of(rule(fixture.inputPortId(),"700","true"),anyRule(fixture.inputPortId(),"false"))));
        assertThat(first.validationStatus()).isEqualTo(TableValidationStatus.VALID);assertThat(first.issues()).isEmpty();
        assertCode("RC-TABLE-STALE",()->tables.update(admin,fixture.projectId(),fixture.serviceId(),fixture.nodeId(),new TableCommand(saved.version(),HitPolicy.FIRST,List.of())));
    }

    @Test void equivalentNumericRulesAreReportedAsUniqueOverlap(){
        var fixture=fixture();var table=tables.initialize(admin,fixture.projectId(),fixture.serviceId(),fixture.nodeId());
        var saved=tables.update(admin,fixture.projectId(),fixture.serviceId(),fixture.nodeId(),
                new TableCommand(table.version(),HitPolicy.UNIQUE,List.of(
                        rule(fixture.inputPortId(),"700","true"),rule(fixture.inputPortId(),"700.0","false"))));
        assertThat(saved.validationStatus()).isEqualTo(TableValidationStatus.INVALID);
        assertThat(saved.issues()).extracting(TableIssue::code).contains("RC-TABLE-UNIQUE-OVERLAP");
    }

    @Test void viewerCanReadButCannotAuthorTable(){
        var fixture=fixture();var table=tables.initialize(admin,fixture.projectId(),fixture.serviceId(),fixture.nodeId());var now=Instant.now(clock);
        var account=users.save(new UserAccount(admin.tenantId(),"table-viewer"+UUID.randomUUID()+"@example.test","Tara Table Viewer",passwordEncoder.encode("CorrectHorseBattery56!"),TenantRole.MEMBER,now));
        projects.upsertMember(admin,fixture.projectId(),account.getId(),ProjectRole.VIEWER);var viewer=new RuleCraftPrincipal(account,"acme-rules");
        assertThat(tables.find(viewer,fixture.projectId(),fixture.serviceId(),fixture.nodeId())).isPresent();
        assertCode("RC-PROJECT-EDIT-FORBIDDEN",()->tables.update(viewer,fixture.projectId(),fixture.serviceId(),fixture.nodeId(),new TableCommand(table.version(),HitPolicy.FIRST,List.of())));
    }

    private Fixture fixture(){var project=projects.create(admin,key("P"),"Decision tables",null);var service=catalog.createService(admin,project.id(),new DecisionServiceCommand(key("SVC"),"Eligibility",null,
            List.of(new FieldCommand("score","Score",new CatalogService.TypeRefCommand(BuiltinType.NUMBER,null),true)),List.of(new FieldCommand("approved","Approved",new CatalogService.TypeRefCommand(BuiltinType.BOOLEAN,null),true)),null));
        var graph=graphs.initialize(admin,project.id(),service.id());var nodeId=UUID.randomUUID();var input=port(nodeId,GraphPortDirection.INPUT,"score",BuiltinType.NUMBER);var output=port(nodeId,GraphPortDirection.OUTPUT,"approved",BuiltinType.BOOLEAN);
        var node=new NodeCommand(nodeId,"eligibilityDecision","Eligibility decision",GraphNodeKind.DECISION,null,265,120,List.of(input,output));var nodes=new ArrayList<NodeCommand>();graph.nodes().forEach(value->nodes.add(command(value,value.positionX())));nodes.add(node);
        graphs.update(admin,project.id(),service.id(),new GraphCommand(graph.version(),nodes,List.of()));return new Fixture(project.id(),service.id(),nodeId,input.id());}
    private PortCommand port(UUID nodeId,GraphPortDirection direction,String key,BuiltinType type){return new PortCommand(UUID.randomUUID(),direction,key,key,new DecisionGraphService.TypeRefCommand(type,null),true,0);}
    private NodeCommand command(GraphNodeView node,int x){return new NodeCommand(node.id(),node.nodeKey(),node.name(),node.kind(),node.serviceFieldKey(),x,node.positionY(),node.ports().stream().map(port->new PortCommand(port.id(),port.direction(),port.portKey(),port.label(),port.type(),port.required(),port.position())).toList());}
    private RuleCommand rule(UUID portId,String input,String output){return new RuleCommand(UUID.randomUUID(),true,null,List.of(new InputEntryCommand(UUID.randomUUID(),portId,InputOperator.EQUALS,input)),output);}
    private RuleCommand anyRule(UUID portId,String output){return new RuleCommand(UUID.randomUUID(),true,null,List.of(new InputEntryCommand(UUID.randomUUID(),portId,InputOperator.ANY,null)),output);}
    private RuleCommand command(RuleView rule){return new RuleCommand(rule.id(),rule.enabled(),rule.description(),rule.inputs().stream().map(entry->new InputEntryCommand(entry.id(),entry.inputPortId(),entry.operator(),entry.valueJson())).toList(),rule.outputValueJson());}
    private String key(String prefix){return prefix+UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase();}
    private void assertCode(String code,Runnable action){assertThatThrownBy(action::run).isInstanceOf(DomainProblemException.class).extracting("code").isEqualTo(code);}
    private record Fixture(UUID projectId,UUID serviceId,UUID nodeId,UUID inputPortId){}
}
