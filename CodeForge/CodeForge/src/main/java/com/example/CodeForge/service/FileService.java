package com.example.CodeForge.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

@Service
public class FileService {
    private final Path projectRoot = Paths.get("C:/Users/Vikas/Downloads/CodeForge/projects")
            .toAbsolutePath()
            .normalize();

    // Create File
    public void createFile(String relativePath) throws IOException {
        Path filePath = resolveSafePath(relativePath);
        if (Files.exists(filePath)) {
            throw new FileAlreadyExistsException(
                    "File already exists: " + relativePath);
        }
        // Making sure that parent directory exists
        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.createFile(filePath);
    }


    // CREATE FOLDER
    public void createFolder(String relativePath) throws IOException {

        Path folderPath = resolveSafePath(relativePath);

        if (Files.exists(folderPath)) {
            throw new FileAlreadyExistsException(
                    "Folder already exists: " + relativePath
            );
        }

        Files.createDirectories(folderPath);
    }

    // READ FILE
    public String readFile(String relativePath) throws IOException {

        Path filePath = resolveSafePath(relativePath);

        if (!Files.exists(filePath)) {
            throw new NoSuchFileException(
                    "File does not exist: " + relativePath
            );
        }

        if (!Files.isRegularFile(filePath)) {
            throw new IOException(
                    "Path is not a file: " + relativePath
            );
        }

        return Files.readString(
                filePath,
                StandardCharsets.UTF_8
        );
    }
    // WRITE / UPDATE FILE
    public void writeFile(
            String relativePath,
            String content
    ) throws IOException {

        Path filePath = resolveSafePath(relativePath);

        if (!Files.exists(filePath)) {

            Path parent = filePath.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }
        }

        Files.writeString(
                filePath,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    // DELETE FILE OR FOLDER
    public void delete(String relativePath) throws IOException {

        Path path = resolveSafePath(relativePath);

        if (!Files.exists(path)) {
            throw new NoSuchFileException(
                    "Path does not exist: " + relativePath);
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> paths = Files.walk(path)) {
                paths.sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> {

                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        } else {

            Files.delete(path);
        }
    }

    // LIST DIRECTORY
    public List<String> listDirectory(
            String relativePath) throws IOException {

        Path directory = resolveSafePath(relativePath);

        if (!Files.exists(directory)) {
            throw new NoSuchFileException(
                    "Directory does not exist: "
                            + relativePath);
        }

        if (!Files.isDirectory(directory)) {
            throw new IOException(
                    "Path is not a directory: "
                            + relativePath);
        }

        List<String> result = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {

            for (Path path : stream) {

                String child = projectRoot
                        .relativize(path)
                        .toString()
                        .replace("\\", "/");

                result.add(child);
            }
        }

        return result;
    }

    // CHECK EXISTS
    public boolean exists(String relativePath) {

        Path path = resolveSafePath(relativePath);

        return Files.exists(path);
    }

    // GET FILE INFORMATION
    public boolean isDirectory(String relativePath) {

        Path path = resolveSafePath(relativePath);

        return Files.isDirectory(path);
    }

    public String toRelativePath(Path path) {
        return projectRoot
                .relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace("\\", "/");
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    // SAFE PATH RESOLUTION
    private Path resolveSafePath(String relativePath) {

        if (relativePath == null ||
                relativePath.isBlank()) {
            throw new IllegalArgumentException(
                    "Path cannot be empty");
        }
        Path resolved = projectRoot
                .resolve(relativePath)
                .normalize();
        if (!resolved.startsWith(projectRoot)) {
            throw new SecurityException(
                    "Access outside project directory is not allowed");
        }

        return resolved;
    }
}
