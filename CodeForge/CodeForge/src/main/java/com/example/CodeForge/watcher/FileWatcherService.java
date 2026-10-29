package com.example.CodeForge.watcher;

import com.example.CodeForge.dto.FileChangeEvent;
import com.example.CodeForge.models.Project;
import com.example.CodeForge.models.ProjectStatus;
import com.example.CodeForge.repo.ProjectRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
public class FileWatcherService {
    private static final Logger logger = LoggerFactory.getLogger(FileWatcherService.class);
    private static final Set<String> IGNORED_DIRECTORIES = Set.of("node_modules", ".git", "target", "dist");

    private final SimpMessagingTemplate messagingTemplate;
    private final ProjectRepository projects;
    private final Map<WatchKey, WatchedDirectory> watchKeys = new ConcurrentHashMap<>();
    private WatchService watchService;

    public FileWatcherService(SimpMessagingTemplate messagingTemplate, ProjectRepository projects) {
        this.messagingTemplate = messagingTemplate;
        this.projects = projects;
    }

    @PostConstruct
    public void startWatchingReadyProjects() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        for (Project project : projects.findAll()) {
            if (project.getStatus() == ProjectStatus.READY && project.getRootPath() != null) {
                try {
                    watchProject(project.getId(), project.getOwner().getId(), Path.of(project.getRootPath()));
                } catch (IOException exception) {
                    logger.warn(
                            "Skipping file watching for project {} because its stored root is unavailable in this runtime: {}",
                            project.getId(),
                            project.getRootPath()
                    );
                }
            }
        }

        Thread watcherThread = new Thread(this::processEvents, "project-file-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    public void watchProject(UUID projectId, UUID ownerId, Path rootDirectory) throws IOException {
        Path root = rootDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IOException("Project root is invalid: " + root);
        }
        registerDirectoryTree(projectId, ownerId, root, root);
    }

    private void registerDirectoryTree(UUID projectId, UUID ownerId, Path root, Path startDirectory)
            throws IOException {
        try (Stream<Path> paths = Files.walk(startDirectory)) {
            for (Path directory : paths.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> !hasIgnoredDirectory(root, path))
                    .toList()) {
                WatchKey key = directory.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE
                );
                watchKeys.put(key, new WatchedDirectory(projectId, ownerId, root, directory));
            }
        }
    }

    private boolean hasIgnoredDirectory(Path root, Path path) {
        for (Path segment : root.relativize(path)) {
            if (IGNORED_DIRECTORIES.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private void processEvents() {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            WatchedDirectory watched = watchKeys.get(key);
            if (watched != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    Path name = (Path) event.context();
                    Path fullPath = watched.directory().resolve(name);
                    String relativePath = watched.root().relativize(fullPath).toString().replace("\\", "/");

                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                            && Files.isDirectory(fullPath, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isSymbolicLink(fullPath)
                            && !IGNORED_DIRECTORIES.contains(fullPath.getFileName().toString())) {
                        try {
                            registerDirectoryTree(
                                    watched.projectId(),
                                    watched.ownerId(),
                                    watched.root(),
                                    fullPath
                            );
                        } catch (IOException exception) {
                            System.err.println("Unable to watch created project directory: " + exception.getMessage());
                        }
                    }

                    messagingTemplate.convertAndSendToUser(
                            watched.ownerId().toString(),
                            "/queue/files",
                            new FileChangeEvent(
                                    watched.projectId(),
                                    event.kind().name(),
                                    relativePath
                            )
                    );
                }
            }
            if (!key.reset()) {
                watchKeys.remove(key);
            }
        }
    }

    private record WatchedDirectory(UUID projectId, UUID ownerId, Path root, Path directory) {
    }
}
