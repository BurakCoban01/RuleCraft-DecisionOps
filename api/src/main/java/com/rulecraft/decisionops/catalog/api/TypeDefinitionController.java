package com.rulecraft.decisionops.catalog.api;

import com.rulecraft.decisionops.catalog.CatalogService;
import com.rulecraft.decisionops.catalog.CatalogService.TypeCommand;
import com.rulecraft.decisionops.catalog.CatalogService.TypeView;
import com.rulecraft.decisionops.identity.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/types")
public class TypeDefinitionController {
    private final CatalogService service;
    public TypeDefinitionController(CatalogService service){this.service=service;}
    @GetMapping List<TypeView> list(@PathVariable UUID projectId,Authentication authentication){return service.listTypes(CurrentUser.require(authentication),projectId);}
    @GetMapping("/{typeId}") TypeView get(@PathVariable UUID projectId,@PathVariable UUID typeId,Authentication authentication){return service.getType(CurrentUser.require(authentication),projectId,typeId);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) TypeView create(@PathVariable UUID projectId,@RequestBody TypeCommand body,Authentication authentication){return service.createType(CurrentUser.require(authentication),projectId,body);}
    @PutMapping("/{typeId}") TypeView update(@PathVariable UUID projectId,@PathVariable UUID typeId,@RequestBody TypeCommand body,Authentication authentication){return service.updateType(CurrentUser.require(authentication),projectId,typeId,body);}
    @DeleteMapping("/{typeId}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable UUID projectId,@PathVariable UUID typeId,@RequestParam long version,Authentication authentication){service.deleteType(CurrentUser.require(authentication),projectId,typeId,version);}
}
