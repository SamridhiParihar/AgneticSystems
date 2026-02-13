package com.task_breakdown_assistant.demo.controller;

import com.task_breakdown_assistant.demo.model.ExecutorResponse;
import com.task_breakdown_assistant.demo.model.Request;
import com.task_breakdown_assistant.demo.model.Response;
import com.task_breakdown_assistant.demo.service.TaskBreakDownService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskBreakDownController {

    private final TaskBreakDownService service ;

    @PostMapping("/agent/start")
    public ResponseEntity<Response> start(@RequestBody Request request) {
        Response response = service.startAgent(request);
        return new ResponseEntity<>(response,HttpStatus.OK);
    }

    @PostMapping("/agent/next/{id}")
    public ResponseEntity<ExecutorResponse> executeNext(@PathVariable String id) {
        ExecutorResponse executorResponse = service.executeNext(id).orElse(new ExecutorResponse());
        return new ResponseEntity<>(executorResponse, HttpStatus.OK);
    }

    @PostMapping("/agent/run/{id}")
    public Flux<ExecutorResponse> runAutonomously(@PathVariable String id) {
        return service.runAutonomously(id);
    }
}
