package com.example.CodeForge.controller;

import java.io.IOException;
import java.util.Locale;
import java.security.Principal;
import java.util.UUID;

import com.example.CodeForge.config.PrincipalUserId;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.example.CodeForge.dto.FileOperationEvent;
import com.example.CodeForge.dto.FileOperationRequest;
import com.example.CodeForge.service.FileService;

@Controller
public class FileWebSocketController {
    private final FileService fileService;
    private final SimpMessagingTemplate messagingTemplate;

    public FileWebSocketController(
            FileService fileService,
            SimpMessagingTemplate messagingTemplate) {
        this.fileService = fileService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/files")
    public void handleOperation(FileOperationRequest request, Principal principal) {
        UUID userId;
        try {
            userId = PrincipalUserId.resolve(principal);
        } catch (ResponseStatusException exception) {
            return;
        }
        if (request == null || request.getProjectId() == null
                || request.getOperation() == null || request.getPath() == null) {
            publishError(userId, request == null ? null : request.getProjectId(),
                    request == null ? null : request.getPath(), "projectId, operation and path are required");
            return;
        }

        try {
            String operation = request.getOperation().toUpperCase(Locale.ROOT);
            String content = null;
            UUID projectId = request.getProjectId();

            switch (operation) {
                case "CREATE_FILE" -> fileService.createFile(projectId, userId, request.getPath());
                case "CREATE_FOLDER" -> fileService.createFolder(projectId, userId, request.getPath());
                case "DELETE" -> fileService.delete(projectId, userId, request.getPath());
                case "WRITE" -> {
                    if (request.getContent() == null) {
                        throw new IllegalArgumentException("content is required for WRITE");
                    }
                    fileService.writeFile(projectId, userId, request.getPath(), request.getContent());
                }
                case "READ" -> content = fileService.readFile(projectId, userId, request.getPath());
                default -> throw new IllegalArgumentException("Unsupported operation: " + request.getOperation());
            }

            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/files",
                    new FileOperationEvent(projectId, operation + "_SUCCESS", request.getPath(), content, null));
        } catch (IOException | RuntimeException exception) {
            publishError(userId, request.getProjectId(), request.getPath(), exception.getMessage());
        }
    }

    private void publishError(UUID userId, UUID projectId, String path, String error) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/files",
                new FileOperationEvent(projectId, "ERROR", path, null, error));
    }
}