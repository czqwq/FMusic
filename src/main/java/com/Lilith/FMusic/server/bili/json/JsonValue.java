package com.Lilith.FMusic.server.bili.json;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Lightweight immutable JSON value used to avoid runtime dependency conflicts. */
public final class JsonValue {

    public enum Type {
        OBJECT,
        ARRAY,
        STRING,
        NUMBER,
        BOOLEAN,
        NULL
    }

    private static final JsonValue NULL = new JsonValue(Type.NULL, null);
    private final Type type;
    private final Object value;

    JsonValue(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    public static JsonValue nullValue() {
        return NULL;
    }

    public Type type() {
        return type;
    }

    @SuppressWarnings("unchecked")
    public Map<String, JsonValue> asObject() {
        return type == Type.OBJECT ? (Map<String, JsonValue>) value : Collections.<String, JsonValue>emptyMap();
    }

    @SuppressWarnings("unchecked")
    public List<JsonValue> asArray() {
        return type == Type.ARRAY ? (List<JsonValue>) value : Collections.<JsonValue>emptyList();
    }

    public JsonValue get(String key) {
        if (key == null || type != Type.OBJECT) {
            return NULL;
        }
        JsonValue found = asObject().get(key);
        return found == null ? NULL : found;
    }

    public JsonValue get(int index) {
        if (type != Type.ARRAY || index < 0 || index >= asArray().size()) {
            return NULL;
        }
        JsonValue found = asArray().get(index);
        return found == null ? NULL : found;
    }

    public boolean has(String key) {
        return type == Type.OBJECT && key != null && asObject().containsKey(key);
    }

    public int size() {
        if (type == Type.ARRAY) {
            return asArray().size();
        }
        if (type == Type.OBJECT) {
            return asObject().size();
        }
        return 0;
    }

    public String asString(String fallback) {
        if (type == Type.STRING || type == Type.NUMBER || type == Type.BOOLEAN) {
            return String.valueOf(value);
        }
        return fallback;
    }

    public long asLong(long fallback) {
        if (type == Type.NUMBER) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                try {
                    return (long) Double.parseDouble(String.valueOf(value));
                } catch (NumberFormatException ignoredAgain) {
                    return fallback;
                }
            }
        }
        if (type == Type.STRING) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public int asInt(int fallback) {
        long result = asLong(fallback);
        return result > Integer.MAX_VALUE || result < Integer.MIN_VALUE ? fallback : (int) result;
    }

    public boolean asBoolean(boolean fallback) {
        if (type == Type.BOOLEAN) {
            return Boolean.TRUE.equals(value);
        }
        if (type == Type.STRING) {
            if ("true".equalsIgnoreCase((String) value)) {
                return true;
            }
            if ("false".equalsIgnoreCase((String) value)) {
                return false;
            }
        }
        return fallback;
    }

    public boolean isNull() {
        return type == Type.NULL;
    }

    Object raw() {
        return value;
    }

    @Override
    public String toString() {
        return Json.stringify(this);
    }
}
