package dev.hzd.jbossqueues.queue.kafka;

import dev.hzd.jbossqueues.queue.QueueConfiguration;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;

@Singleton
public class KafkaQueueConfiguration implements QueueConfiguration {
    private static final String BOOTSTRAP_ENV = "KAFKA_BOOTSTRAP_SERVERS";
    private static final String TOPIC_ENV = "KAFKA_TOPIC_MESSAGES";
    private static final String DLQ_TOPIC_ENV = "KAFKA_TOPIC_MESSAGES_DLQ";
    private static final String GROUP_ENV = "KAFKA_CONSUMER_GROUP";
    private static final String MAX_ATTEMPTS_ENV = "QUEUE_MAX_CONSUMER_ATTEMPTS";
    private static final String RETRY_BACKOFF_ENV = "QUEUE_RETRY_BACKOFF_MILLIS";
    private static final String CIRCUIT_FAILURE_THRESHOLD_ENV = "QUEUE_CIRCUIT_FAILURE_THRESHOLD";
    private static final String CIRCUIT_OPEN_ENV = "QUEUE_CIRCUIT_OPEN_MILLIS";

    @Resource(name = "kafka/bootstrapServers")
    private String bootstrapServers;

    @Resource(name = "kafka/topicMessages")
    private String topicMessages;

    @Resource(name = "kafka/topicMessagesDlq")
    private String topicMessagesDlq;

    @Resource(name = "kafka/consumerGroup")
    private String consumerGroup;

    @Resource(name = "queue/maxConsumerAttempts")
    private String maxConsumerAttempts;

    @Resource(name = "queue/retryBackoffMillis")
    private String retryBackoffMillis;

    @Resource(name = "queue/circuitFailureThreshold")
    private String circuitFailureThreshold;

    @Resource(name = "queue/circuitOpenMillis")
    private String circuitOpenMillis;

    @Override
    public boolean enabled() {
        return bootstrapServers() != null && topic() != null;
    }

    @Override
    public String bootstrapServers() {
        return configuredValue(bootstrapServers, BOOTSTRAP_ENV, null);
    }

    @Override
    public String topic() {
        return configuredValue(topicMessages, TOPIC_ENV, null);
    }

    @Override
    public String deadLetterTopic() {
        return configuredValue(topicMessagesDlq, DLQ_TOPIC_ENV, topic() + ".dlq");
    }

    @Override
    public String consumerGroup() {
        return configuredValue(consumerGroup, GROUP_ENV, "jbossqueues-demo");
    }

    @Override
    public int maxConsumerAttempts() {
        return configuredInt(maxConsumerAttempts, MAX_ATTEMPTS_ENV, 3);
    }

    @Override
    public long retryBackoffMillis() {
        return configuredLong(retryBackoffMillis, RETRY_BACKOFF_ENV, 500);
    }

    @Override
    public int circuitBreakerFailureThreshold() {
        return configuredInt(circuitFailureThreshold, CIRCUIT_FAILURE_THRESHOLD_ENV, 5);
    }

    @Override
    public long circuitBreakerOpenMillis() {
        return configuredLong(circuitOpenMillis, CIRCUIT_OPEN_ENV, 10_000);
    }

    private String configuredValue(String configuredValue, String environmentName, String defaultValue) {
        String value = blankToNull(configuredValue);
        if (value != null && !value.startsWith("${")) {
            return value;
        }

        String environmentValue = blankToNull(System.getenv(environmentName));
        return environmentValue == null ? defaultValue : environmentValue;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private int configuredInt(String configuredValue, String environmentName, int defaultValue) {
        return Integer.parseInt(configuredValue(configuredValue, environmentName, Integer.toString(defaultValue)));
    }

    private long configuredLong(String configuredValue, String environmentName, long defaultValue) {
        return Long.parseLong(configuredValue(configuredValue, environmentName, Long.toString(defaultValue)));
    }
}
