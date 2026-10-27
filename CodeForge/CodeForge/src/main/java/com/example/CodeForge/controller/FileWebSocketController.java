package com.example.CodeForge.controller;

import java.io.IOException;
import java.util.Locale;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.example.CodeForge.dto.FileOperationEvent;
import com.example.CodeForge.dto.FileOperationRequest;
import com.example.CodeForge.service.FileService;

@Controller
public class FileWebSocketController {
    private static final String FILES_TOPIC = "/topic/files";

    private final FileService fileService;
    private final SimpMessagingTemplate messagingTemplate;

    public FileWebSocketController(
            FileService fileService,
            SimpMessagingTemplate messagingTemplate) {
        this.fileService = fileService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/files")
    public void handleOperation(FileOperationRequest request) {
        if (request == null || request.getOperation() == null || request.getPath() == null) {
            publishError(request == null ? null : request.getPath(), "operation and path are required");
            return;
        }

        try {
            String operation = request.getOperation().toUpperCase(Locale.ROOT);
            String content = null;

            switch (operation) {
                case "CREATE_FILE" -> fileService.createFile(request.getPath());
                case "CREATE_FOLDER" -> fileService.createFolder(request.getPath());
                case "DELETE" -> fileService.delete(request.getPath());
                case "WRITE" -> {
                    if (request.getContent() == null) {
                        throw new IllegalArgumentException("content is required for WRITE");
                    }
                    fileService.writeFile(request.getPath(), request.getContent());
                }
                case "READ" -> content = fileService.readFile(request.getPath());
                default -> throw new IllegalArgumentException("Unsupported operation: " + request.getOperation());
            }

            messagingTemplate.convertAndSend(
                    FILES_TOPIC,
                    new FileOperationEvent(operation + "_SUCCESS", request.getPath(), content, null));
        } catch (IOException | RuntimeException exception) {
            publishError(request.getPath(), exception.getMessage());
        }
    }

    private void publishError(String path, String error) {
        messagingTemplate.convertAndSend(
                FILES_TOPIC,
                new FileOperationEvent("ERROR", path, null, error));
    }
}