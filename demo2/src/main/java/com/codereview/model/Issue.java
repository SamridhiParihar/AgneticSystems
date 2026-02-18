package com.codereview.model;

import jakarta.persistence.*;

/**
 * Represents a single problem found in the code
 * WHY: The PLANNING AGENT needs to identify specific issues
 *
 * JPA: This is a child entity owned by CodeReviewState
 */
@Entity
@Table(name = "issues")
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String description;

    private int lineNumber;
    private String severity; // "error", "warning", "info"
    private String category; // "syntax", "logic", "performance", "style"

    public Issue() {}

    public Issue(String description, int lineNumber, String severity, String category) {
        this.description = description;
        this.lineNumber = lineNumber;
        this.severity = severity;
        this.category = category;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}