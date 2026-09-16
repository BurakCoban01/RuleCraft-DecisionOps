package com.rulecraft.decisionops.tables.api;

import com.rulecraft.decisionops.identity.security.CurrentUser;
import com.rulecraft.decisionops.tables.DecisionTableService;
import com.rulecraft.decisionops.tables.DecisionTableService.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/decision-services/{serviceId}/graph")
public class DecisionTableController {
    private final DecisionTableService service;public DecisionTableController(DecisionTableService service){this.service=service;}
    @GetMapping("/decision-tables") List<TableSummary> summaries(@PathVariable UUID projectId,@PathVariable UUID serviceId,Authentication authentication){return service.summaries(CurrentUser.require(authentication),projectId,serviceId);}
    @GetMapping("/nodes/{nodeId}/decision-table") ResponseEntity<TableView> get(@PathVariable UUID projectId,@PathVariable UUID serviceId,@PathVariable UUID nodeId,Authentication authentication){return service.find(CurrentUser.require(authentication),projectId,serviceId,nodeId).map(ResponseEntity::ok).orElseGet(()->ResponseEntity.noContent().build());}
    @PostMapping("/nodes/{nodeId}/decision-table") @ResponseStatus(HttpStatus.CREATED) TableView initialize(@PathVariable UUID projectId,@PathVariable UUID serviceId,@PathVariable UUID nodeId,Authentication authentication){return service.initialize(CurrentUser.require(authentication),projectId,serviceId,nodeId);}
    @PutMapping("/nodes/{nodeId}/decision-table") TableView update(@PathVariable UUID projectId,@PathVariable UUID serviceId,@PathVariable UUID nodeId,@RequestBody TableCommand body,Authentication authentication){return service.update(CurrentUser.require(authentication),projectId,serviceId,nodeId,body);}
}
