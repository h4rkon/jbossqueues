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

    @PostConstruct
    void start() {
        if (!configuration.enabled()) {
            logger().info("Kafka publisher disabled. Configure kafka.bootstrap.servers and kafka.topic.messages to enable it.");
            return;
        }

        producer = new KafkaProducer<>(producerProperties());
        logger().info(() -> "Kafka publisher enabled. bootstrapServers=%s topic=%s"
                .formatted(configuration.bootstrapServers(), configuration.topic()));
    }

    @Override
    public void publish(MessagePayload message) {
        if (producer == null) {
            logger().info("Kafka publisher is disabled; message was only handled by HTTP endpoint");
            return;
        }

        producer.send(new ProducerRecord<>(configuration.topic(), message.key(), message.toJson()), (metadata, exception) -> {
            if (exception != null) {
                logger().log(Level.SEVERE, "Failed to publish message to Kafka topic " + configuration.topic(), exception);
                return;
            }
            logger().info(() -> "Published Kafka message to %s-%d offset %d"
                    .formatted(metadata.topic(), metadata.partition(), metadata.offset()));
        });
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
        return properties;
    }
}
