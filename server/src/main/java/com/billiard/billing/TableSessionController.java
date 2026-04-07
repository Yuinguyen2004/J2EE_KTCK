package com.billiard.billing;

import com.billiard.billing.dto.EndSessionResponse;
import com.billiard.billing.dto.PauseSessionRequest;
import com.billiard.billing.dto.StartSessionRequest;
import com.billiard.billing.dto.TableSessionResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class TableSessionController {

    private final TableSessionService tableSessionService;

    public TableSessionController(TableSessionService tableSessionService) {
        this.tableSessionService = tableSessionService;
    }

    @PostMapping("/tables/{tableId}/start-session")
    public TableSessionResponse startSession(
            @PathVariable Long tableId,
            @Valid @RequestBody StartSessionRequest request,
            Authentication authentication
    ) {
        return tableSessionService.startSession(tableId, request, authentication.getName());
    }

    @PostMapping("/sessions/{sessionId}/pause")
    public TableSessionResponse pauseSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody PauseSessionRequest request,
            Authentication authentication
    ) {
        return tableSessionService.pauseSession(sessionId, request, authentication.getName());
    }

    @PostMapping("/sessions/{sessionId}/resume")
    public TableSessionResponse resumeSession(
            @PathVariable Long sessionId,
            Authentication authentication
    ) {
        return tableSessionService.resumeSession(sessionId, authentication.getName());
    }

    @PostMapping("/sessions/{sessionId}/end")
    public EndSessionResponse endSession(
            @PathVariable Long sessionId,
            Authentication authentication
    ) {
        return tableSessionService.endSession(sessionId, authentication.getName());
    }

    @GetMapping("/sessions/{sessionId}")
    public TableSessionResponse getSession(@PathVariable Long sessionId) {
        return tableSessionService.getSession(sessionId);
    }

    @GetMapping("/tables/{tableId}/active-session")
    public TableSessionResponse getActiveSessionForTable(@PathVariable Long tableId) {
        return tableSessionService.getActiveSessionForTable(tableId);
    }
}
