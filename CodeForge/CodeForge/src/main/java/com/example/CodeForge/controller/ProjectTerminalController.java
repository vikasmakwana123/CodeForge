package com.example.CodeForge.controller;

import com.example.CodeForge.config.PrincipalUserId;
import com.example.CodeForge.dto.TerminalEvent;
import com.example.CodeForge.dto.TerminalRequest;
import com.example.CodeForge.service.ProjectTerminalService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Locale;
import java.util.UUID;

@Controller
public class ProjectTerminalController {
    private final ProjectTerminalService terminalService;
    private final SimpMessagingTemplate messagingTemplate;

    public ProjectTerminalController(
            ProjectTerminalService terminalService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.terminalService = terminalService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/terminal")
    public void handle(TerminalRequest request, Principal principal, SimpMessageHeaderAccessor accessor) {
        UUID userId;
        try {
            userId = PrincipalUserId.resolve(principal);
        } catch (RuntimeException exception) {
            return;
        }
        if (request == null || request.projectId() == null || request.connectionId() == null
                || request.action() == null) {
            sendError(userId, request, "projectId, connectionId, and action are required");
            return;
        }

        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            sendError(userId, request, "Terminal connection is not initialized");
            return;
        }

        try {
            switch (request.action().toUpperCase(Locale.ROOT)) {
                case "OPEN" -> terminalService.open(
                        request.projectId(), userId, sessionId, request.connectionId(), request.columns(), request.rows()
                );
                case "INPUT" -> terminalService.write(
                        request.projectId(), userId, sessionId, request.connectionId(), request.data()
                );
                case "INTERRUPT" -> terminalService.interrupt(
                        request.projectId(), userId, sessionId, request.connectionId()
                );
                case "RESIZE" -> terminalService.resize(
                        request.projectId(), userId, sessionId, request.connectionId(), request.columns(), request.rows()
                );
                case "CLOSE" -> terminalService.close(sessionId, request.connectionId());
                default -> sendError(userId, request, "Unsupported terminal action");
            }
        } catch (RuntimeException exception) {
            sendError(userId, request, exception.getMessage() == null
                    ? "Terminal request failed"
                    : exception.getMessage());
        }
    }

    private void sendError(UUID userId, TerminalRequest request, String error) {
        if (request == null || request.connectionId() == null) {
            return;
        }
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/terminal/" + request.connectionId(),
                new TerminalEvent(request.projectId(), request.connectionId(), "ERROR", null, error)
        );
    }
}
