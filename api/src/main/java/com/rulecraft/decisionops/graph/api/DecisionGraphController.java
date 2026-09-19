package com.rulecraft.decisionops.graph.api;

import com.rulecraft.decisionops.graph.DecisionGraphService;
import com.rulecraft.decisionops.graph.DecisionGraphService.GraphCommand;
import com.rulecraft.decisionops.graph.DecisionGraphService.GraphView;
import com.rulecraft.decisionops.graph.DecisionGraphService.VersionCommand;
import com.rulecraft.decisionops.identity.security.CurrentUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/decision-services/{serviceId}/graph")
public class DecisionGraphController {
    private final DecisionGraphService service;
    public DecisionGraphController(DecisionGraphService service){this.service=service;}
    @GetMapping ResponseEntity<GraphView> get(@PathVariable UUID projectId,@PathVariable UUID serviceId,Authentication authentication){
        return service.find(CurrentUser.require(authentication),projectId,serviceId).map(ResponseEntity::ok).orElseGet(()->ResponseEntity.noContent().build());}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) GraphView initialize(@PathVariable UUID projectId,@PathVariable UUID serviceId,Authentication authentication){
        return service.initialize(CurrentUser.require(authentication),projectId,serviceId);}
    @PutMapping GraphView update(@PathVariable UUID projectId,@PathVariable UUID serviceId,@RequestBody GraphCommand body,Authentication authentication){
        return service.update(CurrentUser.require(authentication),projectId,serviceId,body);}
    @PostMapping("/contract-sync") GraphView synchronize(@PathVariable UUID projectId,@PathVariable UUID serviceId,@RequestBody VersionCommand body,Authentication authentication){
        return service.synchronizeContract(CurrentUser.require(authentication),projectId,serviceId,body.version());}
}
