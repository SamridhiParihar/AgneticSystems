package com.codereview.tool;

import com.codereview.model.ExecutionResult;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Tool for executing code in Docker containers
 *
 * SECURITY MODEL:
 * 1. Code runs in isolated Docker container
 * 2. No network access (unless explicitly enabled)
 * 3. Limited CPU/memory
 * 4. Timeout protection (30 seconds max)
 * 5. Temporary files cleaned up after execution
 *
 * ARCHITECTURE:
 * executeCode() → writeCodeToFile() → runInDocker() → cleanup()
 */
@Component
public class CodeExecutionTool {

    private static final int TIMEOUT_SECONDS = 30;
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    // Docker image names (must match what you built)
    private static final String PYTHON_IMAGE = "code-executor-python";
    private static final String JS_IMAGE = "code-executor-javascript";

    /**
     * Main entry point: Execute code and return results
     *
     * @param code The source code to execute
     * @param language "python" or "javascript"
     * @return ExecutionResult with stdout/stderr/exit code
     */
    public ExecutionResult executeCode(String code, String language) {
        System.out.println("[EXECUTOR] Running " + language + " code in Docker...");

        String fileExtension;
        String dockerImage;
        String dockerCommand;

        // Determine file extension and Docker setup
        switch (language.toLowerCase()) {
            case "python":
                fileExtension = ".py";
                dockerImage = PYTHON_IMAGE;
                dockerCommand = "python";
                break;
            case "javascript":
            case "js":
                fileExtension = ".js";
                dockerImage = JS_IMAGE;
                dockerCommand = "node";
                break;
            default:
                return createErrorResult("Unsupported language: " + language);
        }

        Path tempFile = null;
        try {
            // Step 1: Write code to temporary file
            tempFile = writeCodeToFile(code, fileExtension);
            System.out.println("[EXECUTOR] Code written to: " + tempFile);

            // Step 2: Run in Docker with timeout
            ExecutionResult result = runInDocker(
                    tempFile,
                    dockerImage,
                    dockerCommand,
                    TIMEOUT_SECONDS
            );

            System.out.println("[EXECUTOR] Execution complete: " +
                    (result.isSuccess() ? "SUCCESS" : "FAILED"));

            return result;

        } catch (TimeoutException e) {
            System.err.println("[EXECUTOR] Timeout after " + TIMEOUT_SECONDS + " seconds");
            return createErrorResult("Execution timeout - code ran longer than " + TIMEOUT_SECONDS + "s");

        } catch (Exception e) {
            System.err.println("[EXECUTOR] Execution error: " + e.getMessage());
            e.printStackTrace();
            return createErrorResult("Execution failed: " + e.getMessage());

        } finally {
            // Step 3: Always clean up temporary file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    System.out.println("[EXECUTOR] Cleaned up temp file");
                } catch (Exception e) {
                    System.err.println("[EXECUTOR] Failed to delete temp file: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Write code to a temporary file
     *
     * WHY TEMP FILES:
     * - Docker needs a file to mount into the container
     * - Can't pass code directly as string (security risk with shell injection)
     *
     * @return Path to the created file
     */
    private Path writeCodeToFile(String code, String extension) throws Exception {
        // Generate unique filename
        String filename = "code_" + UUID.randomUUID().toString() + extension;
        Path filePath = Path.of(TEMP_DIR, filename);

        // Write code to file
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write(code);
        }

        return filePath;
    }

    /**
     * Execute code in Docker container with timeout
     *
     * DOCKER COMMAND BREAKDOWN:
     * docker run
     *   --rm                    → Remove container after execution
     *   --network none          → No network access (security)
     *   --memory="256m"         → Limit memory to 256MB
     *   --cpus="0.5"            → Limit to 0.5 CPU cores
     *   -v /path/to/code:/app   → Mount code file into container
     *   image-name              → Which image to use
     *   command /app/code.py    → What to execute
     *
     * WHY THESE LIMITS:
     * - Prevents infinite loops from consuming resources
     * - Prevents network-based attacks
     * - Isolates execution from host system
     */
    private ExecutionResult runInDocker(
            Path codeFile,
            String dockerImage,
            String command,
            int timeoutSeconds
    ) throws Exception {

        // Build Docker command
        List<String> dockerCommand = new ArrayList<>();
        dockerCommand.add("docker");
        dockerCommand.add("run");
        dockerCommand.add("--rm");                          // Remove after run
        dockerCommand.add("--network=none");                // No network
        dockerCommand.add("--memory=256m");                 // Memory limit
        dockerCommand.add("--cpus=0.5");                    // CPU limit
        dockerCommand.add("-v");
        dockerCommand.add(codeFile.toAbsolutePath() + ":/app/code" + getExtension(codeFile));
        dockerCommand.add(dockerImage);
        dockerCommand.add(command);
        dockerCommand.add("/app/code" + getExtension(codeFile));

        System.out.println("[EXECUTOR] Docker command: " + String.join(" ", dockerCommand));

        // Execute with timeout
        ProcessBuilder pb = new ProcessBuilder(dockerCommand);
        pb.redirectErrorStream(false); // Keep stdout and stderr separate

        Process process = pb.start();

        // Use ExecutorService for timeout handling
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ExecutionResult> future = executor.submit(() -> {
            try {
                // Read stdout
                StringBuilder stdout = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append("\n");
                    }
                }

                // Read stderr
                StringBuilder stderr = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append("\n");
                    }
                }

                // Wait for process to complete
                int exitCode = process.waitFor();

                // Create result
                ExecutionResult result = new ExecutionResult(
                        exitCode == 0,
                        stdout.toString().trim(),
                        stderr.toString().trim(),
                        exitCode
                );

                return result;

            } catch (Exception e) {
                throw new RuntimeException("Process execution failed", e);
            }
        });

        try {
            // Wait for result with timeout
            ExecutionResult result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            executor.shutdown();
            return result;

        } catch (TimeoutException e) {
            // Timeout occurred - kill the process
            future.cancel(true);
            process.destroyForcibly();
            executor.shutdownNow();
            throw e;

        } catch (Exception e) {
            executor.shutdownNow();
            throw e;
        }
    }

    /**
     * Helper: Get file extension
     */
    private String getExtension(Path file) {
        String filename = file.getFileName().toString();
        int lastDot = filename.lastIndexOf('.');
        return (lastDot > 0) ? filename.substring(lastDot) : "";
    }

    /**
     * Helper: Create error result
     */
    private ExecutionResult createErrorResult(String errorMessage) {
        ExecutionResult result = new ExecutionResult();
        result.setSuccess(false);
        result.setError(errorMessage);
        result.setExitCode(-1);
        result.setStdout("");
        result.setStderr(errorMessage);
        return result;
    }
}