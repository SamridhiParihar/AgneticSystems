package com.task_breakdown_assistant.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExecutorResponse {
    private String stepDescription;
    private String result;
    private String thought;
    private boolean isComplete;
    private String nextStep;
}
