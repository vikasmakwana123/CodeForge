package com.example.CodeForge.service;

import com.example.CodeForge.models.Project;
import com.example.CodeForge.models.ProjectStatus;
import com.example.CodeForge.repo.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectDeletionService {
    private final ProjectService projectService;
    private final ProjectRepository projects;
    private final NewProjectCreation creator;

    public void deleteOwned(UUID projectId, UUID userId) {
        Project project = projectService.getOwned(projectId, userId);
        if (project.getStatus() == ProjectStatus.CREATING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A project cannot be deleted while provisioning");
        }

        try {
            creator.deleteProjectResources(project);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Project cleanup was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not clean project resources", exception);
        }
        projects.delete(project);
    }
}
