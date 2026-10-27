package com.example.CodeForge.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NewProjectCreation {

    /*
     * =========================================================
     * BASE PROJECT DIRECTORY
     * =========================================================
     */

    private static final String BASE_FOLDER =
            "C:/Users/Vikas/Downloads/CodeForgeFullStack/CodeForge/projects";


    /*
     * =========================================================
     * CREATE NEW PROJECT
     * =========================================================
     */

    public String createNewProject(String appName, String appType) {

        /*
         * Validate app name
         */

        if (appName == null || appName.isBlank()) {
            throw new IllegalArgumentException(
                    "App name cannot be empty"
            );
        }


        /*
         * Validate project type
         */

        if (appType == null || appType.isBlank()) {
            throw new IllegalArgumentException(
                    "App type cannot be empty"
            );
        }


        /*
         * Generate unique project ID
         */

        String projectId = UUID.randomUUID().toString();


        /*
         * Create:
         *
         * projects/
         *     UUID/
         */

        Path projectPath =
                Paths.get(BASE_FOLDER, projectId);


        try {

            Files.createDirectories(projectPath);


            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "Creating CodeForge Project"
            );

            System.out.println(
                    "Project ID : " + projectId
            );

            System.out.println(
                    "App Name   : " + appName
            );

            System.out.println(
                    "App Type   : " + appType
            );

            System.out.println(
                    "========================================"
            );


            /*
             * Build CLI command
             */

            String[] command =
                    buildProjectCommand(
                            appName,
                            appType
                    );


            /*
             * Create ProcessBuilder
             */

            ProcessBuilder processBuilder =
                    new ProcessBuilder(command);


            /*
             * Run command inside UUID directory
             */

            processBuilder.directory(
                    projectPath.toFile()
            );


            /*
             * Combine stdout and stderr
             */

            processBuilder.redirectErrorStream(true);


            /*
             * Start process
             */

            Process process =
                    processBuilder.start();


            /*
             * Read CLI output
             */

            try (
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            process.getInputStream()
                                    )
                            )
            ) {

                String line;

                while (
                        (line = reader.readLine()) != null
                ) {

                    System.out.println(
                            "[CLI] " + line
                    );
                }
            }


            /*
             * Wait for command to finish
             */

            int exitCode =
                    process.waitFor();


            System.out.println(
                    "Project creation finished."
            );

            System.out.println(
                    "Exit code: " + exitCode
            );


            /*
             * If CLI failed
             */

            if (exitCode != 0) {

                deleteDirectory(
                        projectPath.toFile()
                );

                throw new RuntimeException(
                        "Project creation failed. " +
                        "Exit code: " + exitCode
                );
            }


            /*
             * Run the generated app inside Docker so the user project is isolated
             * and does not execute directly on the host machine.
             */

            startGeneratedProject(projectPath, appType, appName);

            /*
             * Return project ID
             */

            return projectId;


        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new RuntimeException(
                    "Project creation was interrupted",
                    e
            );


        } catch (IOException e) {

            throw new RuntimeException(
                    "Error while creating project",
                    e
            );
        }
    }


    private void startGeneratedProject(Path projectPath, String appType, String appName) {
        String normalizedType = appType == null ? "" : appType.trim().toLowerCase();
        Path appDirectory = resolveAppDirectory(projectPath);

        if (!Files.isDirectory(appDirectory)) {
            throw new IllegalStateException("Project directory was not created correctly: " + projectPath);
        }

        int port = resolvePortForType(normalizedType);
        String containerName = sanitizeContainerName(appName == null || appName.isBlank() ? "project" : appName);
        String imageName = containerName + "-img";

        createDockerfileForProject(appDirectory, normalizedType, port);

        if (!isDockerAvailable()) {
            throw new IllegalStateException("Docker is required to run generated projects in isolated containers.");
        }

        try {
            buildDockerImage(appDirectory, imageName);
            runDockerContainer(imageName, containerName, port);
            System.out.println("Started dockerized project: " + containerName + " on port " + port);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to start generated project in Docker: " + e.getMessage(), e);
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
        } catch (IOException ignored) {
            return projectPath;
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
            default -> buildNodeDockerfile(port);
        };

        try {
            Files.writeString(appDirectory.resolve("Dockerfile"), dockerfileContent);
            Files.writeString(appDirectory.resolve(".dockerignore"), "node_modules\n.git\n.gitignore\n.dockerignore\nDockerfile\n*.log\n.target\nbuild\n.vite\n" );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write Docker config for generated project", e);
        }
    }

    private String buildNodeDockerfile(int port) {
        return "FROM node:20-alpine\n" +
                "WORKDIR /app\n" +
                "COPY package*.json ./\n" +
                "RUN npm install\n" +
                "COPY . .\n" +
                "EXPOSE " + port + "\n" +
                "CMD [\"npm\", \"run\", \"dev\", \"--\", \"--host\", \"0.0.0.0\", \"--port\", \"" + port + "\"]\n";
    }

    private String buildAngularDockerfile(int port) {
        return "FROM node:20-alpine\n" +
                "WORKDIR /app\n" +
                "COPY package*.json ./\n" +
                "RUN npm install\n" +
                "COPY . .\n" +
                "EXPOSE " + port + "\n" +
                "CMD [\"npx\", \"ng\", \"serve\", \"--host\", \"0.0.0.0\", \"--port\", \"" + port + "\"]\n";
    }

    private String buildNextDockerfile(int port) {
        return "FROM node:20-alpine\n" +
                "WORKDIR /app\n" +
                "COPY package*.json ./\n" +
                "RUN npm install\n" +
                "COPY . .\n" +
                "EXPOSE " + port + "\n" +
                "CMD [\"npm\", \"run\", \"dev\", \"--\", \"--hostname\", \"0.0.0.0\", \"--port\", \"" + port + "\"]\n";
    }

    private String buildNodeApiDockerfile(int port) {
        return "FROM node:20-alpine\n" +
                "WORKDIR /app\n" +
                "COPY package*.json ./\n" +
                "RUN npm install\n" +
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

    private boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "--version").start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private void buildDockerImage(Path appDirectory, String imageName) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "docker",
                "build",
                "-t",
                imageName,
                appDirectory.toAbsolutePath().toString()
        ).redirectErrorStream(true).start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[DOCKER BUILD] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Docker build failed for project image: " + imageName);
        }
    }

    private void runDockerContainer(String imageName, String containerName, int port) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "docker",
                "run",
                "-d",
                "--rm",
                "--name",
                containerName,
                "-p",
                port + ":" + port,
                imageName
        ).redirectErrorStream(true).start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[DOCKER RUN] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Docker run failed for project container: " + containerName);
        }
    }

    private String sanitizeContainerName(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "")
                .substring(0, Math.min(value.length(), 30));
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

                        "@angular",
                        "cli",

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

            case "spring":
            case "springboot":
            case "spring-boot":

                return buildSpringBootCommand(
                        appName
                );


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
     * SPRING BOOT COMMAND
     * =========================================================
     *
     * Uses Spring Initializr.
     *
     * The generated ZIP is extracted inside:
     *
     * UUID/
     *
     * resulting in:
     *
     * UUID/
     *     appName/
     *         pom.xml
     *         src/
     *
     * =========================================================
     */

    private String[] buildSpringBootCommand(
            String appName
    ) {

        String url =
                "https://start.spring.io/starter.zip"
                + "?type=maven-project"
                + "&language=java"
                + "&dependencies=web"
                + "&name=" + appName
                + "&artifactId=" + appName
                + "&groupId=com.example"
                + "&packageName=com.example."
                + appName;


        return new String[]{

                "cmd.exe",
                "/c",

                "curl",
                "-L",

                "\"" + url + "\"",

                "-o",

                appName + ".zip",

                "&&",

                "tar",
                "-xf",

                appName + ".zip",

                "&&",

                "del",

                appName + ".zip"
        };
    }


    /*
     * =========================================================
     * DIRECTORY TREE
     * =========================================================
     */

    public FileNode directoryTree(
            String projectId
    ) {

        validateProjectId(projectId);


        Path projectPath =
                Paths.get(
                        BASE_FOLDER,
                        projectId
                );


        if (!Files.exists(projectPath)) {

            throw new IllegalArgumentException(
                    "Project does not exist: " +
                    projectId
            );
        }


        return buildTree(
                projectPath.toFile()
        );
    }


    /*
     * =========================================================
     * BUILD DIRECTORY TREE
     * =========================================================
     */

    private FileNode buildTree(
            File file
    ) {

        FileNode node =
                new FileNode();


        node.name =
                file.getName();


        node.isDirectory =
                file.isDirectory();


        /*
         * Only process children
         * when this is a directory.
         */

        if (file.isDirectory()) {

            File[] files =
                    file.listFiles();


            if (files != null) {

                for (File child : files) {

                    node.children.add(
                            buildTree(child)
                    );
                }
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

    private void validateProjectId(
            String projectId
    ) {

        if (
                projectId == null ||
                projectId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Project ID cannot be empty"
            );
        }


        try {

            UUID.fromString(
                    projectId
            );

        } catch (IllegalArgumentException e) {

            throw new IllegalArgumentException(
                    "Invalid project ID"
            );
        }
    }


    /*
     * =========================================================
     * DELETE DIRECTORY
     * =========================================================
     */

    private void deleteDirectory(
            File file
    ) {

        if (file.isDirectory()) {

            File[] children =
                    file.listFiles();


            if (children != null) {

                for (File child : children) {

                    deleteDirectory(
                            child
                    );
                }
            }
        }


        if (!file.delete()) {

            System.out.println(
                    "Could not delete: " +
                    file.getAbsolutePath()
            );
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