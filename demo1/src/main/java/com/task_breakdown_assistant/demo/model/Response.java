package com.task_breakdown_assistant.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Response {
    private String agentId  ;
    private StepResponse stepResponse;
}
