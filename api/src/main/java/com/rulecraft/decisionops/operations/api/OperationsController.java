package com.rulecraft.decisionops.operations.api;

import com.rulecraft.decisionops.identity.security.CurrentUser;
import com.rulecraft.decisionops.operations.OperationsService;
import com.rulecraft.decisionops.operations.OperationsService.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {
    private final OperationsService service;
    public OperationsController(OperationsService service) { this.service = service; }
    @GetMapping OperationsWorkspace workspace(Authentication authentication) {
        return service.workspace(CurrentUser.require(authentication));
    }
    @PostMapping("/recoveries") @ResponseStatus(HttpStatus.CREATED)
    RecoveryView recover(@RequestBody RecoveryCommand command, Authentication authentication) {
        return service.recover(CurrentUser.require(authentication), command);
    }
}
