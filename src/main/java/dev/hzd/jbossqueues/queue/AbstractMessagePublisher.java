package dev.hzd.jbossqueues.queue;

import java.util.logging.Logger;

public abstract class AbstractMessagePublisher<T> implements MessagePublisher<T> {

    protected Logger logger() {
        return Logger.getLogger(getClass().getName());
    }
}
