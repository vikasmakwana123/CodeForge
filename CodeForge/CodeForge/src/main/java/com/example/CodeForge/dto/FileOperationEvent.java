package com.example.CodeForge.dto;

import java.util.UUID;

public class FileOperationEvent {
    private UUID projectId;
    private String type;
    private String path;
    private String content;
    private String error;

    public FileOperationEvent() {
    }

    public FileOperationEvent(String type, String path) {
        this.type = type;
        this.path = path;
    }

    public FileOperationEvent(String type, String path, String content, String error) {
        this(null, type, path, content, error);
    }

    public FileOperationEvent(UUID projectId, String type, String path, String content, String error) {
        this.projectId = projectId;
        this.type = type;
        this.path = path;
        this.content = content;
        this.error = error;
    }

    public String getType() {
        return type;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getPath() {
        return path;
    }

    public String getContent() {
        return content;
    }

    public String getError() {
        return error;
    }
}