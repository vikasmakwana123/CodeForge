package com.example.CodeForge.controller;

import com.example.CodeForge.dto.ProjectRequest;
import com.example.CodeForge.dto.ProjectResponse;
import com.example.CodeForge.models.Project;
import com.example.CodeForge.config.PrincipalUserId;
import com.example.CodeForge.service.NewProjectCreation;
import com.example.CodeForge.service.ProjectDeletionService;
import com.example.CodeForge.service.ProjectProvisioner;
import com.example.CodeForge.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;
    private final ProjectDeletionService deletionService;
    private final ProjectProvisioner provisioner;
    private final NewProjectCreation newProjectCreation;

    public ProjectController(
            ProjectService projectService,
            ProjectDeletionService deletionService,
            ProjectProvisioner provisioner,
            NewProjectCreation newProjectCreation
    ) {
        this.projectService = projectService;
        this.deletionService = deletionService;
        this.provisioner = provisioner;
        this.newProjectCreation = newProjectCreation;
    }

    @PostMapping({"", "/create"})
    public ResponseEntity<ProjectResponse> createProject(
            @RequestBody ProjectRequest request,
            Principal principal
    ) {
        Project project = projectService.createPending(
                PrincipalUserId.resolve(principal),
                request.getName(),
                request.getType()
        );
        provisioner.provision(project.getId());
        return ResponseEntity.accepted().body(ProjectResponse.from(project));
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getProject(
            @PathVariable UUID projectId,
            Principal principal
    ) {
        return ProjectResponse.from(projectService.getOwned(projectId, PrincipalUserId.resolve(principal)));
    }

    @GetMapping("/{projectId}/tree")
    public NewProjectCreation.FileNode getProjectTree(
            @PathVariable UUID projectId,
            Principal principal
    ) {
        return newProjectCreation.directoryTree(projectId, PrincipalUserId.resolve(principal));
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable UUID projectId, Principal principal) {
        deletionService.deleteOwned(projectId, PrincipalUserId.resolve(principal));
    }
}
