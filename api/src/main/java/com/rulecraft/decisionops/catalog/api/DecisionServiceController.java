package com.rulecraft.decisionops.catalog.api;

import com.rulecraft.decisionops.catalog.CatalogService;
import com.rulecraft.decisionops.catalog.CatalogService.DecisionServiceCommand;
import com.rulecraft.decisionops.catalog.CatalogService.DecisionServiceView;
import com.rulecraft.decisionops.catalog.domain.DecisionServiceStatus;
import com.rulecraft.decisionops.identity.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/decision-services")
public class DecisionServiceController {
    private final CatalogService service;
    public DecisionServiceController(CatalogService service){this.service=service;}
    @GetMapping List<DecisionServiceView> list(@PathVariable UUID projectId,Authentication authentication){return service.listServices(CurrentUser.require(authentication),projectId);}
    @GetMapping("/{serviceId}") DecisionServiceView get(@PathVariable UUID projectId,@PathVariable UUID serviceId,Authentication authentication){return service.getService(CurrentUser.require(authentication),projectId,serviceId);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) DecisionServiceView create(@PathVariable UUID projectId,@RequestBody DecisionServiceCommand body,Authentication authentication){return service.createService(CurrentUser.require(authentication),projectId,body);}
    @PutMapping("/{serviceId}") DecisionServiceView update(@PathVariable UUID projectId,@PathVariable UUID serviceId,@RequestBody DecisionServiceCommand body,Authentication authentication){return service.updateService(CurrentUser.require(authentication),projectId,serviceId,body);}
    @PatchMapping("/{serviceId}/status") DecisionServiceView status(@PathVariable UUID projectId,@PathVariable UUID serviceId,@RequestBody StatusCommand body,Authentication authentication){return service.changeServiceStatus(CurrentUser.require(authentication),projectId,serviceId,body.status(),body.version());}
    public record StatusCommand(DecisionServiceStatus status,long version){}
}
