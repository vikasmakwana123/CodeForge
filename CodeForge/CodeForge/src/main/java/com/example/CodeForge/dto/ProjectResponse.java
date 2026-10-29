package com.example.CodeForge.dto;

import com.example.CodeForge.models.Project;
import com.example.CodeForge.models.ProjectStatus;

import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String appType,
        ProjectStatus status,
        String errorMessage,
        Integer hostPort
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getAppType(),
                project.getStatus(),
                project.getErrorMessage(),
                project.getHostPort()
        );
    }
}
