package com.eduexam.security;

import com.eduexam.common.AppException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight single-node protection for the public authentication endpoints.
 * It deliberately keys login failures by both remote address and username, so
 * one mistyped account cannot block every user behind the same classroom NAT.
 */
@Component
public class AuthRateLimiter {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final int MAX_LOGIN_FAILURES_PER_ADDRESS = 25;
    private static final int MAX_CONCURRENT_LOGINS_PER_IDENTITY = 2;
    private static final int MAX_CONCURRENT_LOGINS_PER_ADDRESS = 8;
    private static final long LOGIN_WINDOW_MS = Duration.ofMinutes(15).toMillis();
    private static final int MAX_REGISTRATIONS = 10;
    private static final long REGISTRATION_WINDOW_MS = Duration.ofHours(1).toMillis();
    private static final int MAX_TRACKED_KEYS = 100_000;

    private final Map<String, Deque<Long>> loginFailures = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> addressLoginFailures = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> registrations = new ConcurrentHashMap<>();
    private final Map<String, Integer> loginInFlight = new ConcurrentHashMap<>();
    private final Map<String, Integer> addressLoginInFlight = new ConcurrentHashMap<>();
    private final AtomicInteger operations = new AtomicInteger();

    public void assertLoginAllowed(String remoteAddress, String username) {
        maintainCapacity();
        if (eventCount(addressLoginFailures, normalize(remoteAddress), LOGIN_WINDOW_MS, false)
                >= MAX_LOGIN_FAILURES_PER_ADDRESS) {
            throw AppException.tooManyRequests("当前地址登录失败次数过多，请 15 分钟后再试");
        }
        String key = loginKey(remoteAddress, username);
        if (eventCount(loginFailures, key, LOGIN_WINDOW_MS, false) >= MAX_LOGIN_FAILURES) {
            throw AppException.tooManyRequests("登录失败次数过多，请 15 分钟后再试");
        }
    }

    /**
     * Reserves a small, bounded login-work slot before the database lookup and
     * password hash comparison begin. Failure counters alone are insufficient
     * here: a burst of requests can otherwise all pass the counter check before
     * any one of them has had time to record its failed password.
     */
    public LoginPermit acquireLoginPermit(String remoteAddress, String username) {
        assertLoginAllowed(remoteAddress, username);
        String addressKey = normalize(remoteAddress);
        String identityKey = loginKey(remoteAddress, username);
        if (!tryAcquire(addressLoginInFlight, addressKey, MAX_CONCURRENT_LOGINS_PER_ADDRESS)) {
            throw AppException.tooManyRequests("当前地址的登录请求过多，请稍后再试");
        }
        if (!tryAcquire(loginInFlight, identityKey, MAX_CONCURRENT_LOGINS_PER_IDENTITY)) {
            release(addressLoginInFlight, addressKey);
            throw AppException.tooManyRequests("该账号的登录请求过多，请稍后再试");
        }
        return new LoginPermit(addressKey, identityKey);
    }

    public void recordLoginFailure(String remoteAddress, String username) {
        maintainCapacity();
        String key = loginKey(remoteAddress, username);
        eventCount(loginFailures, key, LOGIN_WINDOW_MS, true);
        eventCount(addressLoginFailures, normalize(remoteAddress), LOGIN_WINDOW_MS, true);
    }

    public void recordLoginSuccess(String remoteAddress, String username) {
        loginFailures.remove(loginKey(remoteAddress, username));
    }

    public void consumeRegistration(String remoteAddress) {
        maintainCapacity();
        String key = normalize(remoteAddress);
        if (!consumeEvent(registrations, key, REGISTRATION_WINDOW_MS, MAX_REGISTRATIONS)) {
            throw AppException.tooManyRequests("注册请求过于频繁，请稍后再试");
        }
    }

