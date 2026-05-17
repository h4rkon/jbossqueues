package dev.hzd.jbossqueues.queue;

import java.time.Clock;
import java.util.logging.Logger;

public class CircuitBreaker {
    private final Logger logger = Logger.getLogger(CircuitBreaker.class.getName());
    private final Clock clock;
    private final String name;
    private final int failureThreshold;
    private final long openMillis;
    private int failures;
    private long openedAtMillis;

    public CircuitBreaker(String name, int failureThreshold, long openMillis) {
        this(name, failureThreshold, openMillis, Clock.systemUTC());
    }

    CircuitBreaker(String name, int failureThreshold, long openMillis, Clock clock) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openMillis = openMillis;
        this.clock = clock;
    }

    public synchronized boolean allowRequest() {
        if (!open()) {
            return true;
        }

        if (clock.millis() - openedAtMillis >= openMillis) {
            logger.info(() -> "Circuit breaker half-open: " + name);
            return true;
        }

        return false;
    }

    public synchronized void recordSuccess() {
        if (failures > 0 || open()) {
            logger.info(() -> "Circuit breaker closed: " + name);
        }
        failures = 0;
        openedAtMillis = 0;
    }

    public synchronized void recordFailure() {
        failures++;
        if (!open() && failures >= failureThreshold) {
            openedAtMillis = clock.millis();
            logger.warning(() -> "Circuit breaker opened: %s after %d failures".formatted(name, failures));
        }
    }

    private boolean open() {
        return openedAtMillis > 0;
    }
}
