package com.rustybank.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer, so the agent needs no libraries at all.
 * Objects become {@link Map}, arrays {@link List}, numbers {@link Long} or {@link Double}.
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.value();
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw parser.error("unexpected trailing content");
        }
        return value;
    }

    /** Walks into nested objects (String keys) and arrays (Integer indexes); null if absent. */
    public static Object at(Object root, Object... path) {
        Object current = root;
        for (Object step : path) {
            if (step instanceof String key && current instanceof Map<?, ?> map) {
                current = map.get(key);
            } else if (step instanceof Integer index && current instanceof List<?> list) {
                current = index < list.size() ? list.get(index) : null;
            } else {
                return null;
            }
        }
        return current;
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            quote(s, out);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                indent(out, depth + 1);
                quote(String.valueOf(entry.getKey()), out);
                out.append(": ");
                write(entry.getValue(), out, depth + 1);
                out.append(++i < map.size() ? ",\n" : "\n");
            }
            indent(out, depth);
            out.append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                out.append("[]");
                return;
            }
            out.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                indent(out, depth + 1);
                write(list.get(i), out, depth + 1);
                out.append(i + 1 < list.size() ? ",\n" : "\n");
            }
            indent(out, depth);
            out.append(']');
        } else {
            quote(value.toString(), out);
        }
    }

    private static void indent(StringBuilder out, int depth) {
        out.append("  ".repeat(depth));
    }

    private static void quote(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid JSON at position " + pos + ": " + message);
        }

        void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        Object value() {
            if (pos >= text.length()) {
                throw error("unexpected end");
            }
            char c = text.charAt(pos);
            if (c == '{') {
                return object();
            }
            if (c == '[') {
                return array();
            }
            if (c == '"') {
                return string();
            }
            if (text.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            if (text.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            return number();
        }

        Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("expected a key");
                }
                String key = string();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, value());
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                } else {
                    expect('}');
                    return map;
                }
            }
        }

        List<Object> array() {
            List<Object> list = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(value());
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                } else {
                    expect(']');
                    return list;
                }
            }
        }

        String string() {
            StringBuilder out = new StringBuilder();
            pos++;
            while (true) {
                if (pos >= text.length()) {
                    throw error("unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (pos >= text.length()) {
                    throw error("unterminated escape");
                }
                char e = text.charAt(pos++);
                switch (e) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw error("bad unicode escape");
                        }
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> out.append(e);
                }
            }
        }

        Object number() {
            int start = pos;
            while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            String token = text.substring(start, pos);
            if (token.isEmpty()) {
                throw error("unexpected character '" + text.charAt(pos) + "'");
            }
            try {
                if (token.contains(".") || token.contains("e") || token.contains("E")) {
                    return Double.parseDouble(token);
                }
                return Long.parseLong(token);
            } catch (NumberFormatException e) {
                throw error("bad number " + token);
            }
        }

        char peek() {
            if (pos >= text.length()) {
                throw error("unexpected end");
            }
            return text.charAt(pos);
        }

        void expect(char c) {
            if (peek() != c) {
                throw error("expected '" + c + "'");
            }
            pos++;
        }
    }
}
