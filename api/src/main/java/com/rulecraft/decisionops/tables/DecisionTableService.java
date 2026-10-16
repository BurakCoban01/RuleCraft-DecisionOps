package com.rulecraft.decisionops.tables;

import com.rulecraft.decisionops.runtime.types.TypedValueEquality;
import com.rulecraft.decisionops.audit.AuditService;
import com.rulecraft.decisionops.catalog.CatalogService;
import com.rulecraft.decisionops.catalog.domain.*;
import com.rulecraft.decisionops.catalog.persistence.*;
import com.rulecraft.decisionops.graph.DecisionGraphService;
import com.rulecraft.decisionops.graph.DecisionGraphService.*;
import com.rulecraft.decisionops.graph.domain.*;
import com.rulecraft.decisionops.expressions.persistence.DecisionExpressionRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.ProjectAccessPolicy;
import com.rulecraft.decisionops.runtime.types.TypedValueValidator;
import com.rulecraft.decisionops.tables.domain.*;
import com.rulecraft.decisionops.tables.persistence.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class DecisionTableService {
    private static final int MAX_RULES=500;
    private final DecisionTableRepository tables;private final DecisionTableRuleRepository rules;private final DecisionTableEntryRepository entries;
    private final DecisionGraphService graphService;private final CatalogService catalog;private final DecisionExpressionRepository expressions;private final TypedValueValidator typedValues;private final ProjectAccessPolicy access;
    private final AuditService audit;private final ObjectMapper mapper;private final Clock clock;
    public DecisionTableService(DecisionTableRepository tables,DecisionTableRuleRepository rules,DecisionTableEntryRepository entries,
            DecisionGraphService graphService,CatalogService catalog,DecisionExpressionRepository expressions,TypedValueValidator typedValues,ProjectAccessPolicy access,AuditService audit,ObjectMapper mapper,Clock clock){this.tables=tables;
        this.rules=rules;this.entries=entries;this.graphService=graphService;this.catalog=catalog;this.expressions=expressions;this.typedValues=typedValues;this.access=access;this.audit=audit;this.mapper=mapper;this.clock=clock;}

    @Transactional(readOnly=true)
    public Optional<TableView> find(RuleCraftPrincipal actor,UUID projectId,UUID serviceId,UUID nodeId){
        var context=context(actor,projectId,serviceId,nodeId,false);return tables.findByNodeIdAndTenantIdAndProjectId(nodeId,actor.tenantId(),projectId).map(table->view(table,context));
    }
    @Transactional(readOnly=true)
    public List<TableSummary> summaries(RuleCraftPrincipal actor,UUID projectId,UUID serviceId){
        var graph=graphService.get(actor,projectId,serviceId);return tables.findAllByGraphIdAndTenantIdOrderByUpdatedAtDesc(graph.id(),actor.tenantId()).stream()
                .map(table->new TableSummary(table.getNodeId(),table.getHitPolicy(),table.getValidationStatus(),
                        rules.findAllByDecisionTableIdOrderByPositionAsc(table.getId()).stream().filter(DecisionTableRule::isEnabled).count(),table.getUpdatedAt(),table.getVersion())).toList();
    }
    @Transactional
    public TableView initialize(RuleCraftPrincipal actor,UUID projectId,UUID serviceId,UUID nodeId){
        var context=context(actor,projectId,serviceId,nodeId,true);if(tables.findByNodeIdAndTenantIdAndProjectId(nodeId,actor.tenantId(),projectId).isPresent())
            conflict("RC-TABLE-EXISTS","This decision already has a table. Refresh to continue editing.");if(expressions.existsByNodeId(nodeId))conflict("RC-IMPLEMENTATION-EXISTS","Remove the decision expression before choosing a table implementation.");var now=Instant.now(clock);
        var table=tables.saveAndFlush(new DecisionTableDefinition(actor.tenantId(),projectId,context.graph().id(),nodeId,actor.userId(),now));
        audit.record(actor.tenantId(),actor.userId(),"DECISION_TABLE_INITIALIZED","DECISION_TABLE",table.getId(),Map.of("nodeId",nodeId));return view(table,context);
    }
    @Transactional
    public TableView update(RuleCraftPrincipal actor,UUID projectId,UUID serviceId,UUID nodeId,TableCommand command){
        var context=context(actor,projectId,serviceId,nodeId,true);var table=requireTable(actor,projectId,nodeId);
        if(command==null||command.version()!=table.getVersion())conflict("RC-TABLE-STALE","The decision table changed. Refresh before saving again.");
        if(command.hitPolicy()==null)bad("RC-TABLE-HIT-POLICY","Choose UNIQUE or FIRST hit policy.");var requested=safe(command.rules());
        if(requested.size()>MAX_RULES)bad("RC-TABLE-RULE-LIMIT","A decision table can contain at most 500 rules.");
        validateStructure(context,requested);var issues=inspect(context,command.hitPolicy(),requested);
        entries.deleteAllByDecisionTableId(table.getId());entries.flush();rules.deleteAllByDecisionTableId(table.getId());rules.flush();
        int position=0;for(var rule:requested){rules.save(new DecisionTableRule(rule.id(),actor.tenantId(),table.getId(),position++,rule.enabled(),rule.description(),blankToNull(rule.outputValueJson())));}rules.flush();
        for(var rule:requested)for(var entry:rule.inputs())entries.save(new DecisionTableInputEntry(entry.id(),actor.tenantId(),table.getId(),rule.id(),entry.inputPortId(),entry.operator(),entry.operator()==InputOperator.ANY?null:entry.valueJson()));entries.flush();
        table.update(command.hitPolicy(),issues.isEmpty()?TableValidationStatus.VALID:TableValidationStatus.INVALID,Instant.now(clock));tables.saveAndFlush(table);
        audit.record(actor.tenantId(),actor.userId(),"DECISION_TABLE_UPDATED","DECISION_TABLE",table.getId(),Map.of("hitPolicy",command.hitPolicy().name(),"rules",requested.size(),"status",table.getValidationStatus().name()));
        return view(table,context);
    }
    @Transactional public void remove(RuleCraftPrincipal actor,UUID projectId,UUID serviceId,UUID nodeId,long version){context(actor,projectId,serviceId,nodeId,true);var table=requireTable(actor,projectId,nodeId);if(version!=table.getVersion())conflict("RC-TABLE-STALE","The decision table changed. Refresh before removing it.");var id=table.getId();tables.delete(table);tables.flush();audit.record(actor.tenantId(),actor.userId(),"DECISION_TABLE_REMOVED","DECISION_TABLE",id,Map.of("nodeId",nodeId));}

    private Context context(RuleCraftPrincipal actor,UUID projectId,UUID serviceId,UUID nodeId,boolean edit){
        if(edit)access.requireEdit(actor,projectId);else access.requireVisible(actor,projectId);var service=catalog.getService(actor,projectId,serviceId);
        if(edit&&service.status()!=DecisionServiceStatus.ACTIVE)conflict("RC-TABLE-SERVICE-ARCHIVED","Restore the decision service before authoring its table.");
        var graph=graphService.get(actor,projectId,serviceId);var node=graph.nodes().stream().filter(value->value.id().equals(nodeId)).findFirst()
                .orElseThrow(()->new DomainProblemException(HttpStatus.NOT_FOUND,"RC-GRAPH-NODE-NOT-FOUND","Decision node not found."));
        if(node.kind()!=GraphNodeKind.DECISION)bad("RC-TABLE-NODE-KIND","Decision tables can attach only to decision nodes.");
        var inputs=node.ports().stream().filter(port->port.direction()==GraphPortDirection.INPUT).sorted(Comparator.comparingInt(GraphPortView::position)).toList();
        var output=node.ports().stream().filter(port->port.direction()==GraphPortDirection.OUTPUT).findFirst().orElseThrow();return new Context(graph,node,inputs,output);
    }
    private void validateStructure(Context context,List<RuleCommand> requested){
        var ruleIds=new HashSet<UUID>();var entryIds=new HashSet<UUID>();var expected=context.inputs().stream().map(GraphPortView::id).collect(Collectors.toSet());
        for(var rule:requested){if(rule==null||rule.id()==null||!ruleIds.add(rule.id()))bad("RC-TABLE-RULE-ID","Rule ids must be present and unique.");
            if(rule.description()!=null&&rule.description().strip().length()>300)bad("RC-TABLE-DESCRIPTION","Rule descriptions can contain at most 300 characters.");
            if(rule.outputValueJson()!=null&&rule.outputValueJson().length()>4000)bad("RC-TABLE-CELL-LIMIT","Cell values can contain at most 4000 characters.");
            var seen=new HashSet<UUID>();for(var entry:safe(rule.inputs())){if(entry==null||entry.id()==null||!entryIds.add(entry.id()))bad("RC-TABLE-ENTRY-ID","Input entry ids must be present and unique.");
                if(entry.inputPortId()==null||!expected.contains(entry.inputPortId())||!seen.add(entry.inputPortId()))bad("RC-TABLE-COLUMNS","Every rule must contain one entry for each current decision input.");
                if(entry.operator()==null)bad("RC-TABLE-OPERATOR","Choose ANY or EQUALS for every input cell.");if(entry.valueJson()!=null&&entry.valueJson().length()>4000)bad("RC-TABLE-CELL-LIMIT","Cell values can contain at most 4000 characters.");
                if(entry.operator()==InputOperator.ANY&&entry.valueJson()!=null&&!entry.valueJson().isBlank())bad("RC-TABLE-ANY-VALUE","ANY cells do not accept a value.");
                if(entry.operator()==InputOperator.EQUALS&&(entry.valueJson()==null||entry.valueJson().isBlank()))bad("RC-TABLE-EQUALS-VALUE","EQUALS cells require a typed JSON value.");}
            if(!seen.equals(expected))bad("RC-TABLE-COLUMNS","Every rule must contain one entry for each current decision input.");}
    }
    private List<TableIssue> inspect(Context context,HitPolicy policy,List<RuleCommand> requested){
        var issues=new ArrayList<TableIssue>();var enabled=requested.stream().filter(RuleCommand::enabled).toList();
        if(enabled.isEmpty())issues.add(new TableIssue("RC-TABLE-NO-ENABLED-RULES","Add or enable at least one rule.",null,null));
        var typeByPort=context.inputs().stream().collect(Collectors.toMap(GraphPortView::id,GraphPortView::type));
        for(var rule:enabled){for(var entry:rule.inputs())if(entry.operator()==InputOperator.EQUALS&&!validJson(entry.valueJson(),typeByPort.get(entry.inputPortId())))
            issues.add(new TableIssue("RC-TABLE-INPUT-TYPE","Enter valid JSON matching this input type.",rule.id(),entry.inputPortId()));
            if(!validJson(rule.outputValueJson(),context.output().type()))issues.add(new TableIssue("RC-TABLE-OUTPUT-TYPE","Enter valid JSON matching the decision result type.",rule.id(),context.output().id()));}
        if(policy==HitPolicy.UNIQUE)for(int left=0;left<enabled.size();left++)for(int right=left+1;right<enabled.size();right++)if(overlap(enabled.get(left),enabled.get(right)))
            issues.add(new TableIssue("RC-TABLE-UNIQUE-OVERLAP","These enabled rules can match the same inputs. Make them exclusive or use FIRST.",enabled.get(right).id(),null));
        return issues;
    }
    private boolean overlap(RuleCommand left,RuleCommand right){var rightByPort=right.inputs().stream().collect(Collectors.toMap(InputEntryCommand::inputPortId,Function.identity()));
        for(var entry:left.inputs()){var other=rightByPort.get(entry.inputPortId());if(entry.operator()==InputOperator.ANY||other.operator()==InputOperator.ANY)continue;
            var a=parse(entry.valueJson());var b=parse(other.valueJson());if(a==null||b==null||!TypedValueEquality.equal(a,b))return false;}return true;}
    private boolean validJson(String raw,TypeRefCommand ref){var node=parse(raw);return node!=null&&ref!=null&&typedValues.valid(node,ref.builtinType(),ref.customTypeId());}
    private JsonNode parse(String raw){if(raw==null||raw.isBlank())return null;try{return mapper.readTree(raw);}catch(JacksonException exception){return null;}}
    private TableView view(DecisionTableDefinition table,Context context){var persistedRules=rules.findAllByDecisionTableIdOrderByPositionAsc(table.getId());var byRule=entries.findAllByDecisionTableId(table.getId()).stream().collect(Collectors.groupingBy(DecisionTableInputEntry::getRuleId));
        var expected=context.inputs().stream().map(GraphPortView::id).collect(Collectors.toSet());var schemaIssues=new ArrayList<TableIssue>();
        var ruleViews=persistedRules.stream().map(rule->{var persisted=byRule.getOrDefault(rule.getId(),List.of());var persistedByPort=persisted.stream().collect(Collectors.toMap(DecisionTableInputEntry::getInputPortId,Function.identity()));
            if(!persistedByPort.keySet().equals(expected))schemaIssues.add(new TableIssue("RC-TABLE-SCHEMA-DRIFT","The decision inputs changed. Review the regenerated cells and save this table.",rule.getId(),null));
            var normalized=context.inputs().stream().map(port->{var entry=persistedByPort.get(port.id());return entry==null?new InputEntryView(UUID.randomUUID(),port.id(),InputOperator.ANY,null):new InputEntryView(entry.getId(),entry.getInputPortId(),entry.getOperator(),entry.getValueJson());}).toList();
            return new RuleView(rule.getId(),rule.isEnabled(),rule.getDescription(),normalized,rule.getOutputValueJson());}).toList();
        var commandRules=ruleViews.stream().map(rule->new RuleCommand(rule.id(),rule.enabled(),rule.description(),rule.inputs().stream().map(entry->new InputEntryCommand(entry.id(),entry.inputPortId(),entry.operator(),entry.valueJson())).toList(),rule.outputValueJson())).toList();
        var issues=new ArrayList<>(schemaIssues);issues.addAll(inspect(context,table.getHitPolicy(),commandRules));var columns=context.inputs().stream().map(port->new ColumnView(port.id(),port.portKey(),port.label(),port.type(),port.required())).toList();
        var output=new ColumnView(context.output().id(),context.output().portKey(),context.output().label(),context.output().type(),context.output().required());
        return new TableView(table.getId(),context.graph().id(),context.node().id(),context.node().nodeKey(),context.node().name(),table.getHitPolicy(),issues.isEmpty()?TableValidationStatus.VALID:TableValidationStatus.INVALID,issues,columns,output,ruleViews,table.getUpdatedAt(),table.getVersion());}
    private DecisionTableDefinition requireTable(RuleCraftPrincipal actor,UUID projectId,UUID nodeId){return tables.findByNodeIdAndTenantIdAndProjectId(nodeId,actor.tenantId(),projectId)
            .orElseThrow(()->new DomainProblemException(HttpStatus.NOT_FOUND,"RC-TABLE-NOT-FOUND","This decision does not have a table yet."));}
    private String blankToNull(String value){return value==null||value.isBlank()?null:value.strip();}private <T> List<T> safe(List<T> value){return value==null?List.of():value;}
    private void bad(String code,String message){throw new DomainProblemException(HttpStatus.BAD_REQUEST,code,message);}private void conflict(String code,String message){throw new DomainProblemException(HttpStatus.CONFLICT,code,message);}

    private record Context(GraphView graph,GraphNodeView node,List<GraphPortView> inputs,GraphPortView output){}
    public record InputEntryCommand(UUID id,UUID inputPortId,InputOperator operator,String valueJson){}
    public record RuleCommand(UUID id,boolean enabled,String description,List<InputEntryCommand> inputs,String outputValueJson){}
    public record TableCommand(long version,HitPolicy hitPolicy,List<RuleCommand> rules){}
    public record TableIssue(String code,String message,UUID ruleId,UUID portId){}
    public record ColumnView(UUID portId,String portKey,String label,TypeRefCommand type,boolean required){}
    public record InputEntryView(UUID id,UUID inputPortId,InputOperator operator,String valueJson){}
    public record RuleView(UUID id,boolean enabled,String description,List<InputEntryView> inputs,String outputValueJson){}
    public record TableView(UUID id,UUID graphId,UUID nodeId,String nodeKey,String nodeName,HitPolicy hitPolicy,TableValidationStatus validationStatus,
            List<TableIssue> issues,List<ColumnView> inputs,ColumnView output,List<RuleView> rules,Instant updatedAt,long version){}
    public record TableSummary(UUID nodeId,HitPolicy hitPolicy,TableValidationStatus validationStatus,long enabledRules,Instant updatedAt,long version){}
}
