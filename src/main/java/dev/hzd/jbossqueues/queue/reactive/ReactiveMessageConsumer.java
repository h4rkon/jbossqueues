package dev.hzd.jbossqueues.queue.reactive;

import dev.hzd.jbossqueues.MessagePayload;
import dev.hzd.jbossqueues.queue.AbstractMessageConsumer;
import dev.hzd.jbossqueues.queue.MessageConsumer;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class ReactiveMessageConsumer extends AbstractMessageConsumer<MessagePayload>
        implements MessageConsumer<MessagePayload> {

    @Incoming("messages-in")
    public void consume(String json) {
        onMessage(MessagePayload.fromJson(json));
    }

    @Override
    public void onMessage(MessagePayload message) {
        logger().info(() -> "Consumed reactive message payload: " + message.toJson());
    }
}
