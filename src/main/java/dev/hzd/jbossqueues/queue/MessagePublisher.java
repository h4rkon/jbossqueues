package dev.hzd.jbossqueues.queue;

public interface MessagePublisher<T> {

    void publish(T message);
}
