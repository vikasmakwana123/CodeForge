package com.example.CodeForge.repo;

import com.example.CodeForge.models.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByIdAndOwnerId(UUID id, UUID ownerId);
}
