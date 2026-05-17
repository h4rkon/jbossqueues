package dev.hzd.jbossqueues.queue.kafka;

import dev.hzd.jbossqueues.queue.QueueConfiguration;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;

@Singleton
public class KafkaQueueConfiguration implements QueueConfiguration {
    private static final String BOOTSTRAP_ENV = "KAFKA_BOOTSTRAP_SERVERS";
    private static final String TOPIC_ENV = "KAFKA_TOPIC_MESSAGES";
    private static final String GROUP_ENV = "KAFKA_CONSUMER_GROUP";

    @Resource(name = "kafka/bootstrapServers")
    private String bootstrapServers;

    @Resource(name = "kafka/topicMessages")
    private String topicMessages;

    @Resource(name = "kafka/consumerGroup")
    private String consumerGroup;

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
    public String consumerGroup() {
        return configuredValue(consumerGroup, GROUP_ENV, "jbossqueues-demo");
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
}
