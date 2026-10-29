package com.example.CodeForge.dto;

import java.util.UUID;

public record TerminalEvent(UUID projectId, UUID connectionId, String type, String data, String error) {
}
