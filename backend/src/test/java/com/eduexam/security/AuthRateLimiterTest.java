package com.eduexam.security;

import com.eduexam.common.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthRateLimiterTest {

    @Test
    void blocksRepeatedLoginFailuresAndSuccessClearsTheBucket() {
        AuthRateLimiter limiter = new AuthRateLimiter();
        String ip = "test-" + UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.assertLoginAllowed(ip, "student"));
            limiter.recordLoginFailure(ip, "student");
        }

        AppException blocked = assertThrows(AppException.class,
                () -> limiter.assertLoginAllowed(ip, "student"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getStatus());

        limiter.recordLoginSuccess(ip, "student");
        assertDoesNotThrow(() -> limiter.assertLoginAllowed(ip, "student"));
    }

    @Test
    void limitsRegistrationBurstsPerRemoteAddress() {
        AuthRateLimiter limiter = new AuthRateLimiter();
        String ip = "test-" + UUID.randomUUID();
        for (int i = 0; i < 10; i++) limiter.consumeRegistration(ip);

        AppException blocked = assertThrows(AppException.class,
                () -> limiter.consumeRegistration(ip));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getStatus());
    }

    @Test
    void blocksDistributedUsernameGuessingFromOneAddress() {
        AuthRateLimiter limiter = new AuthRateLimiter();
        String ip = "test-" + UUID.randomUUID();
        for (int i = 0; i < 25; i++) {
            assertDoesNotThrow(() -> limiter.assertLoginAllowed(ip, "user-" + UUID.randomUUID()));
            limiter.recordLoginFailure(ip, "user-" + i);
        }

        AppException blocked = assertThrows(AppException.class,
                () -> limiter.assertLoginAllowed(ip, "another-user"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getStatus());
    }

    @Test
    void blockedAddressDoesNotAllocateBucketsForRandomUsernames() throws Exception {
        AuthRateLimiter limiter = new AuthRateLimiter();
        String ip = "test-" + UUID.randomUUID();
        for (int i = 0; i < 25; i++) {
            limiter.recordLoginFailure(ip, "user-" + i);
        }

        Field field = AuthRateLimiter.class.getDeclaredField("loginFailures");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ?> failures = (Map<String, ?>) field.get(limiter);
        int bucketsBefore = failures.size();

        for (int i = 0; i < 100; i++) {
            assertThrows(AppException.class,
                    () -> limiter.assertLoginAllowed(ip, "random-" + UUID.randomUUID()));
        }
        assertEquals(bucketsBefore, failures.size());
    }

    @Test
    void registrationLimitIsAtomicUnderConcurrentRequests() throws Exception {
        AuthRateLimiter limiter = new AuthRateLimiter();
        String ip = "test-" + UUID.randomUUID();
        int requests = 40;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        limiter.consumeRegistration(ip);
                        accepted.incrementAndGet();
                    } catch (AppException ignored) {
                        // Expected once the ten atomic slots have been consumed.
                    }
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) future.get();
        } finally {
            pool.shutdownNow();
        }
        assertEquals(10, accepted.get());
    }

    @Test
    void concurrentLoginWorkIsBoundedBeforePasswordHashing() throws Exception {
        AuthRateLimiter limiter = new AuthRateLimiter();
        String ip = "test-" + UUID.randomUUID();
        int requests = 24;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch attempted = new CountDownLatch(requests);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                String username = "user-" + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try (AuthRateLimiter.LoginPermit ignored = limiter.acquireLoginPermit(ip, username)) {
                        accepted.incrementAndGet();
                        attempted.countDown();
                        release.await();
                    } catch (AppException ignored) {
                        attempted.countDown();
                    }
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            attempted.await();
            assertEquals(8, accepted.get());
            release.countDown();
            for (Future<?> future : futures) future.get();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