    private boolean consumeEvent(Map<String, Deque<Long>> store, String key, long windowMs, int limit) {
        long now = System.currentTimeMillis();
        long cutoff = now - windowMs;
        AtomicBoolean consumed = new AtomicBoolean();
        store.compute(key, (ignored, existing) -> {
            Deque<Long> events = existing != null ? existing : new ArrayDeque<>();
            while (!events.isEmpty() && events.peekFirst() < cutoff) events.removeFirst();
            if (events.size() < limit) {
                events.addLast(now);
                consumed.set(true);
            }
            return events;
        });
        return consumed.get();
    }

    private boolean tryAcquire(Map<String, Integer> store, String key, int limit) {
        AtomicBoolean acquired = new AtomicBoolean();
        store.compute(key, (ignored, current) -> {
            int next = current == null ? 1 : current + 1;
            if (next > limit) return current;
            acquired.set(true);
            return next;
        });
        return acquired.get();
    }

    private void release(Map<String, Integer> store, String key) {
        store.computeIfPresent(key, (ignored, current) -> current <= 1 ? null : current - 1);
    }

    private int eventCount(Map<String, Deque<Long>> store, String key, long windowMs, boolean add) {
        long now = System.currentTimeMillis();
        long cutoff = now - windowMs;
        AtomicInteger count = new AtomicInteger();
        if (add) {
            store.compute(key, (ignored, existing) -> {
                Deque<Long> events = existing != null ? existing : new ArrayDeque<>();
                while (!events.isEmpty() && events.peekFirst() < cutoff) events.removeFirst();
                events.addLast(now);
                count.set(events.size());
                return events;
            });
        } else {
            store.computeIfPresent(key, (ignored, events) -> {
                while (!events.isEmpty() && events.peekFirst() < cutoff) events.removeFirst();
                count.set(events.size());
                return events.isEmpty() ? null : events;
            });
        }
        return count.get();
    }

    private String loginKey(String remoteAddress, String username) {
        return normalize(remoteAddress) + ':' + normalize(username);
    }

    private String normalize(String value) {
        return value == null ? "unknown" : value.strip().toLowerCase(java.util.Locale.ROOT);
    }

    private void cleanupOccasionally() {
        if (operations.incrementAndGet() % 256 != 0) return;
        pruneAll();
    }

    private void maintainCapacity() {
        cleanupOccasionally();
        if (trackedKeyCount() < MAX_TRACKED_KEYS) return;
        pruneAll();
        if (trackedKeyCount() >= MAX_TRACKED_KEYS) {
            // Fail closed under sustained identifier/IP churn instead of
            // allowing unauthenticated requests to grow memory without bound.
            throw AppException.tooManyRequests("认证请求过于频繁，请稍后再试");
        }
    }

    private int trackedKeyCount() {
        return loginFailures.size() + addressLoginFailures.size() + registrations.size()
                + loginInFlight.size() + addressLoginInFlight.size();
    }

    private void pruneAll() {
        prune(loginFailures, LOGIN_WINDOW_MS);
        prune(addressLoginFailures, LOGIN_WINDOW_MS);
        prune(registrations, REGISTRATION_WINDOW_MS);
    }

    private void prune(Map<String, Deque<Long>> store, long windowMs) {
        long cutoff = System.currentTimeMillis() - windowMs;
        for (String key : store.keySet()) {
            store.computeIfPresent(key, (ignored, events) -> {
                while (!events.isEmpty() && events.peekFirst() < cutoff) events.removeFirst();
                return events.isEmpty() ? null : events;
            });
        }
    }

    public final class LoginPermit implements AutoCloseable {
        private final String addressKey;
        private final String identityKey;
        private final AtomicBoolean closed = new AtomicBoolean();

        private LoginPermit(String addressKey, String identityKey) {
            this.addressKey = addressKey;
            this.identityKey = identityKey;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            release(loginInFlight, identityKey);
            release(addressLoginInFlight, addressKey);
        }
    }
}
