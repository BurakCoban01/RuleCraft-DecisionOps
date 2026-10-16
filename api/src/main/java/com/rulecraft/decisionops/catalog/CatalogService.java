package com.rulecraft.decisionops.catalog;

import com.rulecraft.decisionops.audit.AuditService;
import com.rulecraft.decisionops.catalog.domain.*;
import com.rulecraft.decisionops.catalog.persistence.*;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.ProjectAccessPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private static final int MAX_FIELDS = 100;
    private final TypeDefinitionRepository types;
    private final TypeFieldRepository typeFields;
    private final TypeEnumValueRepository enumValues;
    private final DecisionServiceRepository services;
    private final DecisionServiceFieldRepository serviceFields;
    private final ProjectAccessPolicy access;
    private final AuditService audit;
    private final Clock clock;

    public CatalogService(TypeDefinitionRepository types, TypeFieldRepository typeFields,
            TypeEnumValueRepository enumValues, DecisionServiceRepository services,
            DecisionServiceFieldRepository serviceFields, ProjectAccessPolicy access,
            AuditService audit, Clock clock) {
        this.types=types; this.typeFields=typeFields; this.enumValues=enumValues; this.services=services;
        this.serviceFields=serviceFields; this.access=access; this.audit=audit; this.clock=clock;
    }

    @Transactional(readOnly=true)
    public List<TypeView> listTypes(RuleCraftPrincipal actor,UUID projectId){
        access.requireVisible(actor,projectId);
        return types.findAllByTenantIdAndProjectIdOrderByNameAsc(actor.tenantId(),projectId).stream().map(this::typeView).toList();
    }

    @Transactional(readOnly=true)
    public TypeView getType(RuleCraftPrincipal actor,UUID projectId,UUID typeId){
        access.requireVisible(actor,projectId); return typeView(requireType(actor,projectId,typeId));
    }

    @Transactional
    public TypeView createType(RuleCraftPrincipal actor,UUID projectId,TypeCommand command){
        access.requireEdit(actor,projectId); validateTypeCommand(command);
        var key=normalizeKey(command.typeKey());
        if(types.existsByProjectIdAndTypeKey(projectId,key)) conflict("RC-TYPE-KEY-EXISTS","That type key is already in use.");
        var now=Instant.now(clock);
        var entity=new TypeDefinition(actor.tenantId(),projectId,key,command.name(),command.description(),command.kind(),actor.userId(),now);
        validateReferences(actor,projectId,entity.getId(),references(command));
        applyType(entity,command,now); types.saveAndFlush(entity); replaceTypeChildren(actor,entity,command);
        audit.record(actor.tenantId(),actor.userId(),"TYPE_CREATED","TYPE_DEFINITION",entity.getId(),Map.of("typeKey",key,"kind",command.kind().name()));
        return typeView(entity);
    }

    @Transactional
    public TypeView updateType(RuleCraftPrincipal actor,UUID projectId,UUID typeId,TypeCommand command){
        access.requireEdit(actor,projectId); validateTypeCommand(command);
        var entity=requireType(actor,projectId,typeId); requireVersion(entity.getVersion(),command.version());
        var key=normalizeKey(command.typeKey());
        if(!entity.getTypeKey().equals(key)&&types.existsByProjectIdAndTypeKey(projectId,key)) conflict("RC-TYPE-KEY-EXISTS","That type key is already in use.");
        validateReferences(actor,projectId,typeId,references(command)); applyType(entity,command,Instant.now(clock));
        types.saveAndFlush(entity); replaceTypeChildren(actor,entity,command);
        audit.record(actor.tenantId(),actor.userId(),"TYPE_UPDATED","TYPE_DEFINITION",typeId,Map.of("version",entity.getVersion()));
        return typeView(entity);
    }

    @Transactional
    public void deleteType(RuleCraftPrincipal actor,UUID projectId,UUID typeId,long version){
        access.requireEdit(actor,projectId); var entity=requireType(actor,projectId,typeId); requireVersion(entity.getVersion(),version);
        long references=typeFields.countByCustomTypeId(typeId)+serviceFields.countByCustomTypeId(typeId)+types.countByItemCustomTypeId(typeId);
        if(references>0) conflict("RC-TYPE-IN-USE","Remove references to this type before deleting it.");
        typeFields.deleteAllByTypeDefinitionId(typeId); enumValues.deleteAllByTypeDefinitionId(typeId); types.delete(entity);
        audit.record(actor.tenantId(),actor.userId(),"TYPE_DELETED","TYPE_DEFINITION",typeId,Map.of("typeKey",entity.getTypeKey()));
    }

    @Transactional(readOnly=true)
    public List<DecisionServiceView> listServices(RuleCraftPrincipal actor,UUID projectId){
        access.requireVisible(actor,projectId);
        return services.findAllByTenantIdAndProjectIdOrderByNameAsc(actor.tenantId(),projectId).stream().map(this::serviceView).toList();
    }

    @Transactional(readOnly=true)
    public DecisionServiceView getService(RuleCraftPrincipal actor,UUID projectId,UUID serviceId){
        access.requireVisible(actor,projectId); return serviceView(requireService(actor,projectId,serviceId));
    }

    @Transactional
    public DecisionServiceView createService(RuleCraftPrincipal actor,UUID projectId,DecisionServiceCommand command){
        access.requireEdit(actor,projectId); validateServiceCommand(command);
        var key=normalizeKey(command.serviceKey());
        if(services.existsByProjectIdAndServiceKey(projectId,key)) conflict("RC-SERVICE-KEY-EXISTS","That decision-service key is already in use.");
        validateReferences(actor,projectId,null,references(command)); var now=Instant.now(clock);
        var entity=services.saveAndFlush(new DecisionService(actor.tenantId(),projectId,key,command.name(),command.description(),actor.userId(),now));
        replaceServiceFields(actor,entity,command);
        audit.record(actor.tenantId(),actor.userId(),"DECISION_SERVICE_CREATED","DECISION_SERVICE",entity.getId(),Map.of("serviceKey",key));
        return serviceView(entity);
    }

    @Transactional
    public DecisionServiceView updateService(RuleCraftPrincipal actor,UUID projectId,UUID serviceId,DecisionServiceCommand command){
        access.requireEdit(actor,projectId); validateServiceCommand(command); var entity=requireService(actor,projectId,serviceId);
        requireVersion(entity.getVersion(),command.version()); var key=normalizeKey(command.serviceKey());
        if(!entity.getServiceKey().equals(key)&&services.existsByProjectIdAndServiceKey(projectId,key)) conflict("RC-SERVICE-KEY-EXISTS","That decision-service key is already in use.");
        validateReferences(actor,projectId,null,references(command)); entity.update(key,command.name(),command.description(),Instant.now(clock));
        services.saveAndFlush(entity); replaceServiceFields(actor,entity,command);
        audit.record(actor.tenantId(),actor.userId(),"DECISION_SERVICE_UPDATED","DECISION_SERVICE",serviceId,Map.of("version",entity.getVersion()));
        return serviceView(entity);
    }

    @Transactional
    public DecisionServiceView changeServiceStatus(RuleCraftPrincipal actor,UUID projectId,UUID serviceId,DecisionServiceStatus status,long version){
        access.requireEdit(actor,projectId); if(status==null)bad("RC-SERVICE-STATUS","Choose a valid service status."); var entity=requireService(actor,projectId,serviceId); requireVersion(entity.getVersion(),version);
        entity.changeStatus(status,Instant.now(clock)); services.saveAndFlush(entity);
        audit.record(actor.tenantId(),actor.userId(),"DECISION_SERVICE_STATUS_CHANGED","DECISION_SERVICE",serviceId,Map.of("status",status.name()));
        return serviceView(entity);
    }

    private void applyType(TypeDefinition entity,TypeCommand command,Instant now){
        var item=command.itemType(); entity.update(command.typeKey(),command.name(),command.description(),command.kind(),
                item==null?null:item.builtinType(),item==null?null:item.customTypeId(),now);
    }

    private void replaceTypeChildren(RuleCraftPrincipal actor,TypeDefinition entity,TypeCommand command){
        typeFields.deleteAllByTypeDefinitionId(entity.getId()); enumValues.deleteAllByTypeDefinitionId(entity.getId());
        typeFields.flush(); enumValues.flush();
        if(command.kind()==TypeKind.OBJECT){int position=0;for(var field:command.fields())typeFields.save(new TypeField(actor.tenantId(),entity.getId(),field.fieldKey(),field.label(),field.type().builtinType(),field.type().customTypeId(),field.required(),position++));}
        if(command.kind()==TypeKind.ENUM){int position=0;for(var value:command.values())enumValues.save(new TypeEnumValue(actor.tenantId(),entity.getId(),value.value(),value.label(),position++));}
        typeFields.flush(); enumValues.flush();
    }

    private void replaceServiceFields(RuleCraftPrincipal actor,DecisionService entity,DecisionServiceCommand command){
        serviceFields.deleteAllByDecisionServiceId(entity.getId()); serviceFields.flush();
        int position=0;for(var field:command.inputs())serviceFields.save(serviceField(actor,entity,FieldDirection.INPUT,field,position++));
        position=0;for(var field:command.outputs())serviceFields.save(serviceField(actor,entity,FieldDirection.OUTPUT,field,position++)); serviceFields.flush();
    }

    private DecisionServiceField serviceField(RuleCraftPrincipal actor,DecisionService entity,FieldDirection direction,FieldCommand field,int position){
        return new DecisionServiceField(actor.tenantId(),entity.getId(),direction,field.fieldKey(),field.label(),field.type().builtinType(),field.type().customTypeId(),field.required(),position);
    }

    private void validateTypeCommand(TypeCommand command){
        if(command==null||command.kind()==null) bad("RC-TYPE-INVALID","Choose a type kind.");
        validateKey(command.typeKey(),"type"); required(command.name(),160,"type name"); optional(command.description(),600,"type description");
        var fields=safe(command.fields());var values=safe(command.values());
        if(command.kind()==TypeKind.OBJECT){if(fields.isEmpty())bad("RC-TYPE-OBJECT-EMPTY","Object types require at least one field.");if(!values.isEmpty()||command.itemType()!=null)bad("RC-TYPE-SHAPE","Object types accept fields only.");validateFields(fields);}
        if(command.kind()==TypeKind.ENUM){if(values.isEmpty())bad("RC-TYPE-ENUM-EMPTY","Enum types require at least one value.");if(!fields.isEmpty()||command.itemType()!=null)bad("RC-TYPE-SHAPE","Enum types accept values only.");validateEnumValues(values);}
        if(command.kind()==TypeKind.LIST){if(command.itemType()==null)bad("RC-TYPE-LIST-ITEM","List types require an item type.");if(!fields.isEmpty()||!values.isEmpty())bad("RC-TYPE-SHAPE","List types accept an item type only.");validateRef(command.itemType());}
    }

    private void validateServiceCommand(DecisionServiceCommand command){
        if(command==null)bad("RC-SERVICE-INVALID","Decision service details are required."); validateKey(command.serviceKey(),"decision-service"); required(command.name(),160,"service name");optional(command.description(),600,"service description");
        var inputs=safe(command.inputs());var outputs=safe(command.outputs()); if(outputs.isEmpty())bad("RC-SERVICE-OUTPUT-EMPTY","A decision service requires at least one output.");
        validateFields(inputs);validateFields(outputs);
    }

    private void validateFields(List<FieldCommand> fields){
        if(fields.size()>MAX_FIELDS)bad("RC-FIELD-LIMIT","At most 100 fields are allowed.");var keys=new HashSet<String>();
        for(var field:fields){if(field==null||field.fieldKey()==null||!field.fieldKey().matches("[a-z][A-Za-z0-9_]{0,59}"))bad("RC-FIELD-KEY","Field keys use lower camel case without spaces.");
            if(!keys.add(field.fieldKey()))bad("RC-FIELD-DUPLICATE","Field keys must be unique.");required(field.label(),160,"field label");validateRef(field.type());}
    }

    private void validateEnumValues(List<EnumValueCommand> values){
        if(values.size()>MAX_FIELDS)bad("RC-ENUM-LIMIT","At most 100 enum values are allowed.");var keys=new HashSet<String>();
        for(var value:values){required(value.value(),80,"enum value");required(value.label(),160,"enum label");if(!keys.add(value.value()))bad("RC-ENUM-DUPLICATE","Enum values must be unique.");}
    }

    private void validateRef(TypeRefCommand ref){
        if(ref==null||(ref.builtinType()==null)==(ref.customTypeId()==null))bad("RC-TYPE-REF","Choose exactly one built-in or custom type.");
    }

    private void validateReferences(RuleCraftPrincipal actor,UUID projectId,UUID currentTypeId,List<TypeRefCommand> refs){
        for(var ref:refs){validateRef(ref);if(ref.customTypeId()!=null){
            var target=types.findByIdAndTenantIdAndProjectId(ref.customTypeId(),actor.tenantId(),projectId).orElseThrow(()->new DomainProblemException(HttpStatus.BAD_REQUEST,"RC-TYPE-REF-NOT-FOUND","A referenced custom type does not exist in this project."));
            if(currentTypeId!=null&&(target.getId().equals(currentTypeId)||dependsOn(target.getId(),currentTypeId,new HashSet<>())))bad("RC-TYPE-CYCLE","Custom types cannot contain circular references.");
        }}
    }

    private boolean dependsOn(UUID candidate,UUID sought,Set<UUID> visited){
        if(!visited.add(candidate))return false;var type=types.findById(candidate).orElse(null);if(type==null)return false;
        var refs=new ArrayList<UUID>();if(type.getItemCustomTypeId()!=null)refs.add(type.getItemCustomTypeId());
        typeFields.findAllByTypeDefinitionIdOrderByPositionAsc(candidate).stream().map(TypeField::getCustomTypeId).filter(java.util.Objects::nonNull).forEach(refs::add);
        return refs.stream().anyMatch(ref->ref.equals(sought)||dependsOn(ref,sought,visited));
    }

    private List<TypeRefCommand> references(TypeCommand command){var refs=new ArrayList<TypeRefCommand>();if(command.itemType()!=null)refs.add(command.itemType());safe(command.fields()).forEach(f->refs.add(f.type()));return refs;}
    private List<TypeRefCommand> references(DecisionServiceCommand command){var refs=new ArrayList<TypeRefCommand>();safe(command.inputs()).forEach(f->refs.add(f.type()));safe(command.outputs()).forEach(f->refs.add(f.type()));return refs;}

    private TypeView typeView(TypeDefinition type){
        var fields=typeFields.findAllByTypeDefinitionIdOrderByPositionAsc(type.getId()).stream().map(f->new FieldView(f.getFieldKey(),f.getLabel(),refView(f.getBuiltinType(),f.getCustomTypeId()),f.isRequired())).toList();
        var values=enumValues.findAllByTypeDefinitionIdOrderByPositionAsc(type.getId()).stream().map(v->new EnumValueView(v.getValueKey(),v.getLabel())).toList();
        return new TypeView(type.getId(),type.getTypeKey(),type.getName(),type.getDescription(),type.getKind(),refView(type.getItemBuiltinType(),type.getItemCustomTypeId()),fields,values,type.getUpdatedAt(),type.getVersion());
    }

    private DecisionServiceView serviceView(DecisionService service){
        var all=serviceFields.findAllByDecisionServiceIdOrderByDirectionAscPositionAsc(service.getId());
        var inputs=all.stream().filter(f->f.getDirection()==FieldDirection.INPUT).map(this::fieldView).toList();
        var outputs=all.stream().filter(f->f.getDirection()==FieldDirection.OUTPUT).map(this::fieldView).toList();
        return new DecisionServiceView(service.getId(),service.getServiceKey(),service.getName(),service.getDescription(),service.getStatus(),inputs,outputs,service.getUpdatedAt(),service.getVersion());
    }

    private FieldView fieldView(DecisionServiceField f){return new FieldView(f.getFieldKey(),f.getLabel(),refView(f.getBuiltinType(),f.getCustomTypeId()),f.isRequired());}
    private TypeRefView refView(BuiltinType builtin,UUID customId){if(builtin==null&&customId==null)return null;if(builtin!=null)return new TypeRefView(builtin,null,null,null);var custom=types.findById(customId).orElse(null);return new TypeRefView(null,customId,custom==null?null:custom.getTypeKey(),custom==null?"Unavailable type":custom.getName());}
    private TypeDefinition requireType(RuleCraftPrincipal actor,UUID projectId,UUID id){return types.findByIdAndTenantIdAndProjectId(id,actor.tenantId(),projectId).orElseThrow(()->new DomainProblemException(HttpStatus.NOT_FOUND,"RC-TYPE-NOT-FOUND","Type definition not found."));}
    private DecisionService requireService(RuleCraftPrincipal actor,UUID projectId,UUID id){return services.findByIdAndTenantIdAndProjectId(id,actor.tenantId(),projectId).orElseThrow(()->new DomainProblemException(HttpStatus.NOT_FOUND,"RC-SERVICE-NOT-FOUND","Decision service not found."));}
    private void requireVersion(long actual,Long expected){if(expected==null||actual!=expected)conflict("RC-DATA-STALE","The record changed. Refresh and try again.");}
    private String normalizeKey(String value){return value.strip().toUpperCase(Locale.ROOT);}
    private void validateKey(String value,String label){if(value==null||!value.matches("[A-Za-z][A-Za-z0-9_-]{1,39}"))bad("RC-KEY-INVALID","The "+label+" key must be 2–40 letters, numbers, underscores, or hyphens.");}
    private void required(String value,int max,String label){if(value==null||value.isBlank()||value.strip().length()>max)bad("RC-FIELD-INVALID","Provide a valid "+label+".");}
    private void optional(String value,int max,String label){if(value!=null&&value.strip().length()>max)bad("RC-FIELD-INVALID","Provide a valid "+label+".");}
    private <T> List<T> safe(List<T> values){return values==null?List.of():values;}
    private void bad(String code,String message){throw new DomainProblemException(HttpStatus.BAD_REQUEST,code,message);}
    private void conflict(String code,String message){throw new DomainProblemException(HttpStatus.CONFLICT,code,message);}

    public record TypeRefCommand(BuiltinType builtinType,UUID customTypeId){}
    public record FieldCommand(String fieldKey,String label,TypeRefCommand type,boolean required){}
    public record EnumValueCommand(String value,String label){}
    public record TypeCommand(String typeKey,String name,String description,TypeKind kind,TypeRefCommand itemType,List<FieldCommand> fields,List<EnumValueCommand> values,Long version){}
    public record DecisionServiceCommand(String serviceKey,String name,String description,List<FieldCommand> inputs,List<FieldCommand> outputs,Long version){}
    public record TypeRefView(BuiltinType builtinType,UUID customTypeId,String customTypeKey,String displayName){}
    public record FieldView(String fieldKey,String label,TypeRefView type,boolean required){}
    public record EnumValueView(String value,String label){}
    public record TypeView(UUID id,String typeKey,String name,String description,TypeKind kind,TypeRefView itemType,List<FieldView> fields,List<EnumValueView> values,Instant updatedAt,long version){}
    public record DecisionServiceView(UUID id,String serviceKey,String name,String description,DecisionServiceStatus status,List<FieldView> inputs,List<FieldView> outputs,Instant updatedAt,long version){}
}
