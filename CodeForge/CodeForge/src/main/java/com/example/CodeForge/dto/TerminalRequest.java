package com.example.CodeForge.dto;

import java.util.UUID;

public record TerminalRequest(
        String action,
        UUID projectId,
        UUID connectionId,
        String data,
        Integer columns,
        Integer rows
) {
    public TerminalRequest(String action, UUID projectId, UUID connectionId, String data) {
        this(action, projectId, connectionId, data, null, null);
    }
}
