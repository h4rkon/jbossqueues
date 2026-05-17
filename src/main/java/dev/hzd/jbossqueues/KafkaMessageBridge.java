package dev.hzd.jbossqueues;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

@WebListener
public class KafkaMessageBridge implements ServletContextListener {
    private static final Logger LOGGER = Logger.getLogger(KafkaMessageBridge.class.getName());
    private static final String BOOTSTRAP_ENV = "KAFKA_BOOTSTRAP_SERVERS";
    private static final String TOPIC_ENV = "KAFKA_TOPIC_MESSAGES";
    private static final String GROUP_ENV = "KAFKA_CONSUMER_GROUP";
    private static volatile KafkaMessageBridge instance;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService consumerExecutor;
    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;
    private String topic;

    public static void publish(MessagePayload payload) {
        KafkaMessageBridge bridge = instance;
        if (bridge == null || bridge.producer == null) {
            LOGGER.info("Kafka producer is disabled; message was only handled by HTTP endpoint");
            return;
        }

        bridge.producer.send(new ProducerRecord<>(bridge.topic, payload.key(), payload.toJson()), (metadata, exception) -> {
            if (exception != null) {
                LOGGER.log(Level.SEVERE, "Failed to publish message to Kafka topic " + bridge.topic, exception);
                return;
            }
            LOGGER.info(() -> "Published Kafka message to %s-%d offset %d"
                    .formatted(metadata.topic(), metadata.partition(), metadata.offset()));
        });
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        instance = this;

        String bootstrapServers = getenv(BOOTSTRAP_ENV);
        topic = getenv(TOPIC_ENV);
        if (bootstrapServers == null || topic == null) {
            LOGGER.info("Kafka bridge disabled. Set " + BOOTSTRAP_ENV + " and " + TOPIC_ENV + " to enable it.");
            return;
        }

        producer = new KafkaProducer<>(producerProperties(bootstrapServers));
        consumer = new KafkaConsumer<>(consumerProperties(bootstrapServers));
        running.set(true);
        consumerExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "jbossqueues-kafka-consumer");
            thread.setDaemon(true);
            return thread;
        });
        consumerExecutor.submit(this::consume);
        LOGGER.info(() -> "Kafka bridge enabled. bootstrapServers=%s topic=%s"
                .formatted(bootstrapServers, topic));
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        running.set(false);
        if (consumer != null) {
            consumer.wakeup();
        }
        if (consumerExecutor != null) {
            consumerExecutor.shutdownNow();
        }
        if (producer != null) {
            producer.close(Duration.ofSeconds(5));
        }
        instance = null;
    }

    private void consume() {
        try {
            consumer.subscribe(List.of(topic));
            while (running.get()) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(1))) {
                    LOGGER.info(() -> "Consumed Kafka message from %s-%d offset %d key=%s value=%s"
                            .formatted(record.topic(), record.partition(), record.offset(), record.key(), record.value()));
                }
            }
        } catch (WakeupException exception) {
            if (running.get()) {
                LOGGER.log(Level.SEVERE, "Kafka consumer was interrupted unexpectedly", exception);
            }
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Kafka consumer stopped after an error", exception);
        } finally {
            if (consumer != null) {
                consumer.close(Duration.ofSeconds(5));
            }
        }
    }

    private static Properties producerProperties(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, "jbossqueues-producer");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        return properties;
    }

    private static Properties consumerProperties(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, getenvOrDefault(GROUP_ENV, "jbossqueues-demo"));
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        return properties;
    }

    private static String getenv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String getenvOrDefault(String name, String defaultValue) {
        String value = getenv(name);
        return value == null ? defaultValue : value;
    }
}
