package dev.hzd.jbossqueues.queue.kafka;

import dev.hzd.jbossqueues.queue.QueueConfiguration;
import jakarta.ejb.Singleton;

@Singleton
public class KafkaQueueConfiguration implements QueueConfiguration {
    private static final String BOOTSTRAP_ENV = "KAFKA_BOOTSTRAP_SERVERS";
    private static final String TOPIC_ENV = "KAFKA_TOPIC_MESSAGES";
    private static final String DLQ_TOPIC_ENV = "KAFKA_TOPIC_MESSAGES_DLQ";
    private static final String GROUP_ENV = "KAFKA_CONSUMER_GROUP";
    private static final String RETRY_BACKOFF_ENV = "QUEUE_RETRY_BACKOFF_MILLIS";

    @Override
    public boolean enabled() {
        return bootstrapServers() != null && topic() != null;
    }

    @Override
    public String bootstrapServers() {
        return env(BOOTSTRAP_ENV, null);
    }

    @Override
    public String topic() {
        return env(TOPIC_ENV, null);
    }

    @Override
    public String deadLetterTopic() {
        return env(DLQ_TOPIC_ENV, topic() + ".dlq");
    }

    @Override
    public String consumerGroup() {
        return env(GROUP_ENV, "jbossqueues-demo");
    }

    @Override
    public long retryBackoffMillis() {
        return Long.parseLong(env(RETRY_BACKOFF_ENV, "500"));
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
