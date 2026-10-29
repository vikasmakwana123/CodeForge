package com.example.CodeForge.service;

import com.example.CodeForge.models.Project;
import com.example.CodeForge.models.ProjectStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FileService {
    private final ProjectService projectService;

    public FileService(ProjectService projectService) {
        this.projectService = projectService;
    }

    public void createFile(UUID projectId, UUID userId, String relativePath) throws IOException {
        Path filePath = resolveSafePath(projectId, userId, relativePath);
        if (Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "File already exists: " + relativePath);
        }
        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        filePath = resolveSafePath(projectId, userId, relativePath);
        Files.createFile(filePath);
    }

    public void createFolder(UUID projectId, UUID userId, String relativePath) throws IOException {
        Path folderPath = resolveSafePath(projectId, userId, relativePath);
        if (Files.exists(folderPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Folder already exists: " + relativePath);
        }
        Files.createDirectories(folderPath);
    }

    public String readFile(UUID projectId, UUID userId, String relativePath) throws IOException {
        return Files.readString(getExistingFilePath(projectId, userId, relativePath), StandardCharsets.UTF_8);
    }

    public byte[] readFileBytes(UUID projectId, UUID userId, String relativePath) throws IOException {
        return Files.readAllBytes(getExistingFilePath(projectId, userId, relativePath));
    }

    public String getContentType(UUID projectId, UUID userId, String relativePath) throws IOException {
        Path filePath = getExistingFilePath(projectId, userId, relativePath);
        String contentType = Files.probeContentType(filePath);
        if (contentType != null && contentType.startsWith("image/")) {
            return contentType;
        }
        if (filePath.getFileName().toString().toLowerCase().endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    private Path getExistingFilePath(UUID projectId, UUID userId, String relativePath) throws IOException {
        Path filePath = resolveSafePath(projectId, userId, relativePath);
        if (!Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException("File does not exist: " + relativePath);
        }
        if (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Path is not a file: " + relativePath);
        }
        return filePath;
    }

    public void writeFile(UUID projectId, UUID userId, String relativePath, String content) throws IOException {
        Path filePath = resolveSafePath(projectId, userId, relativePath);
        if (!Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)) {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        }
        filePath = resolveSafePath(projectId, userId, relativePath);
        Files.writeString(
                filePath,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    public void delete(UUID projectId, UUID userId, String relativePath) throws IOException {
        Path root = getReadyRoot(projectId, userId);
        Path path = resolveSafePath(root, relativePath);
        if (path.equals(root)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The project root cannot be deleted with a file operation");
        }
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException("Path does not exist: " + relativePath);
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public List<String> listDirectory(UUID projectId, UUID userId, String relativePath) throws IOException {
        Path root = getReadyRoot(projectId, userId);
        Path directory = resolveSafePath(root, relativePath);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException("Directory does not exist: " + relativePath);
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Path is not a directory: " + relativePath);
        }

        List<String> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                if (!Files.isSymbolicLink(child)) {
                    result.add(root.relativize(child).toString().replace("\\", "/"));
                }
            }
        }
        return result;
    }

    public boolean exists(UUID projectId, UUID userId, String relativePath) {
        return Files.exists(resolveSafePath(projectId, userId, relativePath), LinkOption.NOFOLLOW_LINKS);
    }

    private Path resolveSafePath(UUID projectId, UUID userId, String relativePath) {
        return resolveSafePath(getReadyRoot(projectId, userId), relativePath);
    }

    private Path getReadyRoot(UUID projectId, UUID userId) {
        Project project = projectService.getOwned(projectId, userId);
        if (project.getStatus() != ProjectStatus.READY || project.getRootPath() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is not ready");
        }
        Path root = Path.of(project.getRootPath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IllegalStateException("Stored project directory is invalid");
        }
        return root;
    }

    private Path resolveSafePath(Path root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path cannot be empty");
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access outside project directory is not allowed");
        }
        Path current = root;
        for (Path segment : root.relativize(resolved)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Symbolic links are not allowed in project paths");
            }
        }
        return resolved;
    }
}
