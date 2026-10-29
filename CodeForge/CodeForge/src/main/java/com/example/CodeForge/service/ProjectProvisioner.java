package com.example.CodeForge.service;

import com.example.CodeForge.models.Project;
import com.example.CodeForge.models.ProjectStatus;
import com.example.CodeForge.repo.ProjectRepository;
import com.example.CodeForge.watcher.FileWatcherService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectProvisioner {
    private static final Logger logger = LoggerFactory.getLogger(ProjectProvisioner.class);

    private final ProjectRepository projects;
    private final NewProjectCreation creator;
    private final FileWatcherService fileWatcher;

    @Async
    public void provision(UUID projectId) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new IllegalStateException("Project not found: " + projectId));
        try {
            Path appDirectory = creator.scaffold(
                    project.getId(),
                    project.getName(),
                    project.getAppType()
            );
            project.setRootPath(appDirectory.toString());
            project.setErrorMessage(null);
            project.setStatus(ProjectStatus.READY);
        } catch (Exception exception) {
            project.setStatus(ProjectStatus.FAILED);
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            project.setErrorMessage(message.substring(0, Math.min(message.length(), 1000)));
        }
        projects.save(project);
        if (project.getStatus() == ProjectStatus.READY) {
            try {
                fileWatcher.watchProject(project.getId(), project.getOwner().getId(), Path.of(project.getRootPath()));
            } catch (IOException exception) {
                logger.error("Project {} was provisioned but file watching could not start", projectId, exception);
            }
        }
    }
}
