package dev.hzd.jbossqueues.queue;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class Retry {
    private static final Logger LOGGER = Logger.getLogger(Retry.class.getName());

    private Retry() {
    }

    public static void run(String operation, int maxAttempts, long backoffMillis, Retryable action) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run(attempt);
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                LOGGER.log(Level.WARNING, "%s failed on attempt %d/%d".formatted(operation, attempt, maxAttempts), exception);
                backoff(attempt, maxAttempts, backoffMillis);
            }
        }
        throw lastFailure;
    }

    private static void backoff(int attempt, int maxAttempts, long backoffMillis) {
        if (attempt >= maxAttempts || backoffMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", exception);
        }
    }

    @FunctionalInterface
    public interface Retryable {
        void run(int attempt);
    }
}
