package com.example.CodeForge.service;

import com.example.CodeForge.models.Project;
import com.example.CodeForge.models.ProjectStatus;
import com.example.CodeForge.models.User;
import com.example.CodeForge.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class ProjectServiceTest {
    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserRepository users;

    @Test
    void createsPendingProjectWithDatabaseIdAndOwner() {
        User user = new User();
        user.setUsername("project-owner");
        user.setEmail("project-owner@example.test");
        user.setPasswordHash("test-hash");
        user = users.saveAndFlush(user);

        Project project = projectService.createPending(user.getId(), "my-project", "react");

        assertNotNull(project.getId());
        assertEquals(ProjectStatus.CREATING, project.getStatus());
        assertEquals(user.getId(), projectService.getOwned(project.getId(), user.getId()).getOwner().getId());
    }
}
