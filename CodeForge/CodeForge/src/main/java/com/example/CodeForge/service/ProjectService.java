package com.example.CodeForge.service;

import com.example.CodeForge.models.Project;
import com.example.CodeForge.models.ProjectStatus;
import com.example.CodeForge.repo.ProjectRepository;
import com.example.CodeForge.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "react", "vue", "angular", "next", "nextjs", "next.js", "node", "nodejs",
            "spring", "springboot", "spring-boot", "vite"
    );

    private final ProjectRepository projects;
    private final UserRepository users;

    @Transactional
    public Project createPending(UUID userId, String name, String type) {
        if (name == null || !name.matches("^[a-zA-Z][a-zA-Z0-9-]{0,39}$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Name must be letters, digits, '-' (max 40)"
            );
        }
        if (type == null || !SUPPORTED_TYPES.contains(type.trim().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported project type: " + type);
        }

        Project project = new Project();
        project.setOwner(users.getReferenceById(userId));
        project.setName(name);
        project.setAppType(type.trim().toLowerCase(Locale.ROOT));
        project.setStatus(ProjectStatus.CREATING);
        return projects.save(project);
    }

    @Transactional(readOnly = true)
    public Project getOwned(UUID projectId, UUID userId) {
        return projects.findByIdAndOwnerId(projectId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
