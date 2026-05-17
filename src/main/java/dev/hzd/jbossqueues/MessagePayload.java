package dev.hzd.jbossqueues;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

public record MessagePayload(String key, String value) {
    private static final Jsonb JSONB = JsonbBuilder.create();

    public String toJson() {
        return JSONB.toJson(this);
    }
}
