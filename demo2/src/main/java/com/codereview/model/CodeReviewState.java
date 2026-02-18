package com.codereview.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Complete state of the code review process
 * WHY: Agent needs to track everything across the reflection loop
 *
 */
@Entity
@Table(name = "code_review_states")
public class CodeReviewState {

    @Id
    private String id;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String originalCode;

    private String language;

    // Planning phase
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "state_id") // Foreign key in Issue table
    private List<Issue> identifiedIssues;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String fixPlan;

    // Execution loop tracking
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "state_id") // Foreign key in Attempt table
    private List<Attempt> attempts;

    private int currentAttemptNumber;

    @Transient // Not stored in DB - it's a constant
    private static final int MAX_ATTEMPTS = 3;

    // Final state
    private boolean isFixed;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String finalCode;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String finalReport;

    public CodeReviewState() {
        this.id = UUID.randomUUID().toString();
        this.identifiedIssues = new ArrayList<>();
        this.attempts = new ArrayList<>();
        this.currentAttemptNumber = 0;
        this.isFixed = false;
    }

    public CodeReviewState(String originalCode, String language) {
        this();
        this.originalCode = originalCode;
        this.language = language;
    }

    /**
     * Check if we should continue the loop
     * WHY: Reflection agent needs clear stopping conditions
     */
    public boolean shouldContinue() {
        return !isFixed && currentAttemptNumber < MAX_ATTEMPTS;
    }

    /**
     * Add a new attempt to history
     * WHY: Reflection needs to see what was tried before
     */
    public void addAttempt(Attempt attempt) {
        this.attempts.add(attempt);
        this.currentAttemptNumber++;
    }

    /**
     * Get the last attempt for reflection
     */
    public Attempt getLastAttempt() {
        if (attempts.isEmpty()) {
            return null;
        }
        return attempts.get(attempts.size() - 1);
    }

    // Getters and setters (same as before)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOriginalCode() { return originalCode; }
    public void setOriginalCode(String code) { this.originalCode = code; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public List<Issue> getIdentifiedIssues() { return identifiedIssues; }
    public void setIdentifiedIssues(List<Issue> issues) { this.identifiedIssues = issues; }
    public String getFixPlan() { return fixPlan; }
    public void setFixPlan(String plan) { this.fixPlan = plan; }
    public List<Attempt> getAttempts() { return attempts; }
    public int getCurrentAttemptNumber() { return currentAttemptNumber; }
    public void setCurrentAttemptNumber(int num) { this.currentAttemptNumber = num; }
    public boolean isFixed() { return isFixed; }
    public void setFixed(boolean fixed) { this.isFixed = fixed; }
    public String getFinalCode() { return finalCode; }
    public void setFinalCode(String code) { this.finalCode = code; }
    public String getFinalReport() { return finalReport; }
    public void setFinalReport(String report) { this.finalReport = report; }
    public int getMaxAttempts() { return MAX_ATTEMPTS; }
}