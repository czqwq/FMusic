package com.Lilith.FMusic.server.bili.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON parser/writer supporting the subset required by Bilibili APIs. */
public final class Json {

    private Json() {}

    public static JsonValue parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("JSON text is null");
        }
        Parser parser = new Parser(text);
        JsonValue value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.end()) {
            throw parser.error("Trailing data");
        }
        return value;
    }

    public static String stringify(JsonValue value) {
        StringBuilder out = new StringBuilder();
        write(value == null ? JsonValue.nullValue() : value, out);
        return out.toString();
    }

    public static String stringifyObject(Map<String, ?> values) {
        StringBuilder out = new StringBuilder();
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(entry.getKey(), out);
            out.append(':');
            writeJavaValue(entry.getValue(), out);
        }
        out.append('}');
        return out.toString();
    }

    private static void writeJavaValue(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(String.valueOf(value));
        } else {
            writeString(String.valueOf(value), out);
        }
    }

    private static void write(JsonValue value, StringBuilder out) {
        switch (value.type()) {
            case NULL:
                out.append("null");
                break;
            case BOOLEAN:
            case NUMBER:
                out.append(String.valueOf(value.raw()));
                break;
            case STRING:
                writeString(String.valueOf(value.raw()), out);
                break;
            case ARRAY:
                out.append('[');
                for (int i = 0; i < value.asArray()
                    .size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    write(
                        value.asArray()
                            .get(i),
                        out);
                }
                out.append(']');
                break;
            case OBJECT:
                out.append('{');
                boolean first = true;
                for (Map.Entry<String, JsonValue> entry : value.asObject()
                    .entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    writeString(entry.getKey(), out);
                    out.append(':');
                    write(entry.getValue(), out);
                }
                out.append('}');
                break;
            default:
                throw new IllegalStateException("Unknown JSON type: " + value.type());
        }
    }

    private static void writeString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        String hex = Integer.toHexString(c);
                        out.append("\\u");
                        for (int pad = hex.length(); pad < 4; pad++) {
                            out.append('0');
                        }
                        out.append(hex);
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    private static final class Parser {

        private final String input;
        private int index;

        private Parser(String input) {
            this.input = input;
        }

        private JsonValue parseValue() {
            skipWhitespace();
            if (end()) {
                throw error("Unexpected end of JSON");
            }
            char c = input.charAt(index);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return new JsonValue(JsonValue.Type.STRING, parseString());
            if (c == 't') return literal("true", new JsonValue(JsonValue.Type.BOOLEAN, Boolean.TRUE));
            if (c == 'f') return literal("false", new JsonValue(JsonValue.Type.BOOLEAN, Boolean.FALSE));
            if (c == 'n') return literal("null", JsonValue.nullValue());
            if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
            throw error("Unexpected character '" + c + "'");
        }

        private JsonValue parseObject() {
            index++;
            skipWhitespace();
            Map<String, JsonValue> object = new LinkedHashMap<String, JsonValue>();
            if (consume('}')) {
                return new JsonValue(JsonValue.Type.OBJECT, object);
            }
            while (true) {
                skipWhitespace();
                if (end() || input.charAt(index) != '"') {
                    throw error("Object key must be a string");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
                if (consume('}')) {
                    return new JsonValue(JsonValue.Type.OBJECT, object);
                }
                expect(',');
            }
        }

        private JsonValue parseArray() {
            index++;
            skipWhitespace();
            List<JsonValue> array = new ArrayList<JsonValue>();
            if (consume(']')) {
                return new JsonValue(JsonValue.Type.ARRAY, array);
            }
            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    return new JsonValue(JsonValue.Type.ARRAY, array);
                }
                expect(',');
            }
        }

        private JsonValue parseNumber() {
            int start = index;
            if (input.charAt(index) == '-') index++;
            if (end()) throw error("Invalid number");
            if (input.charAt(index) == '0') {
                index++;
            } else {
                requireDigit();
                while (!end() && Character.isDigit(input.charAt(index))) index++;
            }
            if (!end() && input.charAt(index) == '.') {
                index++;
                requireDigit();
                while (!end() && Character.isDigit(input.charAt(index))) index++;
            }
            if (!end() && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
                index++;
                if (!end() && (input.charAt(index) == '+' || input.charAt(index) == '-')) index++;
                requireDigit();
                while (!end() && Character.isDigit(input.charAt(index))) index++;
            }
            return new JsonValue(JsonValue.Type.NUMBER, input.substring(start, index));
        }

        private void requireDigit() {
            if (end() || !Character.isDigit(input.charAt(index))) {
                throw error("Digit expected");
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (!end()) {
                char c = input.charAt(index++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    if (c < 0x20) throw error("Control character in string");
                    out.append(c);
                    continue;
                }
                if (end()) throw error("Unterminated escape sequence");
                char escaped = input.charAt(index++);
                switch (escaped) {
                    case '"':
                        out.append('"');
                        break;
                    case '\\':
                        out.append('\\');
                        break;
                    case '/':
                        out.append('/');
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'u':
                        out.append(parseUnicode());
                        break;
                    default:
                        throw error("Invalid escape sequence");
                }
            }
            throw error("Unterminated string");
        }

        private char parseUnicode() {
            if (index + 4 > input.length()) throw error("Invalid unicode escape");
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(input.charAt(index++), 16);
                if (digit < 0) throw error("Invalid unicode escape");
                value = (value << 4) | digit;
            }
            return (char) value;
        }

        private JsonValue literal(String literal, JsonValue value) {
            if (!input.regionMatches(index, literal, 0, literal.length())) {
                throw error("Invalid literal");
            }
            index += literal.length();
            return value;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (end() || input.charAt(index) != expected) {
                throw error("Expected '" + expected + "'");
            }
            index++;
        }

        private boolean consume(char expected) {
            if (!end() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (!end()) {
                char c = input.charAt(index);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    index++;
                } else {
                    break;
                }
            }
        }

        private boolean end() {
            return index >= input.length();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at index " + index);
        }
    }
}
