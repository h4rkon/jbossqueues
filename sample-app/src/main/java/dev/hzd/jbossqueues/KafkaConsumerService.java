package dev.hzd.jbossqueues;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;

@Singleton
@Startup
public class KafkaConsumerService {
    private static final Logger LOG = Logger.getLogger(KafkaConsumerService.class.getName());

    @Inject
    KafkaSettings settings;

    @Inject
    ConsumedMessages consumedMessages;

    private final AtomicBoolean running = new AtomicBoolean();
    private ExecutorService executor;
    private KafkaConsumer<String, String> consumer;

    @PostConstruct
    void start() {
        running.set(true);
        executor = Executors.newSingleThreadExecutor();
        executor.submit(this::consume);
    }

    @PreDestroy
    void stop() {
        running.set(false);
        KafkaConsumer<String, String> current = consumer;
        if (current != null) {
            current.wakeup();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void consume() {
        try (KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(settings.consumerProperties())) {
            consumer = kafkaConsumer;
            kafkaConsumer.subscribe(List.of(settings.topic()));
            while (running.get()) {
                for (ConsumerRecord<String, String> record : kafkaConsumer.poll(Duration.ofSeconds(1))) {
                    String message = record.value();
                    consumedMessages.add(message);
                    LOG.info(() -> "Consumed Kafka message from topic " + record.topic()
                            + " partition " + record.partition()
                            + " offset " + record.offset()
                            + ": " + message);
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                LOG.log(Level.SEVERE, "Kafka consumer stopped unexpectedly", e);
            }
        }
    }
}

