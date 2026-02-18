package com.codereview.model;

/**
 * Input from user - the code we want to be reviewed
 * WHY: Clean separation of what comes from API vs internal state
 */
public class CodeReviewRequest {
    private String code;
    private String language; // "python" or "javascript"

    public CodeReviewRequest() {}

    public CodeReviewRequest(String code, String language) {
        this.code = code;
        this.language = language;
    }

    // Getters and setters
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}