package com.codereview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot Application
 */
@SpringBootApplication
public class CodeReviewApplication {

	public static void main(String[] args) {
		System.out.println("Starting Code Review Agent...");
		SpringApplication.run(CodeReviewApplication.class, args);
		System.out.println("Application started successfully!");
		System.out.println("API available at: http://localhost:8080/api/review");
		System.out.println("Health check at: http://localhost:8080/api/health");
	}
}