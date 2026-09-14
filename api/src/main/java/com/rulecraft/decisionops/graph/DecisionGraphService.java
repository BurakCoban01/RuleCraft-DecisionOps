package com.rulecraft.decisionops.graph;

import com.rulecraft.decisionops.audit.AuditService;
import com.rulecraft.decisionops.catalog.CatalogService;
import com.rulecraft.decisionops.catalog.CatalogService.DecisionServiceView;
import com.rulecraft.decisionops.catalog.domain.BuiltinType;
import com.rulecraft.decisionops.catalog.domain.DecisionServiceStatus;
import com.rulecraft.decisionops.catalog.persistence.TypeDefinitionRepository;
import com.rulecraft.decisionops.graph.domain.*;
import com.rulecraft.decisionops.graph.persistence.*;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.ProjectAccessPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionGraphService {
    private static final int MAX_NODES=200,MAX_PORTS=100,MAX_EDGES=500;
    private final DecisionGraphRepository graphs;
    private final GraphNodeRepository nodes;
    private final GraphPortRepository ports;
    private final GraphEdgeRepository edges;
    private final TypeDefinitionRepository types;
    private final CatalogService catalog;
    private final ProjectAccessPolicy access;
    private final AuditService audit;
    private final Clock clock;

    public DecisionGraphService(DecisionGraphRepository graphs,GraphNodeRepository nodes,GraphPortRepository ports,
            GraphEdgeRepository edges,TypeDefinitionRepository types,CatalogService catalog,ProjectAccessPolicy access,
            AuditService audit,Clock clock){this.graphs=graphs;this.nodes=nodes;this.ports=ports;this.edges=edges;this.types=types;
        this.catalog=catalog;this.access=access;this.audit=audit;this.clock=clock;}

    @Transactional(readOnly=true)
    public GraphView get(RuleCraftPrincipal actor,UUID projectId,UUID serviceId){
        access.requireVisible(actor,projectId);var service=catalog.getService(actor,projectId,serviceId);
        return view(requireGraph(actor,projectId,serviceId),service);
    }

    @Transactional(readOnly=true)
    public Optional<GraphView> find(RuleCraftPrincipal actor,UUID projectId,UUID serviceId){
        access.requireVisible(actor,projectId);var service=catalog.getService(actor,projectId,serviceId);
        return graphs.findByDecisionServiceIdAndTenantIdAndProjectId(serviceId,actor.tenantId(),projectId).map(graph->view(graph,service));
    }

    @Transactional
    public GraphView initialize(RuleCraftPrincipal actor,UUID projectId,UUID serviceId){
        access.requireEdit(actor,projectId);var service=catalog.getService(actor,projectId,serviceId);
        if(service.status()!=DecisionServiceStatus.ACTIVE)conflict("RC-GRAPH-SERVICE-ARCHIVED","Restore the decision service before authoring its graph.");
        if(graphs.findByDecisionServiceIdAndTenantIdAndProjectId(serviceId,actor.tenantId(),projectId).isPresent())
            conflict("RC-GRAPH-EXISTS","This decision service already has a graph. Refresh to continue editing.");
        var graph=graphs.saveAndFlush(new DecisionGraph(actor.tenantId(),projectId,serviceId,actor.userId(),Instant.now(clock)));
        var aggregate=initialAggregate(actor,graph,service);persistChildren(actor,graph,aggregate.nodes(),aggregate.edges());
        graph.touch(GraphValidationStatus.INVALID,Instant.now(clock));graphs.saveAndFlush(graph);
        audit.record(actor.tenantId(),actor.userId(),"DECISION_GRAPH_INITIALIZED","DECISION_GRAPH",graph.getId(),Map.of("decisionServiceId",serviceId));
        return view(graph,service);
    }

    @Transactional
    public GraphView update(RuleCraftPrincipal actor,UUID projectId,UUID serviceId,GraphCommand command){
        access.requireEdit(actor,projectId);var service=catalog.getService(actor,projectId,serviceId);
        if(service.status()!=DecisionServiceStatus.ACTIVE)conflict("RC-GRAPH-SERVICE-ARCHIVED","Restore the decision service before authoring its graph.");
        var graph=requireGraph(actor,projectId,serviceId);if(command==null||command.version()!=graph.getVersion())
            conflict("RC-GRAPH-STALE","The graph changed while you were editing it. Refresh before saving again.");
        var normalized=validateAndNormalize(actor,projectId,service,safe(command.nodes()),safe(command.edges()));
        deleteChildren(graph.getId());persistChildren(actor,graph,normalized.nodes(),normalized.edges());
        var issues=completionIssues(service,normalized.nodes(),normalized.edges());
        graph.touch(issues.isEmpty()?GraphValidationStatus.VALID:GraphValidationStatus.INVALID,Instant.now(clock));graphs.saveAndFlush(graph);
        audit.record(actor.tenantId(),actor.userId(),"DECISION_GRAPH_UPDATED","DECISION_GRAPH",graph.getId(),
                Map.of("nodes",normalized.nodes().size(),"edges",normalized.edges().size(),"status",graph.getValidationStatus().name()));
        return view(graph,service);
    }

    @Transactional
    public GraphView synchronizeContract(RuleCraftPrincipal actor,UUID projectId,UUID serviceId,long version){
        access.requireEdit(actor,projectId);var service=catalog.getService(actor,projectId,serviceId);var graph=requireGraph(actor,projectId,serviceId);
        if(service.status()!=DecisionServiceStatus.ACTIVE)conflict("RC-GRAPH-SERVICE-ARCHIVED","Restore the decision service before synchronizing its graph.");
        if(version!=graph.getVersion())conflict("RC-GRAPH-STALE","The graph changed while you were editing it. Refresh before saving again.");
        var persistedNodes=nodes.findAllByGraphIdOrderByPositionXAscPositionYAsc(graph.getId());
        var persistedPorts=ports.findAllByGraphIdOrderByNodeIdAscDirectionAscPositionAsc(graph.getId());
        var byNode=persistedPorts.stream().map(this::portView).collect(Collectors.groupingBy(GraphPortView::nodeId));
        var decisions=persistedNodes.stream().filter(n->n.getKind()==GraphNodeKind.DECISION).map(n->new NodeCommand(n.getId(),n.getNodeKey(),n.getName(),n.getKind(),null,
                n.getPositionX(),n.getPositionY(),byNode.getOrDefault(n.getId(),List.of()).stream().map(p->new PortCommand(p.id(),p.direction(),p.portKey(),p.label(),p.type(),p.required(),p.position())).toList())).toList();
        var fresh=initialAggregate(actor,graph,service);var allNodes=new ArrayList<NodeCommand>(fresh.nodes());allNodes.addAll(decisions);
        var decisionPorts=decisions.stream().flatMap(n->n.ports().stream()).map(PortCommand::id).collect(Collectors.toSet());
        var preservedEdges=edges.findAllByGraphIdOrderByIdAsc(graph.getId()).stream().filter(e->decisionPorts.contains(e.getSourcePortId())&&decisionPorts.contains(e.getTargetPortId()))
                .map(e->new EdgeCommand(e.getId(),e.getSourcePortId(),e.getTargetPortId())).toList();
        var normalized=validateAndNormalize(actor,projectId,service,allNodes,preservedEdges);deleteChildren(graph.getId());persistChildren(actor,graph,normalized.nodes(),normalized.edges());
        var issues=completionIssues(service,normalized.nodes(),normalized.edges());graph.touch(issues.isEmpty()?GraphValidationStatus.VALID:GraphValidationStatus.INVALID,Instant.now(clock));graphs.saveAndFlush(graph);
        audit.record(actor.tenantId(),actor.userId(),"DECISION_GRAPH_CONTRACT_SYNCHRONIZED","DECISION_GRAPH",graph.getId(),Map.of("decisionServiceId",serviceId));
        return view(graph,service);
    }

    private Aggregate initialAggregate(RuleCraftPrincipal actor,DecisionGraph graph,DecisionServiceView service){
        var result=new ArrayList<NodeCommand>();int y=80;
        for(var field:service.inputs()){var nodeId=UUID.randomUUID();result.add(new NodeCommand(nodeId,"input_"+field.fieldKey(),field.label(),
                GraphNodeKind.INPUT,field.fieldKey(),20,y,List.of(port(nodeId,GraphPortDirection.OUTPUT,field.fieldKey(),field))));y+=150;}
        y=80;for(var field:service.outputs()){var nodeId=UUID.randomUUID();result.add(new NodeCommand(nodeId,"output_"+field.fieldKey(),field.label(),
                GraphNodeKind.OUTPUT,field.fieldKey(),510,y,List.of(port(nodeId,GraphPortDirection.INPUT,field.fieldKey(),field))));y+=150;}
        return new Aggregate(result,List.of());
    }

    private PortCommand port(UUID nodeId,GraphPortDirection direction,String key,CatalogService.FieldView field){
        var ref=field.type();return new PortCommand(UUID.randomUUID(),direction,key,field.label(),
                new TypeRefCommand(ref.builtinType(),ref.customTypeId()),field.required(),0);
    }

    private Aggregate validateAndNormalize(RuleCraftPrincipal actor,UUID projectId,DecisionServiceView service,
            List<NodeCommand> requestedNodes,List<EdgeCommand> requestedEdges){
        if(requestedNodes.size()>MAX_NODES)bad("RC-GRAPH-NODE-LIMIT","A graph can contain at most 200 nodes.");
        if(requestedEdges.size()>MAX_EDGES)bad("RC-GRAPH-EDGE-LIMIT","A graph can contain at most 500 connections.");
        var nodeIds=new HashSet<UUID>();var nodeKeys=new HashSet<String>();var portIds=new HashSet<UUID>();
        var normalizedNodes=new ArrayList<NodeCommand>();
        for(var node:requestedNodes){
            if(node==null||node.id()==null||node.kind()==null)bad("RC-GRAPH-NODE-INVALID","Every graph node requires an id and kind.");
            if(!nodeIds.add(node.id()))bad("RC-GRAPH-NODE-DUPLICATE","Graph node ids must be unique.");
            var key=requiredKey(node.nodeKey(),"node");if(!nodeKeys.add(key))bad("RC-GRAPH-NODE-KEY-DUPLICATE","Graph node keys must be unique.");
            var name=requiredText(node.name(),160,"node name");if(node.positionX()<0||node.positionX()>4000||node.positionY()<0||node.positionY()>4000)
                bad("RC-GRAPH-POSITION","Node positions must stay within the graph canvas.");
            var requestedPorts=safe(node.ports());if(requestedPorts.size()>MAX_PORTS)bad("RC-GRAPH-PORT-LIMIT","A node can contain at most 100 ports.");
            var portKeys=new HashSet<String>();var normalizedPorts=new ArrayList<PortCommand>();int position=0;
            for(var port:requestedPorts){
                if(port==null||port.id()==null||port.direction()==null)bad("RC-GRAPH-PORT-INVALID","Every port requires an id and direction.");
                if(!portIds.add(port.id()))bad("RC-GRAPH-PORT-DUPLICATE","Graph port ids must be unique.");
                var portKey=requiredKey(port.portKey(),"port");if(!portKeys.add(port.direction()+":"+portKey))bad("RC-GRAPH-PORT-KEY-DUPLICATE","Port keys must be unique within each direction.");
                validateTypeRef(actor,projectId,port.type());
                normalizedPorts.add(new PortCommand(port.id(),port.direction(),portKey,requiredText(port.label(),160,"port label"),port.type(),port.required(),position++));
            }
            validateNodeShape(node.kind(),node.serviceFieldKey(),normalizedPorts);
            normalizedNodes.add(new NodeCommand(node.id(),key,name,node.kind(),node.serviceFieldKey(),node.positionX(),node.positionY(),normalizedPorts));
        }
        validateContract(service,normalizedNodes);
        var byPort=normalizedNodes.stream().flatMap(n->n.ports().stream()).collect(Collectors.toMap(PortCommand::id,Function.identity()));
        var ownerByPort=new HashMap<UUID,UUID>();normalizedNodes.forEach(n->n.ports().forEach(p->ownerByPort.put(p.id(),n.id())));
        var edgeIds=new HashSet<UUID>();var targets=new HashSet<UUID>();var normalizedEdges=new ArrayList<EdgeCommand>();
        for(var edge:requestedEdges){
            if(edge==null||edge.id()==null||!edgeIds.add(edge.id()))bad("RC-GRAPH-EDGE-DUPLICATE","Connection ids must be present and unique.");
            var source=byPort.get(edge.sourcePortId());var target=byPort.get(edge.targetPortId());
            if(source==null||target==null)bad("RC-GRAPH-EDGE-PORT","Connections must reference ports in this graph.");
            if(source.direction()!=GraphPortDirection.OUTPUT||target.direction()!=GraphPortDirection.INPUT)
                bad("RC-GRAPH-EDGE-DIRECTION","Connections run from an output port to an input port.");
            if(ownerByPort.get(source.id()).equals(ownerByPort.get(target.id())))bad("RC-GRAPH-EDGE-SELF","A node cannot connect to itself.");
            if(!sameType(source.type(),target.type()))bad("RC-GRAPH-EDGE-TYPE","Connected ports must use the exact same type.");
            if(!targets.add(target.id()))bad("RC-GRAPH-EDGE-TARGET","An input port accepts only one incoming connection.");
            normalizedEdges.add(edge);
        }
        rejectCycles(normalizedNodes,normalizedEdges,ownerByPort);
        return new Aggregate(normalizedNodes,normalizedEdges);
    }

    private void validateNodeShape(GraphNodeKind kind,String serviceFieldKey,List<PortCommand> nodePorts){
        long ins=nodePorts.stream().filter(p->p.direction()==GraphPortDirection.INPUT).count();
        long outs=nodePorts.size()-ins;
        if(kind==GraphNodeKind.INPUT&&(serviceFieldKey==null||ins!=0||outs!=1))bad("RC-GRAPH-INPUT-SHAPE","Contract input nodes require exactly one output port.");
        if(kind==GraphNodeKind.OUTPUT&&(serviceFieldKey==null||ins!=1||outs!=0))bad("RC-GRAPH-OUTPUT-SHAPE","Contract output nodes require exactly one input port.");
        if(kind==GraphNodeKind.DECISION&&(serviceFieldKey!=null||outs!=1))bad("RC-GRAPH-DECISION-SHAPE","Decision nodes require exactly one output port and no contract field.");
    }

    private void validateContract(DecisionServiceView service,List<NodeCommand> graphNodes){
        for(var field:service.inputs())validateContractField(graphNodes,GraphNodeKind.INPUT,field);
        for(var field:service.outputs())validateContractField(graphNodes,GraphNodeKind.OUTPUT,field);
        long contracts=graphNodes.stream().filter(n->n.kind()!=GraphNodeKind.DECISION).count();
        if(contracts!=service.inputs().size()+service.outputs().size())bad("RC-GRAPH-CONTRACT","Graph contract nodes must exactly match the decision service schema.");
    }

    private void validateContractField(List<NodeCommand> graphNodes,GraphNodeKind kind,CatalogService.FieldView field){
        var matches=graphNodes.stream().filter(n->n.kind()==kind&&field.fieldKey().equals(n.serviceFieldKey())).toList();
        if(matches.size()!=1)bad("RC-GRAPH-CONTRACT","Graph contract nodes must exactly match the decision service schema.");
        var node=matches.getFirst();var port=node.ports().getFirst();var expected=new TypeRefCommand(field.type().builtinType(),field.type().customTypeId());
        var prefix=kind==GraphNodeKind.INPUT?"input_":"output_";
        if(!node.nodeKey().equals(prefix+field.fieldKey())||!node.name().equals(field.label())||!port.portKey().equals(field.fieldKey())
                ||!port.label().equals(field.label())||port.required()!=field.required())
            bad("RC-GRAPH-CONTRACT","Contract node labels, keys, and requirements are controlled by the decision service schema.");
        if(!sameType(port.type(),expected))bad("RC-GRAPH-CONTRACT-TYPE","Contract node types are controlled by the decision service schema.");
    }

    private void rejectCycles(List<NodeCommand> graphNodes,List<EdgeCommand> graphEdges,Map<UUID,UUID> ownerByPort){
        var adjacent=new HashMap<UUID,List<UUID>>();graphNodes.forEach(n->adjacent.put(n.id(),new ArrayList<>()));
        graphEdges.forEach(e->adjacent.get(ownerByPort.get(e.sourcePortId())).add(ownerByPort.get(e.targetPortId())));
        var visiting=new HashSet<UUID>();var visited=new HashSet<UUID>();
        for(var node:graphNodes)if(hasCycle(node.id(),adjacent,visiting,visited))bad("RC-GRAPH-CYCLE","Decision graphs must not contain circular dependencies.");
    }

    private boolean hasCycle(UUID node,Map<UUID,List<UUID>> adjacent,Set<UUID> visiting,Set<UUID> visited){
        if(visiting.contains(node))return true;if(!visited.add(node))return false;visiting.add(node);
        for(var next:adjacent.getOrDefault(node,List.of()))if(hasCycle(next,adjacent,visiting,visited))return true;
        visiting.remove(node);return false;
    }

    private List<ValidationIssue> completionIssues(DecisionServiceView service,List<NodeCommand> graphNodes,List<EdgeCommand> graphEdges){
        var issues=new ArrayList<ValidationIssue>();var targets=graphEdges.stream().map(EdgeCommand::targetPortId).collect(Collectors.toSet());
        for(var node:graphNodes){
            for(var port:node.ports())if(port.direction()==GraphPortDirection.INPUT&&port.required()&&!targets.contains(port.id()))
                issues.add(new ValidationIssue("ERROR","RC-GRAPH-REQUIRED-UNCONNECTED","Connect the required “"+port.label()+"” input.",node.id(),port.id()));
            if(node.kind()==GraphNodeKind.DECISION&&graphEdges.stream().noneMatch(e->node.ports().stream().anyMatch(p->p.id().equals(e.sourcePortId())||p.id().equals(e.targetPortId()))))
                issues.add(new ValidationIssue("ERROR","RC-GRAPH-DECISION-ORPHAN","Connect this decision to the graph flow.",node.id(),null));
        }
        if(graphNodes.stream().noneMatch(n->n.kind()==GraphNodeKind.DECISION))
            issues.add(new ValidationIssue("ERROR","RC-GRAPH-DECISION-MISSING","Add at least one decision node.",null,null));
        return issues;
    }

    private GraphView view(DecisionGraph graph,DecisionServiceView service){
        var persistedNodes=nodes.findAllByGraphIdOrderByPositionXAscPositionYAsc(graph.getId());
        var persistedPorts=ports.findAllByGraphIdOrderByNodeIdAscDirectionAscPositionAsc(graph.getId());
        var portViews=persistedPorts.stream().map(this::portView).collect(Collectors.groupingBy(GraphPortView::nodeId,LinkedHashMap::new,Collectors.toList()));
        var nodeViews=persistedNodes.stream().map(n->new GraphNodeView(n.getId(),n.getNodeKey(),n.getName(),n.getKind(),n.getServiceFieldKey(),
                n.getPositionX(),n.getPositionY(),portViews.getOrDefault(n.getId(),List.of()))).toList();
        var edgeViews=edges.findAllByGraphIdOrderByIdAsc(graph.getId()).stream().map(e->new GraphEdgeView(e.getId(),e.getSourcePortId(),e.getTargetPortId())).toList();
        var commandNodes=nodeViews.stream().map(this::commandNode).toList();var commandEdges=edgeViews.stream().map(e->new EdgeCommand(e.id(),e.sourcePortId(),e.targetPortId())).toList();
        var issues=new ArrayList<ValidationIssue>();
        if(contractDrift(service,commandNodes))issues.add(new ValidationIssue("ERROR","RC-GRAPH-CONTRACT-DRIFT","The decision-service schema changed. Restore its graph contract nodes before saving.",null,null));
        else issues.addAll(completionIssues(service,commandNodes,commandEdges));
        var status=issues.isEmpty()?GraphValidationStatus.VALID:GraphValidationStatus.INVALID;
        return new GraphView(graph.getId(),service.id(),service.serviceKey(),service.name(),status,issues,nodeViews,edgeViews,graph.getUpdatedAt(),graph.getVersion());
    }

    private boolean contractDrift(DecisionServiceView service,List<NodeCommand> graphNodes){
        try{validateContract(service,graphNodes);return false;}catch(DomainProblemException exception){return true;}
    }

    private NodeCommand commandNode(GraphNodeView n){return new NodeCommand(n.id(),n.nodeKey(),n.name(),n.kind(),n.serviceFieldKey(),n.positionX(),n.positionY(),
            n.ports().stream().map(p->new PortCommand(p.id(),p.direction(),p.portKey(),p.label(),p.type(),p.required(),p.position())).toList());}
    private GraphPortView portView(GraphPort p){return new GraphPortView(p.getId(),p.getNodeId(),p.getDirection(),p.getPortKey(),p.getLabel(),
            typeView(p.getBuiltinType(),p.getCustomTypeId()),p.isRequired(),p.getPosition());}
    private TypeRefCommand typeView(BuiltinType builtin,UUID customId){return new TypeRefCommand(builtin,customId);}

    private void persistChildren(RuleCraftPrincipal actor,DecisionGraph graph,List<NodeCommand> graphNodes,List<EdgeCommand> graphEdges){
        for(var node:graphNodes)nodes.save(new GraphNode(node.id(),actor.tenantId(),graph.getId(),node.nodeKey(),node.name(),node.kind(),node.serviceFieldKey(),node.positionX(),node.positionY()));
        nodes.flush();for(var node:graphNodes)for(var port:node.ports())ports.save(new GraphPort(port.id(),actor.tenantId(),graph.getId(),node.id(),port.direction(),
                port.portKey(),port.label(),port.type().builtinType(),port.type().customTypeId(),port.required(),port.position()));
        ports.flush();for(var edge:graphEdges)edges.save(new GraphEdge(edge.id(),actor.tenantId(),graph.getId(),edge.sourcePortId(),edge.targetPortId()));edges.flush();
    }
    private void deleteChildren(UUID graphId){edges.deleteAllByGraphId(graphId);edges.flush();ports.deleteAllByGraphId(graphId);ports.flush();nodes.deleteAllByGraphId(graphId);nodes.flush();}
    private DecisionGraph requireGraph(RuleCraftPrincipal actor,UUID projectId,UUID serviceId){return graphs.findByDecisionServiceIdAndTenantIdAndProjectId(serviceId,actor.tenantId(),projectId)
            .orElseThrow(()->new DomainProblemException(HttpStatus.NOT_FOUND,"RC-GRAPH-NOT-FOUND","This decision service does not have a graph yet."));}
    private void validateTypeRef(RuleCraftPrincipal actor,UUID projectId,TypeRefCommand ref){
        if(ref==null||(ref.builtinType()==null)==(ref.customTypeId()==null))bad("RC-GRAPH-TYPE","Choose exactly one built-in or project type.");
        if(ref.customTypeId()!=null&&!types.findByIdAndTenantIdAndProjectId(ref.customTypeId(),actor.tenantId(),projectId).isPresent())
            bad("RC-GRAPH-TYPE-NOT-FOUND","A graph port references a type outside this project.");
    }
    private boolean sameType(TypeRefCommand a,TypeRefCommand b){return a!=null&&b!=null&&a.builtinType()==b.builtinType()&&Objects.equals(a.customTypeId(),b.customTypeId());}
    private String requiredKey(String value,String label){if(value==null||!value.matches("[a-z][A-Za-z0-9_]{0,59}"))bad("RC-GRAPH-KEY","The "+label+" key must use lower camel case without spaces.");return value;}
    private String requiredText(String value,int max,String label){if(value==null||value.isBlank()||value.strip().length()>max)bad("RC-GRAPH-FIELD","Provide a valid "+label+".");return value.strip();}
    private <T> List<T> safe(List<T> values){return values==null?List.of():values;}
    private void bad(String code,String message){throw new DomainProblemException(HttpStatus.BAD_REQUEST,code,message);}
    private void conflict(String code,String message){throw new DomainProblemException(HttpStatus.CONFLICT,code,message);}

    private record Aggregate(List<NodeCommand> nodes,List<EdgeCommand> edges){}
    public record TypeRefCommand(BuiltinType builtinType,UUID customTypeId){}
    public record PortCommand(UUID id,GraphPortDirection direction,String portKey,String label,TypeRefCommand type,boolean required,int position){}
    public record NodeCommand(UUID id,String nodeKey,String name,GraphNodeKind kind,String serviceFieldKey,int positionX,int positionY,List<PortCommand> ports){}
    public record EdgeCommand(UUID id,UUID sourcePortId,UUID targetPortId){}
    public record GraphCommand(long version,List<NodeCommand> nodes,List<EdgeCommand> edges){}
    public record VersionCommand(long version){}
    public record ValidationIssue(String severity,String code,String message,UUID nodeId,UUID portId){}
    public record GraphPortView(UUID id,UUID nodeId,GraphPortDirection direction,String portKey,String label,TypeRefCommand type,boolean required,int position){}
    public record GraphNodeView(UUID id,String nodeKey,String name,GraphNodeKind kind,String serviceFieldKey,int positionX,int positionY,List<GraphPortView> ports){}
    public record GraphEdgeView(UUID id,UUID sourcePortId,UUID targetPortId){}
    public record GraphView(UUID id,UUID decisionServiceId,String serviceKey,String serviceName,GraphValidationStatus validationStatus,
            List<ValidationIssue> issues,List<GraphNodeView> nodes,List<GraphEdgeView> edges,Instant updatedAt,long version){}
}
