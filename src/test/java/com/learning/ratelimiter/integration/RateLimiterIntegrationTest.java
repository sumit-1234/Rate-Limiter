package com.learning.ratelimiter.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RateLimiterIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void shouldRateLimitHelloEndpoint(){
        String url="http://localhost:"+port+"/api/hello";
        for(int i=1;i<=10;i++){
            ResponseEntity<String> response=restTemplate.getForEntity(url, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            assertThat(response.getHeaders().getFirst("X-RateLimit-Remaining")).isNotNull();
            assertThat(response.getHeaders().getFirst("X-RateLimit-Algorithm")).isEqualTo("FIXED_WINDOW");
            // Check remaining requests decreases
            String remainingHeader = response.getHeaders().getFirst("X-RateLimit-Remaining");
            int remaining = Integer.parseInt(remainingHeader);
            assertThat(remaining).isEqualTo(10 - i); // Should decrease: 9, 8, 7, ..., 0

            System.out.println("Request " + i + ": Status=" + response.getStatusCode() +
                    ", Remaining=" + remaining);

        }
        ResponseEntity<String> blockedResponse=restTemplate.getForEntity(url, String.class);
        assertThat(blockedResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Verify remaining is 0
        String remainingHeader = blockedResponse.getHeaders().getFirst("X-RateLimit-Remaining");
        assertThat(remainingHeader).isEqualTo("0");

        System.out.println("Request 11: Status=" + blockedResponse.getStatusCode() +
                ", Remaining=" + remainingHeader);
    }
    // Test that /api/limited allows only 3 requests
    @Test
    void shouldRateLimitLimitedEndpoint() {
        String url="http://localhost:"+port+"/api/limited";
        // Make 3 requests - all should succeed
        for(int i=1;i<=3;i++){
            ResponseEntity<String> response=restTemplate.getForEntity(url, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getFirst("X-RateLimit-Remaining")).isNotNull();
            assertThat(response.getHeaders().getFirst("X-RateLimit-Algorithm")).isEqualTo("SLIDING_WINDOW");
            // Check remaining requests decreases
            String remainingHeader = response.getHeaders().getFirst("X-RateLimit-Remaining");
            int remaining = Integer.parseInt(remainingHeader);
            assertThat(remaining).isEqualTo(3 - i); // Should decrease: 9, 8, 7, ..., 0

            System.out.println("Request " + i + ": Status=" + response.getStatusCode() +
                    ", Remaining=" + remaining);

        }
        // Make 4th request - should fail
        ResponseEntity<String> blockedResponse=restTemplate.getForEntity(url, String.class);
        assertThat(blockedResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // Verify remaining is 0
        String remainingHeader = blockedResponse.getHeaders().getFirst("X-RateLimit-Remaining");
        assertThat(remainingHeader).isEqualTo("0");

        System.out.println("Request 11: Status=" + blockedResponse.getStatusCode() +
                ", Remaining=" + remainingHeader);
    }
    // Test different client IDs are tracked separately
    @Test
    void shouldTrackDifferentClientsIndependently() {
        String url="http://localhost:"+port+"/api/hello";

        // Create headers for Client A (using API key strategy)
        HttpHeaders headersClientA = new HttpHeaders();
        headersClientA.set("X-API-Key", "client-a-key");
        HttpEntity<String> entityClientA = new HttpEntity<>(headersClientA);

        // Create headers for Client B
        HttpHeaders headersClientB = new HttpHeaders();
        headersClientB.set("X-API-Key", "client-b-key");
        HttpEntity<String> entityClientB = new HttpEntity<>(headersClientB);
        // Client A makes 10 requests
        for(int i=1;i<=10;i++){
            ResponseEntity<String>  response=restTemplate.exchange(url, HttpMethod.GET,entityClientA, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            System.out.println("Client A Request " + i + ": " + response.getStatusCode());

        }
        // Client B should still be allowed
        // Client A's 11th request should be blocked
        ResponseEntity<String> blockedResponse = restTemplate.exchange(
                url, HttpMethod.GET, entityClientA, String.class);
        assertThat(blockedResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        System.out.println("Client A Request 11: " + blockedResponse.getStatusCode() + " (BLOCKED)");

        // Client B should still be allowed (independent limit)
        ResponseEntity<String> clientBResponse = restTemplate.exchange(
                url, HttpMethod.GET, entityClientB, String.class);
        assertThat(clientBResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Client B should have full quota available
        String remainingHeader = clientBResponse.getHeaders().getFirst("X-RateLimit-Remaining");
        assertThat(remainingHeader).isEqualTo("9"); // Client B's first request, 9 remaining

        System.out.println("Client B Request 1: " + clientBResponse.getStatusCode() +
                ", Remaining=" + remainingHeader + " (INDEPENDENT!)");
    }
}
