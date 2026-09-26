package com.rulecraft.decisionops.runtime.types;

import com.rulecraft.decisionops.catalog.domain.*;
import com.rulecraft.decisionops.catalog.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class TypedValueValidator {
    private final TypeDefinitionRepository types;private final TypeFieldRepository fields;private final TypeEnumValueRepository enumValues;
    public TypedValueValidator(TypeDefinitionRepository types,TypeFieldRepository fields,TypeEnumValueRepository enumValues){this.types=types;this.fields=fields;this.enumValues=enumValues;}
    public boolean valid(JsonNode node,BuiltinType builtin,UUID customId){return valid(node,builtin,customId,new HashSet<>());}
    private boolean valid(JsonNode node,BuiltinType builtin,UUID customId,Set<UUID> path){if(node==null||node.isNull()||(builtin==null)==(customId==null))return false;if(builtin!=null)return switch(builtin){case TEXT->node.isString();case NUMBER->node.isNumber();case BOOLEAN->node.isBoolean();case DATE->node.isString()&&date(node.asString());case DATE_TIME->node.isString()&&dateTime(node.asString());};var type=types.findById(customId).orElse(null);if(type==null||!path.add(type.getId()))return false;if(type.getKind()==TypeKind.ENUM)return node.isString()&&enumValues.findAllByTypeDefinitionIdOrderByPositionAsc(type.getId()).stream().anyMatch(value->value.getValueKey().equals(node.asString()));if(type.getKind()==TypeKind.LIST)return node.isArray()&&node.values().stream().allMatch(item->valid(item,type.getItemBuiltinType(),type.getItemCustomTypeId(),new HashSet<>(path)));var definitions=fields.findAllByTypeDefinitionIdOrderByPositionAsc(type.getId());var allowed=definitions.stream().map(TypeField::getFieldKey).collect(java.util.stream.Collectors.toSet());if(!node.isObject()||!allowed.containsAll(node.propertyNames()))return false;for(var field:definitions){var value=node.get(field.getFieldKey());if(value==null){if(field.isRequired())return false;}else if(!valid(value,field.getBuiltinType(),field.getCustomTypeId(),new HashSet<>(path)))return false;}return true;}
    private boolean date(String value){try{LocalDate.parse(value);return true;}catch(DateTimeException failure){return false;}}private boolean dateTime(String value){try{OffsetDateTime.parse(value);return true;}catch(DateTimeException failure){return false;}}
}
