package com.task_breakdown_assistant.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Step {
    private String description;
    private StepStatus status;
    private String result;
    private String thought;
    
    public enum StepStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }
}
