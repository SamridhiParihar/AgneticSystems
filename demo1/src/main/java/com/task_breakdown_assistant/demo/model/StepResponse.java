package com.task_breakdown_assistant.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StepResponse {
    private List<Step> steps;
    private List<String> thoughts;
}
