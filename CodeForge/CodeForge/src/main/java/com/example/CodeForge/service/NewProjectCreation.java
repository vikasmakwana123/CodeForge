package com.example.CodeForge.service;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import com.example.CodeForge.models.Project;
import com.example.CodeForge.models.ProjectStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NewProjectCreation {

    /*
     * =========================================================
     * BASE PROJECT DIRECTORY
     * =========================================================
     */

    private static final Pattern SAFE_NAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9-]{0,39}$");
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "react", "vue", "angular", "next", "nextjs", "next.js", "node", "nodejs",
            "spring", "springboot", "spring-boot", "vite"
    );

    @Value("${codeforge.projects-base}")
    private String baseFolder;

    private final ProjectService projectService;

    public NewProjectCreation(ProjectService projectService) {
        this.projectService = projectService;
    }


    /*
     * =========================================================
     * CREATE NEW PROJECT
     * =========================================================
     */

    public Path scaffold(UUID projectId, String appName, String appType) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID cannot be null");
        }
        if (appName == null || !SAFE_NAME.matcher(appName).matches()) {
            throw new IllegalArgumentException("Name must be letters, digits, '-' (max 40)");
        }

        String normalizedType = validateAppType(appType);
        Path projectPath = Paths.get(baseFolder).toAbsolutePath().normalize().resolve(projectId.toString());
        try {
            Files.createDirectories(projectPath);
            if (isSpringType(normalizedType)) {
                downloadAndExtractSpringProject(projectPath, appName);
            } else {
                runProcess(projectPath, buildProjectCommand(appName, normalizedType), "project generator");
            }
            return resolveAppDirectory(projectPath);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cleanupAfterFailure(projectPath, exception);
            throw new IllegalStateException("Project creation was interrupted", exception);
        } catch (Exception exception) {
            cleanupAfterFailure(projectPath, exception);
            throw new IllegalStateException("Project creation failed: " + exception.getMessage(), exception);
        }
    }

    public RunInfo start(UUID projectId, Path appDirectory, String appType) {
        String normalizedType = validateAppType(appType);
        if (projectId == null || appDirectory == null || !Files.isDirectory(appDirectory)) {
            throw new IllegalArgumentException("App directory does not exist");
        }
        Path projectRoot = Paths.get(baseFolder).toAbsolutePath().normalize().resolve(projectId.toString());
        Path normalizedAppDirectory = appDirectory.toAbsolutePath().normalize();
        if (!normalizedAppDirectory.startsWith(projectRoot) || Files.isSymbolicLink(normalizedAppDirectory)) {
            throw new SecurityException("App directory must be inside the stored project folder");
        }
        int containerPort = resolvePortForType(normalizedType);
        int hostPort = findAvailablePort();
        String containerName = "cf-" + projectId.toString().substring(0, 8);
        String imageName = containerName + "-img";
        createDockerfileForProject(appDirectory, normalizedType, containerPort);
        try {
            runProcess(appDirectory, new String[]{"docker", "--version"}, "Docker availability check");
            runProcess(appDirectory, new String[]{
                    "docker", "build", "-t", imageName, normalizedAppDirectory.toString()
            }, "Docker build");
            List<String> command = new ArrayList<>(List.of(
                    "docker", "run", "-d", "--rm", "--name", containerName,
                    "--memory=512m", "--cpus=1", "--pids-limit=256",
                    "-p", hostPort + ":" + containerPort
            ));
            if (!isSpringType(normalizedType)) {
                command.addAll(List.of(
                        "-v", normalizedAppDirectory + ":/app",
                        "-v", "/app/node_modules"
                ));
            }
            command.add(imageName);
            runProcess(appDirectory, command.toArray(String[]::new), "Docker container start");
            return new RunInfo(containerName, hostPort, containerPort, imageName);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start generated project in Docker: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Docker startup was interrupted", exception);
        }
    }

    public boolean isContainerRunning(String containerName) throws IOException, InterruptedException {
        if (containerName == null || !containerName.matches("^cf-[a-f0-9]{8}$")) {
            throw new IllegalArgumentException("Invalid project container name");
        }
        Process process = new ProcessBuilder(
                "docker", "ps", "--filter", "name=^" + containerName + "$", "--format", "{{.Names}}"
        ).redirectErrorStream(true).start();
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Docker container status check timed out");
        }
        if (process.exitValue() != 0) {
            throw new IOException("Docker container status check failed: "
                    + new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim());
        }
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .lines()
                .anyMatch(containerName::equals);
    }

    public void deleteProjectResources(Project project) throws IOException, InterruptedException {
        Path projectsRoot = Paths.get(baseFolder).toAbsolutePath().normalize();
        Path projectDirectory = projectsRoot.resolve(project.getId().toString()).normalize();
        if (!projectDirectory.startsWith(projectsRoot) || Files.isSymbolicLink(projectDirectory)) {
            throw new SecurityException("Stored project directory is invalid");
        }

        Files.createDirectories(projectsRoot);
        if (project.getContainerName() != null && !project.getContainerName().isBlank()) {
            if (!project.getContainerName().matches("^cf-[a-f0-9-]+$")) {
                throw new SecurityException("Stored container name is invalid");
            }
            runProcess(projectsRoot, new String[]{"docker", "--version"}, "Docker availability check");
            removeDockerResource(projectsRoot, "container", project.getContainerName());
            removeDockerResource(projectsRoot, "image", project.getContainerName() + "-img");
        }
        deleteDirectory(projectDirectory.toFile());
    }

    private void removeDockerResource(Path directory, String resourceType, String resourceName)
            throws IOException, InterruptedException {
        try {
            runProcess(directory, new String[]{"docker", resourceType, "rm", "-f", resourceName},
                    "Docker " + resourceType + " removal");
        } catch (IOException exception) {
            String missingResourceMessage = resourceType.equals("container") ? "No such container" : "No such image";
            if (exception.getMessage() == null || !exception.getMessage().contains(missingResourceMessage)) {
                throw exception;
            }
        }
    }

    public record RunInfo(String containerName, int hostPort, int containerPort, String imageName) {
    }

    private String validateAppType(String appType) {
        String normalized = appType == null ? "" : appType.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported project type: " + appType);
        }
        return normalized;
    }

    private boolean isSpringType(String appType) {
        return appType.equals("spring") || appType.equals("springboot") || appType.equals("spring-boot");
    }

    private int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not allocate a host port", exception);
        }
    }

    private void cleanupAfterFailure(Path projectPath, Exception failure) {
        try {
            deleteDirectory(projectPath.toFile());
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void runProcess(Path directory, String[] command, String operation)
            throws IOException, InterruptedException {
        Path logFile = Files.createTempFile(directory, ".codeforge-", ".log");
        try {
            Process process = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
            if (!process.waitFor(5, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                process.waitFor();
                throw new IOException(operation + " timed out after 5 minutes");
            }
            if (process.exitValue() != 0) {
                throw new IOException(operation + " failed with exit code " + process.exitValue()
                        + ": " + readLogTail(logFile));
            }
        } finally {
            Files.deleteIfExists(logFile);
        }
    }

    private String readLogTail(Path logFile) throws IOException {
        try (RandomAccessFile log = new RandomAccessFile(logFile.toFile(), "r")) {
            long start = Math.max(0, log.length() - 4096);
            byte[] tail = new byte[(int) (log.length() - start)];
            log.seek(start);
            log.readFully(tail);
            return new String(tail, StandardCharsets.UTF_8).trim();
        }
    }

    private void downloadAndExtractSpringProject(Path projectPath, String appName) throws Exception {
        String encodedName = URLEncoder.encode(appName, StandardCharsets.UTF_8);
        String encodedPackageName = URLEncoder.encode(
                "com.example." + appName.replace('-', '_'),
                StandardCharsets.UTF_8
        );
        String url = "https://start.spring.io/starter.zip"
                + "?type=maven-project&language=java&dependencies=web"
                + "&name=" + encodedName
                + "&artifactId=" + encodedName
                + "&groupId=com.example"
                + "&packageName=" + encodedPackageName;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Spring Initializr returned HTTP " + response.statusCode());
        }

        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(response.body()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = projectPath.resolve(entry.getName()).normalize();
                if (!target.startsWith(projectPath)) {
                    throw new IOException("Spring Initializr ZIP contains an invalid path");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private Path resolveAppDirectory(Path projectPath) {
        if (!Files.isDirectory(projectPath)) {
            return projectPath;
        }

        try (var stream = Files.list(projectPath)) {
            List<Path> entries = stream.toList();
            if (entries.size() == 1 && Files.isDirectory(entries.get(0))) {
                return entries.get(0);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not resolve generated app directory", exception);
        }

        return projectPath;
    }

    private void createDockerfileForProject(Path appDirectory, String appType, int port) {
        String dockerfileContent = switch (appType) {
            case "react", "vue", "vite" -> buildNodeDockerfile(port);
            case "angular" -> buildAngularDockerfile(port);
            case "next", "nextjs", "next.js" -> buildNextDockerfile(port);
            case "node", "nodejs" -> buildNodeApiDockerfile(port);
            case "spring", "springboot", "spring-boot" -> buildSpringDockerfile(port);
            default -> throw new IllegalArgumentException("Unsupported project type: " + appType);
        };

        try {
            Files.writeString(appDirectory.resolve("Dockerfile"), dockerfileContent);
            Files.writeString(appDirectory.resolve(".dockerignore"), "node_modules\n.git\n.gitignore\n.dockerignore\nDockerfile\n*.log\n.target\nbuild\n.vite\n" );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write Docker config for generated project", e);
        }
    }

    private String buildNodeDockerfile(int port) {
        return "FROM node:22-alpine\n" +
                "WORKDIR /app\n" +
                "COPY package*.json ./\n" +
                "RUN npm install --global npm@12.2.1 && npm install\n" +
                "COPY . .\n" +
                "EXPOSE " + port + "\n" +
                "CMD [\"npm\", \"run\", \"dev\", \"--\", \"--host\", \"0.0.0.0\", \"--port\", \"" + port + "\"]\n";
    }

    private String buildAngularDockerfile(int port) {
        return "FROM node:22-alpine\n" +
                "WORKDIR /app\n" +
                "COPY package*.json ./\n" +
                "RUN npm install --global npm@12.2.1 && npm install\n" +
                "COPY . .\n" +
                "EXPOSE " + port + "\n" +
                "CMD [\"npx\", \"ng\", \"serve\", \"--host\", \"0.0.0.0\", \"--port\", \"" + port + "\"]\n";
    }

    private String buildNextDockerfile(int port) {
        return "FROM node:22-alpine\n" +
                "WORKDIR /app\n" +
                "COPY package*.json ./\n" +
                "RUN npm install --global npm@12.2.1 && npm install\n" +
                "COPY . .\n" +
                "EXPOSE " + port + "\n" +
                "CMD [\"npm\", \"run\", \"dev\", \"--\", \"--hostname\", \"0.0.0.0\", \"--port\", \"" + port + "\"]\n";
    }

    private String buildNodeApiDockerfile(int port) {
        return "FROM node:22-alpine\n" +
                "WORKDIR /app\n" +
                "COPY package*.json ./\n" +
                "RUN npm install --global npm@12.2.1 && npm install\n" +
                "COPY . .\n" +
                "ENV PORT=" + port + "\n" +
                "EXPOSE " + port + "\n" +
                "CMD [\"npm\", \"start\"]\n";
    }

    private String buildSpringDockerfile(int port) {
        return "FROM maven:3.9.9-eclipse-temurin-21 AS build\n" +
                "WORKDIR /workspace\n" +
                "COPY . .\n" +
                "RUN mvn -q -DskipTests package\n" +
                "\n" +
                "FROM eclipse-temurin:21-jre-jammy\n" +
                "WORKDIR /app\n" +
                "COPY --from=build /workspace/target/*.jar app.jar\n" +
                "EXPOSE " + port + "\n" +
                "ENTRYPOINT [\"java\", \"-jar\", \"/app/app.jar\", \"--server.port=" + port + "\"]\n";
    }

    private int resolvePortForType(String appType) {
        int basePort = switch (appType) {
            case "react", "vue", "vite" -> 5173;
            case "angular" -> 4200;
            case "next", "nextjs", "next.js" -> 3000;
            case "node", "nodejs" -> 4000;
            case "spring", "springboot", "spring-boot" -> 8081;
            default -> 5173;
        };

        return basePort;
    }

    /*
     * =========================================================
     * BUILD PROJECT COMMAND
     * =========================================================
     */

    private String[] buildProjectCommand(
            String appName,
            String appType
    ) {

        /*
         * Normalize project type
         */

        String type =
                appType
                        .trim()
                        .toLowerCase();


        switch (type) {


            /*
             * =================================================
             * REACT
             * =================================================
             */

            case "react":

                return new String[]{

                        "cmd.exe",
                        "/c",

                        "npm",
                        "create",
                        "vite@latest",

                        appName,

                        "--",

                        "--template",
                        "react"
                };


            /*
             * =================================================
             * VUE
             * =================================================
             */

            case "vue":

                return new String[]{

                        "cmd.exe",
                        "/c",

                        "npm",
                        "create",
                        "vite@latest",

                        appName,

                        "--",

                        "--template",
                        "vue"
                };


            /*
             * =================================================
             * ANGULAR
             * =================================================
             */

            case "angular":

                return new String[]{

                        "cmd.exe",
                        "/c",

                        "npx",

                        "@angular/cli",

                        "new",

                        appName,

                        "--skip-git",

                        "--style=css",

                        "--routing=true"
                };


            /*
             * =================================================
             * NEXT.JS
             * =================================================
             */

            case "next":
            case "nextjs":
            case "next.js":

                return new String[]{

                        "cmd.exe",
                        "/c",

                        "npx",

                        "create-next-app@latest",

                        appName,

                        "--js",

                        "--eslint",

                        "--tailwind",

                        "--app",

                        "--src-dir",

                        "--use-npm",

                        "--import-alias",
                        "@/*"
                };


            /*
             * =================================================
             * NODE.JS / EXPRESS
             * =================================================
             */

            case "node":
            case "nodejs":

                return new String[]{

                        "cmd.exe",
                        "/c",

                        "npx",

                        "express-generator",

                        "--no-view",

                        appName
                };


            /*
             * =================================================
             * VITE
             * =================================================
             */

            case "vite":

                return new String[]{

                        "cmd.exe",
                        "/c",

                        "npm",
                        "create",
                        "vite@latest",

                        appName,

                        "--",

                        "--template",
                        "vanilla"
                };


            /*
             * =================================================
             * SPRING BOOT
             * =================================================
             */

            /*
             * =================================================
             * UNSUPPORTED PROJECT
             * =================================================
             */

            default:

                throw new IllegalArgumentException(
                        "Unsupported project type: " +
                        appType
                );
        }
    }


    /*
     * =========================================================
     * DIRECTORY TREE
     * =========================================================
     */

    public FileNode directoryTree(UUID projectId, UUID userId) {
        Project project = projectService.getOwned(projectId, userId);
        if (project.getStatus() != ProjectStatus.READY || project.getRootPath() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is not ready");
        }

        Path appRoot = Paths.get(project.getRootPath()).toAbsolutePath().normalize();
        Path projectBase = Paths.get(baseFolder).toAbsolutePath().normalize()
                .resolve(project.getId().toString()).normalize();
        if (!appRoot.startsWith(projectBase) || Files.isSymbolicLink(appRoot) || !Files.isDirectory(appRoot)) {
            throw new IllegalStateException("Stored project directory is invalid");
        }

        try {
            return buildTree(appRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read project directory tree", exception);
        }
    }


    /*
     * =========================================================
     * BUILD DIRECTORY TREE
     * =========================================================
     */

    private FileNode buildTree(Path path) throws IOException {
        FileNode node = new FileNode();
        node.name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        node.isDirectory = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
        if (!node.isDirectory) {
            return node;
        }

        try (var children = Files.list(path)) {
            for (Path child : children.toList()) {
                String name = child.getFileName().toString();
                if (Files.isSymbolicLink(child)
                        || Set.of("node_modules", ".git", "target", "dist").contains(name)) {
                    continue;
                }
                node.children.add(buildTree(child));
            }
        }
        return node;
    }


    /*
     * =========================================================
     * PROJECT ID VALIDATION
     * =========================================================
     *
     * Prevents:
     *
     * ../../some-folder
     *
     * attacks.
     *
     * Only UUIDs are accepted.
     *
     * =========================================================
     */

    /*
     * =========================================================
     * DELETE DIRECTORY
     * =========================================================
     */

    private void deleteDirectory(File file) {
        Path root = file.toPath();
        try {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                    Files.delete(path);
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
        } catch (IOException exception) {
            throw new IllegalStateException("Could not clean failed project directory", exception);
        }
    }


    /*
     * =========================================================
     * FILE NODE DTO
     * =========================================================
     */

    public static class FileNode {

        public String name;

        public boolean isDirectory;

        public List<FileNode> children =
                new ArrayList<>();


        public FileNode() {
        }
    }
}