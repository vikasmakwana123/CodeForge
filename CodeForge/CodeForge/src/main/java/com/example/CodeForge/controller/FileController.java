package com.example.CodeForge.controller;

import com.example.CodeForge.config.PrincipalUserId;
import com.example.CodeForge.service.FileService;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/file")
    public ResponseEntity<?> createFile(
            @RequestParam UUID projectId,
            @RequestParam String path,
            Principal principal
    ) throws IOException {
        fileService.createFile(projectId, PrincipalUserId.resolve(principal), path);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "File created successfully",
                "path", path
        ));
    }

    @PostMapping("/folder")
    public ResponseEntity<?> createFolder(
            @RequestParam UUID projectId,
            @RequestParam String path,
            Principal principal
    ) throws IOException {
        fileService.createFolder(projectId, PrincipalUserId.resolve(principal), path);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Folder created successfully",
                "path", path
        ));
    }

    @GetMapping
    public Map<String, String> readFile(
            @RequestParam UUID projectId,
            @RequestParam String path,
            Principal principal
    ) throws IOException {
        return Map.of(
                "path", path,
                "content", fileService.readFile(projectId, PrincipalUserId.resolve(principal), path)
        );
    }

    @GetMapping("/content")
    public ResponseEntity<byte[]> readFileContent(
            @RequestParam UUID projectId,
            @RequestParam String path,
            Principal principal
    ) throws IOException {
        UUID userId = PrincipalUserId.resolve(principal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileService.getContentType(projectId, userId, path)))
                .header("X-Content-Type-Options", "nosniff")
                .body(fileService.readFileBytes(projectId, userId, path));
    }

    @PutMapping
    public Map<String, String> writeFile(
            @RequestParam UUID projectId,
            @RequestParam String path,
            @RequestBody String content,
            Principal principal
    ) throws IOException {
        fileService.writeFile(projectId, PrincipalUserId.resolve(principal), path, content);
        return Map.of("message", "File saved successfully", "path", path);
    }

    @DeleteMapping
    public Map<String, String> delete(
            @RequestParam UUID projectId,
            @RequestParam String path,
            Principal principal
    ) throws IOException {
        fileService.delete(projectId, PrincipalUserId.resolve(principal), path);
        return Map.of("message", "Deleted successfully", "path", path);
    }

    @GetMapping("/list")
    public List<String> listDirectory(
            @RequestParam UUID projectId,
            @RequestParam(defaultValue = ".") String path,
            Principal principal
    ) throws IOException {
        return fileService.listDirectory(projectId, PrincipalUserId.resolve(principal), path);
    }

    @GetMapping("/exists")
    public Map<String, Object> exists(
            @RequestParam UUID projectId,
            @RequestParam String path,
            Principal principal
    ) {
        return Map.of(
                "path", path,
                "exists", fileService.exists(projectId, PrincipalUserId.resolve(principal), path)
        );
    }
}
