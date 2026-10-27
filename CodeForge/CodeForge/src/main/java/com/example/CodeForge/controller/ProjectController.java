package com.example.CodeForge.controller;

import com.example.CodeForge.dto.ProjectRequest;
import com.example.CodeForge.service.NewProjectCreation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private NewProjectCreation newProjectCreation;

    @PostMapping("/create")
    public String createProject(
            @RequestBody ProjectRequest request
    ) {

        return newProjectCreation.createNewProject(
                request.getAppName(),
                request.getAppType()
        );
    }

    @GetMapping("/{projectId}/tree")
    public NewProjectCreation.FileNode getProjectTree(
            @PathVariable String projectId
    ) {

        return newProjectCreation.directoryTree(projectId);
    }
}