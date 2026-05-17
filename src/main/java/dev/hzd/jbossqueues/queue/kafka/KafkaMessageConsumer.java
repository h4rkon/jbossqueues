package dev.hzd.jbossqueues.queue.kafka;

import dev.hzd.jbossqueues.MessagePayload;
import dev.hzd.jbossqueues.queue.AbstractMessageConsumer;
import dev.hzd.jbossqueues.queue.MessageConsumer;
import dev.hzd.jbossqueues.queue.QueueConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import jakarta.enterprise.concurrent.ManagedExecutorService;

@Singleton
@Startup
public class KafkaMessageConsumer extends AbstractMessageConsumer<MessagePayload>
        implements MessageConsumer<MessagePayload> {

    @Inject
    private QueueConfiguration configuration;

    @Resource
    private ManagedExecutorService executorService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private KafkaConsumer<String, String> consumer;
    private Future<?> consumerTask;

    @PostConstruct
    void start() {
        if (!configuration.enabled()) {
            logger().info("Kafka consumer disabled. Configure kafka.bootstrap.servers and kafka.topic.messages to enable it.");
            return;
        }

        consumer = new KafkaConsumer<>(consumerProperties());
        running.set(true);
        consumerTask = executorService.submit(this::consume);
        logger().info(() -> "Kafka consumer enabled. bootstrapServers=%s topic=%s group=%s"
                .formatted(configuration.bootstrapServers(), configuration.topic(), configuration.consumerGroup()));
    }

    @Override
    public void onMessage(MessagePayload message) {
        logger().info(() -> "Consumed Kafka message payload: " + message.toJson());
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (consumer != null) {
            consumer.wakeup();
        }
        if (consumerTask != null) {
            consumerTask.cancel(true);
        }
    }

    private void consume() {
        try {
            consumer.subscribe(List.of(configuration.topic()));
            while (running.get()) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(1))) {
                    logger().info(() -> "Consumed Kafka message from %s-%d offset %d key=%s value=%s"
                            .formatted(record.topic(), record.partition(), record.offset(), record.key(), record.value()));
                    onMessage(MessagePayload.fromJson(record.value()));
                }
            }
        } catch (WakeupException exception) {
            if (running.get()) {
                logger().log(Level.SEVERE, "Kafka consumer was interrupted unexpectedly", exception);
            }
        } catch (RuntimeException exception) {
            logger().log(Level.SEVERE, "Kafka consumer stopped after an error", exception);
        } finally {
            if (consumer != null) {
                consumer.close(Duration.ofSeconds(5));
            }
        }
    }

    private Properties consumerProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.bootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, configuration.consumerGroup());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        return properties;
    }
}
