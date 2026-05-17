package dev.hzd.jbossqueues.queue.reactive;

import dev.hzd.jbossqueues.MessagePayload;
import dev.hzd.jbossqueues.queue.AbstractMessagePublisher;
import dev.hzd.jbossqueues.queue.MessagePublisher;
import io.smallrye.reactive.messaging.kafka.api.KafkaMetadataUtil;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Level;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class ReactiveMessagePublisher extends AbstractMessagePublisher<MessagePayload>
        implements MessagePublisher<MessagePayload> {

    @Inject
    @Channel("messages-out")
    private Emitter<Message<String>> emitter;

    @Override
    @Retry(maxRetries = 3, delay = 500)
    @CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.5, delay = 10_000)
    public void publish(MessagePayload message) {
        try {
            emitter.send(kafkaMessage(message)).toCompletableFuture().join();
            logger().info(() -> "Published reactive message payload: " + message.toJson());
        } catch (Exception exception) {
            logger().log(Level.SEVERE, "Failed to publish reactive message payload", exception);
            throw new IllegalStateException("Failed to publish reactive message payload", exception);
        }
    }

    private Message<String> kafkaMessage(MessagePayload payload) {
        OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
                .withKey(payload.key())
                .build();
        return KafkaMetadataUtil.writeOutgoingKafkaMetadata(Message.of(payload.toJson()), metadata);
    }
}
