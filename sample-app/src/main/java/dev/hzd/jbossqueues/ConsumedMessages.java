package dev.hzd.jbossqueues;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConsumedMessages {
    private static final int MAX_MESSAGES = 20;

    private final Deque<String> messages = new ConcurrentLinkedDeque<>();

    public void add(String message) {
        messages.addFirst(message);
        while (messages.size() > MAX_MESSAGES) {
            messages.removeLast();
        }
    }

    public List<String> latest() {
        return new ArrayList<>(messages);
    }
}

