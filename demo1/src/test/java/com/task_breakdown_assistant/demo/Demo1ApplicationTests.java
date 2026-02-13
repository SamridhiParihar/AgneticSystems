package com.task_breakdown_assistant.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class Demo1ApplicationTests {

    @Test
    void contextLoads() {
        // Test will pass if the application context loads successfully
    }
}
