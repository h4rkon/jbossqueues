package dev.hzd.jbossqueues.queue.kafka;

import dev.hzd.jbossqueues.MessagePayload;
import dev.hzd.jbossqueues.queue.AbstractMessagePublisher;
import dev.hzd.jbossqueues.queue.CircuitBreaker;
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

@ApplicationScoped
public class KafkaMessagePublisher extends AbstractMessagePublisher<MessagePayload>
        implements MessagePublisher<MessagePayload> {

    @Inject
    private QueueConfiguration configuration;

    private KafkaProducer<String, String> producer;
    private CircuitBreaker circuitBreaker;

    @PostConstruct
    void start() {
        if (!configuration.enabled()) {
            logger().info("Kafka publisher disabled. Configure kafka.bootstrap.servers and kafka.topic.messages to enable it.");
            return;
        }

        producer = new KafkaProducer<>(producerProperties());
        circuitBreaker = new CircuitBreaker(
                "kafka-producer",
                configuration.circuitBreakerFailureThreshold(),
                configuration.circuitBreakerOpenMillis());
        logger().info(() -> "Kafka publisher enabled. bootstrapServers=%s topic=%s dlqTopic=%s"
                .formatted(configuration.bootstrapServers(), configuration.topic(), configuration.deadLetterTopic()));
    }

    @Override
    public void publish(MessagePayload message) {
        if (producer == null) {
            logger().info("Kafka publisher is disabled; message was only handled by HTTP endpoint");
            return;
        }

        if (!circuitBreaker.allowRequest()) {
            logger().warning("Kafka publisher circuit is open; message was not sent to Kafka");
            return;
        }

        try {
            var metadata = producer.send(new ProducerRecord<>(configuration.topic(), message.key(), message.toJson()))
                    .get(10, TimeUnit.SECONDS);
            circuitBreaker.recordSuccess();
            logger().info(() -> "Published Kafka message to %s-%d offset %d"
                    .formatted(metadata.topic(), metadata.partition(), metadata.offset()));
        } catch (Exception exception) {
            circuitBreaker.recordFailure();
            logger().log(Level.SEVERE, "Failed to publish message to Kafka topic " + configuration.topic(), exception);
        }
    }

    void publishDeadLetter(String key, String value, Exception exception) {
        if (producer == null) {
            logger().warning("Kafka publisher is disabled; failed message could not be sent to the DLQ");
            return;
        }

        String reason = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        ProducerRecord<String, String> deadLetter = new ProducerRecord<>(configuration.deadLetterTopic(), key, value);
        deadLetter.headers().add("x-error-reason", reason.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            var metadata = producer.send(deadLetter).get(10, TimeUnit.SECONDS);
            logger().warning(() -> "Published failed Kafka message to DLQ %s-%d offset %d reason=%s"
                    .formatted(metadata.topic(), metadata.partition(), metadata.offset(), reason));
        } catch (Exception dlqException) {
            logger().log(Level.SEVERE, "Failed to publish message to Kafka DLQ " + configuration.deadLetterTopic(), dlqException);
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
