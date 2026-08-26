package com.flashsale.inventory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrencyLoadTest {

    // Adjust this port if your api-gateway runs on something else
    private static final String TARGET_URL = "http://localhost:8081/api/inventory/reserve";
    private static final int CONCURRENT_USERS = 500;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Starting high-concurrency test with " + CONCURRENT_USERS + " virtual threads...");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyHeldCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        ConcurrentHashMap<Integer, Integer> responseCodes = new ConcurrentHashMap<>();

        // Latch to hold all threads at the starting line so they fire at the exact same millisecond
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(CONCURRENT_USERS);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CONCURRENT_USERS; i++) {
                final String userId = "user-" + i;

                executor.submit(() -> {
                    try {
                        String jsonPayload = """
                            {
                                "eventId": "TAYLOR-SWIFT-2026",
                                "seatId": "VIP-A1",
                                "userId": "%s"
                            }
                            """.formatted(userId);

                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(TARGET_URL))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                                .build();

                        // Wait at the gate
                        startGate.await();

                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                        responseCodes.merge(response.statusCode(), 1, Integer::sum);

                        if (response.body().contains("\"status\":\"RESERVED\"")) {
                            successCount.incrementAndGet();
                        } else if (response.body().contains("ALREADY_HELD")) {
                            alreadyHeldCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        endGate.countDown();
                    }
                });
            }

            System.out.println("All threads primed. Firing requests...");
            long startTime = System.currentTimeMillis();

            // Open the gate
            startGate.countDown();

            // Wait for all requests to finish
            endGate.await();
            long duration = System.currentTimeMillis() - startTime;

            System.out.println("\n--- TEST RESULTS ---");
            System.out.println("Total Time: " + duration + " ms");
            System.out.println("Successful Reservations (Should be exactly 1): " + successCount.get());
            System.out.println("Rejected (Already Held): " + alreadyHeldCount.get());
            System.out.println("Errors/Timeouts: " + errorCount.get());
            System.out.println("HTTP Status Codes: " + responseCodes);

            if (successCount.get() > 1) {
                System.err.println("CRITICAL FAILURE: Overselling detected! " + successCount.get() + " users claimed the same seat.");
            } else if (successCount.get() == 0) {
                System.err.println("FAILURE: No one got the seat.");
            } else {
                System.out.println("SUCCESS: Architecture holds up. Zero overselling.");
            }
        }
    }
}