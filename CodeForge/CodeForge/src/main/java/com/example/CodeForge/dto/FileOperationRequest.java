package com.example.CodeForge.dto;

import java.util.UUID;

public class FileOperationRequest {
    private UUID projectId;
    private String operation;
    private String path;
    private String content;

    public String getOperation() {
        return operation;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}