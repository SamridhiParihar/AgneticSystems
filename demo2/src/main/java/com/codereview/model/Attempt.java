package com.codereview.model;

import jakarta.persistence.*;

/**
 * Tracks each iteration of the reflection loop
 * WHY: REFLECTION AGENT needs history to avoid repeating mistakes
 *
 * JPA: Child entity owned by CodeReviewState
 */
@Entity
@Table(name = "attempts")
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int attemptNumber;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String modifiedCode;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Embedded // ExecutionResult is embedded in this table
    private ExecutionResult executionResult;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String reflection;

    public Attempt() {}

    public Attempt(int attemptNumber, String modifiedCode, String reasoning) {
        this.attemptNumber = attemptNumber;
        this.modifiedCode = modifiedCode;
        this.reasoning = reasoning;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }
    public String getModifiedCode() { return modifiedCode; }
    public void setModifiedCode(String modifiedCode) { this.modifiedCode = modifiedCode; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public ExecutionResult getExecutionResult() { return executionResult; }
    public void setExecutionResult(ExecutionResult result) { this.executionResult = result; }
    public String getReflection() { return reflection; }
    public void setReflection(String reflection) { this.reflection = reflection; }
}