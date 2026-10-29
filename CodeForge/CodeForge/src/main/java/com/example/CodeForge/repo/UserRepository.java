package com.example.CodeForge.repo;

import com.example.CodeForge.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);
}
