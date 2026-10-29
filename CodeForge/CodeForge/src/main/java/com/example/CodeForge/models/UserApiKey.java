package com.example.CodeForge.models;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_api_keys",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "provider"}))
@Getter @Setter @NoArgsConstructor
public class UserApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;          // GROQ, OPENAI, GEMINI, ANTHROPIC

    @JsonIgnore
    @ToString.Exclude
    @Column(nullable = false, length = 1000)
    private String encryptedKey;        // AES-GCM output from CryptoService

    private String last4;               // for masked display
    private String defaultModel;
    private Instant createdAt = Instant.now();
}
