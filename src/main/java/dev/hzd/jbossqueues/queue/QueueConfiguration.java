package dev.hzd.jbossqueues.queue;

public interface QueueConfiguration {

    boolean enabled();

    String bootstrapServers();

    String topic();

    String deadLetterTopic();

    String consumerGroup();

    int maxConsumerAttempts();

    long retryBackoffMillis();

    int circuitBreakerFailureThreshold();

    long circuitBreakerOpenMillis();
}
