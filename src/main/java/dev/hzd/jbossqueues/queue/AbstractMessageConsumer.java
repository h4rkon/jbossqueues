package dev.hzd.jbossqueues.queue;

import java.util.logging.Logger;

public abstract class AbstractMessageConsumer<T> implements MessageConsumer<T> {

    protected Logger logger() {
        return Logger.getLogger(getClass().getName());
    }
}
