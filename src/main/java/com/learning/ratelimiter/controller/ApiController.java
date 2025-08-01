// FILE PATH: src/main/java/com/learning/ratelimiter/controller/ApiController.java

package com.learning.ratelimiter.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/hello")
    public ResponseEntity<Map<String, Object>> hello() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello, World!");
        response.put("timestamp", LocalDateTime.now());
        response.put("endpoint", "/api/hello");

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/data/{id}")
    public ResponseEntity<Map<String, Object>> getData(@PathVariable String id) {
        // Mock database with sample data
        Map<String, Map<String, Object>> mockDatabase = new HashMap<>();

        // Sample data
        Map<String, Object> user1 = new HashMap<>();
        user1.put("id", "1");
        user1.put("name", "Sumit");
        user1.put("role", "Developer");
        user1.put("department", "Engineering");

        Map<String, Object> user2 = new HashMap<>();
        user2.put("id", "2");
        user2.put("name", "Priya");
        user2.put("role", "Designer");
        user2.put("department", "Product");

        Map<String, Object> user3 = new HashMap<>();
        user3.put("id", "3");
        user3.put("name", "Rahul");
        user3.put("role", "Manager");
        user3.put("department", "Operations");

        mockDatabase.put("1", user1);
        mockDatabase.put("2", user2);
        mockDatabase.put("3", user3);

        Map<String, Object> response = new HashMap<>();

        if (mockDatabase.containsKey(id)) {
            response.put("success", true);
            response.put("data", mockDatabase.get(id));
            response.put("timestamp", LocalDateTime.now());
            return new ResponseEntity<>(response, HttpStatus.OK);
        } else {
            response.put("success", false);
            response.put("error", "User not found with ID: " + id);
            response.put("timestamp", LocalDateTime.now());
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitData(@RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Simulate data processing
            Thread.sleep(100); // Simulate some processing time

            response.put("success", true);
            response.put("message", "Data submitted successfully");
            response.put("submittedData", data);
            response.put("processedAt", LocalDateTime.now());
            response.put("submissionId", "SUB_" + System.currentTimeMillis());

            return new ResponseEntity<>(response, HttpStatus.CREATED);

        } catch (InterruptedException e) {
            response.put("success", false);
            response.put("error", "Processing interrupted");
            response.put("timestamp", LocalDateTime.now());

            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/limited")
    public ResponseEntity<Map<String, Object>> limitedEndpoint() {
        // This endpoint will have stricter rate limiting (we'll configure this later)
        Map<String, Object> response = new HashMap<>();
        response.put("message", "This is a rate-limited endpoint!");
        response.put("warning", "This endpoint has strict rate limits");
        response.put("timestamp", LocalDateTime.now());
        response.put("allowedRequests", "Only 3 requests per minute");

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "Rate Limiter API");
        response.put("status", "UP");
        response.put("version", "1.0.0");
        response.put("timestamp", LocalDateTime.now());
        response.put("endpoints", new String[]{
                "/api/hello",
                "/api/data/{id}",
                "/api/submit",
                "/api/limited",
                "/api/status"
        });

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/bulk-test")
    public ResponseEntity<Map<String, Object>> bulkTest() {
        // Endpoint designed for testing rate limiting with multiple requests
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Bulk test endpoint - make multiple requests to test rate limiting");
        response.put("suggestion", "Try making 20+ requests quickly to see rate limiting in action");
        response.put("timestamp", LocalDateTime.now());
        response.put("requestNumber", System.currentTimeMillis() % 1000);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}