package dev.hzd.jbossqueues.queue;

public interface MessageConsumer<T> {

    void onMessage(T message);
}
