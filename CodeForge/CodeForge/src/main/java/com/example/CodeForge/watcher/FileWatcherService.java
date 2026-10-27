package com.example.CodeForge.watcher;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.example.CodeForge.dto.FileChangeEvent;
import com.example.CodeForge.service.FileService;

import jakarta.annotation.PostConstruct;

@Service
public class FileWatcherService {

    private final SimpMessagingTemplate messagingTemplate;
    private final FileService fileService;
    private WatchService watchService;

    private final Map<WatchKey, Path> watchKeys= new ConcurrentHashMap<>();
    public FileWatcherService(
            SimpMessagingTemplate messagingTemplate,
            FileService fileService) {
        this.messagingTemplate = messagingTemplate;
        this.fileService = fileService;
    }

    @PostConstruct
    public void startWatchingProjectRoot() throws IOException {
        startWatching(fileService.getProjectRoot());
    }
    public void startWatching(Path rootDirectory) throws IOException {

        Files.createDirectories(rootDirectory);
        watchService = FileSystems.getDefault().newWatchService();

        registerDirectoryTree(rootDirectory);

        Thread watcherThread = new Thread(
                this::processEvents,
                "file-watcher-thread"
        );

        watcherThread.setDaemon(true);
        watcherThread.start();

        System.out.println(
                "Started watching: " + rootDirectory
        );

    }
    private void registerDirectoryTree(Path root)
            throws IOException {

        Files.walk(root)
                .filter(Files::isDirectory)
                .forEach(directory -> {

                    try {

                        WatchKey key = directory.register(
                                watchService,

                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY,
                                StandardWatchEventKinds.ENTRY_DELETE
                        );

                        watchKeys.put(key, directory);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
    private void processEvents() {

        while (true) {

            WatchKey key;

            try {

                key = watchService.take();

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                return;

            }
            Path directory = watchKeys.get(key);

            if (directory == null) {
                key.reset();
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {

                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                Path filename = (Path) event.context();

                Path fullPath =
                        directory.resolve(filename);

                String relativePath = fileService.toRelativePath(fullPath);

                FileChangeEvent changeEvent;

                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {

                    changeEvent = new FileChangeEvent(
                            "CREATED",
                            relativePath
                    );

                    /*
                     * Important:
                     *
                     * If a new directory is created,
                     * register it too.
                     */
                    if (Files.isDirectory(fullPath)) {

                        try {
                            registerDirectoryTree(fullPath);
                        } catch (IOException e) {
                            System.err.println("Unable to watch created directory: " + e.getMessage());
                        }
                    }

                }else if (
                        kind == StandardWatchEventKinds.ENTRY_MODIFY
                ) {

                    changeEvent = new FileChangeEvent(
                            "MODIFIED",
                            relativePath
                    );

                } else if (
                        kind == StandardWatchEventKinds.ENTRY_DELETE
                ) {

                    changeEvent = new FileChangeEvent(
                            "DELETED",
                            relativePath
                    );

                } else {
                    continue;
                }

                messagingTemplate.convertAndSend(
                        "/topic/files",
                        changeEvent
                );
            }

            boolean valid = key.reset();

            if (!valid) {

                watchKeys.remove(key);

                if (watchKeys.isEmpty()) {
                    break;
                }
            }
        }}
}
