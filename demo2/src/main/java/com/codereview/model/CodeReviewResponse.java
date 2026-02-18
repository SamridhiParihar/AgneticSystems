package com.codereview.model;

import java.util.List;

/**
 * Final output to user
 * WHY: Clean API contract for what we return
 */
public class CodeReviewResponse {
    private String reviewId;
    private List<Issue> issuesFound;
    private String fixPlan;
    private List<Attempt> attempts;
    private boolean success;
    private String finalCode;
    private String report;

    public CodeReviewResponse() {}

    // Getters and setters
    public String getReviewId() { return reviewId; }
    public void setReviewId(String id) { this.reviewId = id; }
    public List<Issue> getIssuesFound() { return issuesFound; }
    public void setIssuesFound(List<Issue> issues) { this.issuesFound = issues; }
    public String getFixPlan() { return fixPlan; }
    public void setFixPlan(String plan) { this.fixPlan = plan; }
    public List<Attempt> getAttempts() { return attempts; }
    public void setAttempts(List<Attempt> attempts) { this.attempts = attempts; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getFinalCode() { return finalCode; }
    public void setFinalCode(String code) { this.finalCode = code; }
    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }
}