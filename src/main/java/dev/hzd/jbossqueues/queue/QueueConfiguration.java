package dev.hzd.jbossqueues.queue;

public interface QueueConfiguration {

    boolean enabled();

    String bootstrapServers();

    String topic();

    String consumerGroup();
}
