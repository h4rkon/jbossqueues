package dev.hzd.jbossqueues.queue.kafka;

import dev.hzd.jbossqueues.MessagePayload;
import dev.hzd.jbossqueues.queue.AbstractMessagePublisher;
import dev.hzd.jbossqueues.queue.MessagePublisher;
import dev.hzd.jbossqueues.queue.QueueConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;

@ApplicationScoped
public class KafkaMessagePublisher extends AbstractMessagePublisher<MessagePayload>
        implements MessagePublisher<MessagePayload> {

    @Inject
    private QueueConfiguration configuration;

    private KafkaProducer<String, String> producer;

    @PostConstruct
    void start() {
        if (!configuration.enabled()) {
            logger().info("Kafka publisher disabled. Configure KAFKA_BOOTSTRAP_SERVERS and KAFKA_TOPIC_MESSAGES to enable it.");
            return;
        }

        producer = new KafkaProducer<>(producerProperties());
        logger().info(() -> "Kafka publisher enabled. bootstrapServers=%s topic=%s dlqTopic=%s"
                .formatted(configuration.bootstrapServers(), configuration.topic(), configuration.deadLetterTopic()));
    }

    @Override
    @Retry(maxRetries = 3, delay = 500)
    @CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.5, delay = 10_000)
    public void publish(MessagePayload message) {
        if (producer == null) {
            logger().info("Kafka publisher is disabled; message was only handled by HTTP endpoint");
            return;
        }

        try {
            var metadata = producer.send(new ProducerRecord<>(configuration.topic(), message.key(), message.toJson()))
                    .get(10, TimeUnit.SECONDS);
            logger().info(() -> "Published Kafka message to %s-%d offset %d payload=%s"
                    .formatted(metadata.topic(), metadata.partition(), metadata.offset(), message.toJson()));
        } catch (Exception exception) {
            logger().log(Level.SEVERE, "Failed to publish Kafka message", exception);
            throw new IllegalStateException("Failed to publish Kafka message", exception);
        }
    }

    void publishDeadLetter(String key, String value, Exception exception) {
        if (producer == null) {
            logger().warning("Kafka publisher is disabled; failed message could not be sent to the DLQ");
            return;
        }

        try {
            var metadata = producer.send(new ProducerRecord<>(configuration.deadLetterTopic(), key, value))
                    .get(10, TimeUnit.SECONDS);
            logger().warning(() -> "Published failed Kafka message to DLQ %s-%d offset %d reason=%s"
                    .formatted(metadata.topic(), metadata.partition(), metadata.offset(), exception.getMessage()));
        } catch (Exception dlqException) {
            logger().log(Level.SEVERE, "Failed to publish Kafka DLQ message", dlqException);
        }
    }

    @PreDestroy
    void stop() {
        if (producer != null) {
            producer.close(Duration.ofSeconds(5));
        }
    }

    private Properties producerProperties() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.bootstrapServers());
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, "jbossqueues-producer");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.RETRIES_CONFIG, 3);
        properties.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, configuration.retryBackoffMillis());
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30_000);
        return properties;
    }
}
