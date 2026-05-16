package dev.hzd.jbossqueues;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

@ApplicationScoped
public class KafkaProducerService {
    @Inject
    KafkaSettings settings;

    private volatile KafkaProducer<String, String> producer;

    public void send(String message) {
        producer().send(new ProducerRecord<>(settings.topic(), "wildfly", message));
    }

    @PreDestroy
    void close() {
        KafkaProducer<String, String> current = producer;
        if (current != null) {
            current.close();
        }
    }

    private KafkaProducer<String, String> producer() {
        KafkaProducer<String, String> current = producer;
        if (current == null) {
            synchronized (this) {
                current = producer;
                if (current == null) {
                    current = new KafkaProducer<>(settings.producerProperties());
                    producer = current;
                }
            }
        }
        return current;
    }
}

