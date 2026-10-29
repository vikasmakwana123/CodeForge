package com.example.CodeForge.service;

import com.example.CodeForge.dto.TerminalEvent;
import com.example.CodeForge.models.Project;
import com.example.CodeForge.models.ProjectStatus;
import com.example.CodeForge.repo.ProjectRepository;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ProjectTerminalService {
    private static final int MAX_INPUT_LENGTH = 16_384;

    private final ProjectService projectService;
    private final ProjectRepository projects;
    private final NewProjectCreation creator;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<TerminalKey, TerminalSession> terminals = new ConcurrentHashMap<>();
    private final Map<UUID, Object> projectStartLocks = new ConcurrentHashMap<>();

    public void open(
            UUID projectId,
            UUID userId,
            String stompSessionId,
            UUID connectionId,
            Integer columns,
            Integer rows
    ) {
        if (stompSessionId == null || connectionId == null) {
            throw new IllegalArgumentException("Terminal session information is missing");
        }
        validateSize(columns, rows);
        TerminalKey key = new TerminalKey(stompSessionId, connectionId);
        close(key);
        send(projectId, connectionId, userId, "STATUS", "Starting project container...", null);

        try {
            Project project = ensureRunning(projectId, userId);
            String terminalPidFile = isWindows() ? terminalPidFile(connectionId) : null;
            Process process = startTerminalProcess(project.getContainerName(), connectionId, columns, rows);
            TerminalSession terminal = new TerminalSession(
                    projectId, userId, connectionId, stompSessionId, project.getContainerName(), terminalPidFile, process
            );
            terminals.put(key, terminal);
            if (project.getHostPort() != null) {
                send(projectId, connectionId, userId, "STATUS",
                        "Preview URL: http://localhost:" + project.getHostPort() + "/\r\n", null);
            }
            send(projectId, connectionId, userId, "STATUS", "Terminal connected.\r\n", null);
            Thread.ofVirtual().name("project-terminal-" + connectionId).start(() -> pumpOutput(key, terminal));
        } catch (Exception exception) {
            send(projectId, connectionId, userId, "ERROR", null, safeMessage(exception));
        }
    }

    public void resize(
            UUID projectId,
            UUID userId,
            String stompSessionId,
            UUID connectionId,
            Integer columns,
            Integer rows
    ) {
        validateSize(columns, rows);
        if (columns == null || rows == null) {
            throw new IllegalArgumentException("Terminal dimensions are required");
        }
        TerminalSession terminal = terminals.get(new TerminalKey(stompSessionId, connectionId));
        if (terminal == null || !terminal.projectId.equals(projectId) || !terminal.userId.equals(userId)) {
            return;
        }
        if (terminal.process instanceof PtyProcess ptyProcess) {
            ptyProcess.setWinSize(new WinSize(columns, rows));
        }
    }

    public void write(UUID projectId, UUID userId, String stompSessionId, UUID connectionId, String data) {
        if (data == null || data.length() > MAX_INPUT_LENGTH) {
            send(projectId, connectionId, userId, "ERROR", null, "Terminal input is empty or too large");
            return;
        }
        TerminalSession terminal = terminals.get(new TerminalKey(stompSessionId, connectionId));
        if (terminal == null || !terminal.projectId.equals(projectId) || !terminal.userId.equals(userId)) {
            send(projectId, connectionId, userId, "ERROR", null, "Terminal session is not open");
            return;
        }
        try {
            OutputStream input = terminal.process.getOutputStream();
            input.write(data.getBytes(StandardCharsets.UTF_8));
            input.flush();
        } catch (IOException exception) {
            send(projectId, connectionId, userId, "ERROR", null, "Could not write to the project terminal");
            close(new TerminalKey(stompSessionId, connectionId));
        }
    }

    public void interrupt(UUID projectId, UUID userId, String stompSessionId, UUID connectionId) {
        TerminalSession terminal = terminals.get(new TerminalKey(stompSessionId, connectionId));
        if (terminal == null || !terminal.projectId.equals(projectId) || !terminal.userId.equals(userId)) {
            send(projectId, connectionId, userId, "ERROR", null, "Terminal session is not open");
            return;
        }
        if (!isWindows()) {
            write(projectId, userId, stompSessionId, connectionId, "\u0003");
            return;
        }

        String script = "pid=$(cat " + terminal.pidFile + " 2>/dev/null) || exit 0; "
                + "case \"$pid\" in ''|*[!0-9]*) exit 0;; esac; "
                + "stat=$(cat \"/proc/$pid/stat\" 2>/dev/null) || exit 0; "
                + "set -- $stat; shell_group=$5; foreground_group=$8; "
                + "case \"$foreground_group\" in ''|*[!0-9]*) exit 0;; esac; "
                + "if [ \"$foreground_group\" -gt 0 ] && [ \"$foreground_group\" != \"$shell_group\" ]; "
                + "then kill -INT -\"$foreground_group\"; else kill -INT \"$pid\"; fi";
        try {
            runContainerControlCommand(terminal.containerName, script);
        } catch (IOException exception) {
            send(projectId, connectionId, userId, "ERROR", null, "Could not interrupt the project terminal command");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            send(projectId, connectionId, userId, "ERROR", null, "Terminal interrupt was interrupted");
        }
    }

    public void close(String stompSessionId, UUID connectionId) {
        if (stompSessionId != null && connectionId != null) {
            close(new TerminalKey(stompSessionId, connectionId));
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String stompSessionId = event.getSessionId();
        terminals.keySet().stream()
                .filter(key -> key.stompSessionId.equals(stompSessionId))
                .toList()
                .forEach(this::close);
    }

    private Project ensureRunning(UUID projectId, UUID userId) throws IOException, InterruptedException {
        Object startLock = projectStartLocks.computeIfAbsent(projectId, ignored -> new Object());
        synchronized (startLock) {
            Project project = projectService.getOwned(projectId, userId);
            if (project.getStatus() != ProjectStatus.READY || project.getRootPath() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is not ready");
            }
            if (project.getContainerName() != null && creator.isContainerRunning(project.getContainerName())) {
                return project;
            }

            NewProjectCreation.RunInfo runInfo = creator.start(
                    project.getId(),
                    Path.of(project.getRootPath()),
                    project.getAppType()
            );
            project.setContainerName(runInfo.containerName());
            project.setHostPort(runInfo.hostPort());
            projects.save(project);
            return project;
        }
    }

    private String[] buildTerminalCommand(String containerName, UUID connectionId) {
        if (containerName == null || !containerName.matches("^cf-[a-f0-9]{8}$")) {
            throw new IllegalArgumentException("Invalid project container name");
        }
        if (isWindows()) {
            String markerFile = terminalPidFile(connectionId);
            String shellCommand = "echo $$ > " + markerFile + "; exec sh -i";
            return new String[]{"docker", "exec", "-it", "-w", "/app", containerName, "sh", "-c", shellCommand};
        }
        return new String[]{
                "script", "--quiet", "--flush", "--return", "--command",
                "docker exec -it -w /app " + containerName + " sh", "/dev/null"
        };
    }

    private Process startTerminalProcess(
            String containerName,
            UUID connectionId,
            Integer columns,
            Integer rows
    ) throws IOException {
        String[] command = buildTerminalCommand(containerName, connectionId);
        if (!isWindows()) {
            return new ProcessBuilder(command).redirectErrorStream(true).start();
        }

        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.putIfAbsent("TERM", "xterm-256color");
        return new PtyProcessBuilder(command)
                .setEnvironment(environment)
                .setRedirectErrorStream(true)
                .setInitialColumns(columns == null ? 80 : columns)
                .setInitialRows(rows == null ? 24 : rows)
                .setUseWinConPty(true)
                .start();
    }

    private String terminalPidFile(UUID connectionId) {
        if (connectionId == null) {
            throw new IllegalArgumentException("Terminal connection ID is required");
        }
        return "/tmp/codeforge-terminal-" + connectionId + ".pid";
    }

    private void runContainerControlCommand(String containerName, String script) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("docker", "exec", containerName, "sh", "-c", script)
                .redirectErrorStream(true)
                .start();
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
            throw new IOException("Terminal control command timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) {
            throw new IOException("Terminal control command failed: " + output);
        }
    }

    private void validateSize(Integer columns, Integer rows) {
        if ((columns != null && (columns < 2 || columns > 500))
                || (rows != null && (rows < 2 || rows > 300))) {
            throw new IllegalArgumentException("Terminal dimensions are outside the allowed range");
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private void pumpOutput(TerminalKey key, TerminalSession terminal) {
        try (InputStreamReader reader = new InputStreamReader(
                terminal.process.getInputStream(), StandardCharsets.UTF_8)) {
            CharBuffer buffer = CharBuffer.allocate(4096);
            int count;
            while ((count = reader.read(buffer)) != -1) {
                buffer.flip();
                String output = buffer.toString();
                buffer.clear();
                send(terminal.projectId, terminal.connectionId, terminal.userId, "OUTPUT", output, null);
            }
            if (terminal.process.isAlive()) {
                terminal.process.waitFor();
            }
            send(terminal.projectId, terminal.connectionId, terminal.userId, "EXITED",
                    "\r\nTerminal process ended.\r\n", null);
        } catch (IOException exception) {
            send(terminal.projectId, terminal.connectionId, terminal.userId, "ERROR", null,
                    "Could not read project terminal output");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            terminals.remove(key, terminal);
            closeProcess(terminal.process);
        }
    }

    private void send(UUID projectId, UUID connectionId, UUID userId, String type, String data, String error) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/terminal/" + connectionId,
                new TerminalEvent(projectId, connectionId, type, data, error)
        );
    }

    private void close(TerminalKey key) {
        TerminalSession terminal = terminals.remove(key);
        if (terminal != null) {
            closeProcess(terminal.process);
        }
    }

    private void closeProcess(Process process) {
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            process.destroy();
        }
        if (process.isAlive()) {
            process.destroy();
        }
        try {
            if (process.isAlive() && !process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private String safeMessage(Exception exception) {
        if (exception instanceof ResponseStatusException statusException) {
            return statusException.getReason() == null ? "Project is not available" : statusException.getReason();
        }
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Could not start the project terminal" : message;
    }

    private record TerminalKey(String stompSessionId, UUID connectionId) {
    }

    private record TerminalSession(
            UUID projectId,
            UUID userId,
            UUID connectionId,
            String stompSessionId,
            String containerName,
            String pidFile,
            Process process
    ) {
    }
}
