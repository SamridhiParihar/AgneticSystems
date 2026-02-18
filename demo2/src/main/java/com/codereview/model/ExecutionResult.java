package com.codereview.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;

/**
 * Result from running code in Docker
 * WHY: The EXECUTOR needs to know if code worked or failed
 *
 * @Embeddable: This is NOT a separate table, it's embedded in Attempt
 * WHY: ExecutionResult doesn't exist independently - it's part of an Attempt
 *      we didn't use base entity because conceptually it is having relation of is-an not has-an
 */
@Embeddable
public class ExecutionResult {

    private boolean success;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String stdout;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String stderr;

    private int exitCode;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String error;

    public ExecutionResult() {}

    public ExecutionResult(boolean success, String stdout, String stderr, int exitCode) {
        this.success = success;
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
    }

    // Getters and setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getStdout() { return stdout; }
    public void setStdout(String stdout) { this.stdout = stdout; }
    public String getStderr() { return stderr; }
    public void setStderr(String stderr) { this.stderr = stderr; }
    public int getExitCode() { return exitCode; }
    public void setExitCode(int exitCode) { this.exitCode = exitCode; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}