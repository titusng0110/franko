import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class Json {

    private Json() {
    }

    public static JsonValue parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("JSON input is null");
        }

        Parser parser = new Parser(text);
        JsonValue value = parser.parseValue();
        parser.skipWhitespace();

        if (!parser.atEnd()) {
            throw parser.error("trailing content");
        }

        return value;
    }

    public static String write(JsonValue value) {
        return write(value, false);
    }

    public static String write(JsonValue value, boolean pretty) {
        StringBuilder sb = new StringBuilder();
        value.write(sb, pretty, 0);
        return sb.toString();
    }

    public static String canonical(JsonValue value) {
        return write(sortKeys(value), false);
    }

    private static JsonValue sortKeys(JsonValue value) {
        if (value instanceof JsonObject object) {
            JsonObject sorted = new JsonObject();
            Map<String, JsonValue> keys = new TreeMap<>();

            for (Map.Entry<String, JsonValue> entry : object.entries()) {
                keys.put(entry.getKey(), sortKeys(entry.getValue()));
            }

            for (Map.Entry<String, JsonValue> entry : keys.entrySet()) {
                sorted.put(entry.getKey(), entry.getValue());
            }

            return sorted;
        }

        if (value instanceof JsonArray array) {
            JsonArray sorted = new JsonArray();

            for (JsonValue element : array.values()) {
                sorted.add(sortKeys(element));
            }

            return sorted;
        }

        return value;
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        void skipWhitespace() {
            while (!atEnd()) {
                char c = text.charAt(index);

                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    index++;
                } else {
                    break;
                }
            }
        }

        JsonValue parseValue() {
            skipWhitespace();

            if (atEnd()) {
                throw error("expected a JSON value");
            }

            char c = text.charAt(index);

            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> new JsonString(parseString());
                case 't' -> parseKeyword("true", JsonBool.TRUE);
                case 'f' -> parseKeyword("false", JsonBool.FALSE);
                case 'n' -> parseKeyword("null", JsonNull.INSTANCE);
                default -> parseNumber();
            };
        }

        private JsonValue parseKeyword(String keyword, JsonValue value) {
            if (!text.startsWith(keyword, index)) {
                throw error("expected '" + keyword + "'");
            }

            index += keyword.length();
            return value;
        }

        private JsonObject parseObject() {
            expect('{');
            JsonObject object = new JsonObject();
            skipWhitespace();

            if (peek('}')) {
                index++;
                return object;
            }

            while (true) {
                skipWhitespace();

                if (!peek('"')) {
                    throw error("expected an object key string");
                }

                String key = parseString();
                skipWhitespace();
                expect(':');
                JsonValue value = parseValue();

                if (object.has(key)) {
                    throw error("duplicate object key '" + key + "'");
                }

                object.put(key, value);
                skipWhitespace();

                if (peek(',')) {
                    index++;
                    continue;
                }

                expect('}');
                return object;
            }
        }

        private JsonArray parseArray() {
            expect('[');
            JsonArray array = new JsonArray();
            skipWhitespace();

            if (peek(']')) {
                index++;
                return array;
            }

            while (true) {
                array.add(parseValue());
                skipWhitespace();

                if (peek(',')) {
                    index++;
                    continue;
                }

                expect(']');
                return array;
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();

            while (!atEnd()) {
                char c = text.charAt(index++);

                if (c == '"') {
                    return sb.toString();
                }

                if (c == '\\') {
                    if (atEnd()) {
                        throw error("unterminated escape sequence");
                    }

                    char escaped = text.charAt(index++);

                    switch (escaped) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> sb.append(parseUnicodeEscape());
                        default -> throw error("invalid escape sequence '\\" + escaped + "'");
                    }

                    continue;
                }

                if (c < 0x20) {
                    throw error("unescaped control character in string");
                }

                sb.append(c);
            }

            throw error("unterminated string");
        }

        private char parseUnicodeEscape() {
            if (index + 4 > text.length()) {
                throw error("short unicode escape");
            }

            int value = 0;

            for (int i = 0; i < 4; i++) {
                char c = text.charAt(index++);
                int digit = Character.digit(c, 16);

                if (digit < 0) {
                    throw error("invalid unicode escape digit '" + c + "'");
                }

                value = value * 16 + digit;
            }

            return (char) value;
        }

        private JsonNumber parseNumber() {
            int start = index;

            if (peek('-')) {
                index++;
            }

            if (atEnd()) {
                throw error("expected a number");
            }

            if (peek('0')) {
                index++;
            } else if (isDigit(peekChar())) {
                while (isDigit(peekChar())) {
                    index++;
                }
            } else {
                throw error("expected a number");
            }

            if (peek('.')) {
                index++;

                if (!isDigit(peekChar())) {
                    throw error("expected a digit after decimal point");
                }

                while (isDigit(peekChar())) {
                    index++;
                }
            }

            if (peek('e') || peek('E')) {
                index++;

                if (peek('+') || peek('-')) {
                    index++;
                }

                if (!isDigit(peekChar())) {
                    throw error("expected a digit in exponent");
                }

                while (isDigit(peekChar())) {
                    index++;
                }
            }

            return new JsonNumber(text.substring(start, index));
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private char peekChar() {
            return atEnd() ? '\0' : text.charAt(index);
        }

        private boolean peek(char c) {
            return !atEnd() && text.charAt(index) == c;
        }

        private void expect(char c) {
            if (atEnd() || text.charAt(index) != c) {
                throw error("expected '" + c + "'");
            }

            index++;
        }

        IllegalArgumentException error(String message) {
            int line = 1;
            int column = 1;

            for (int i = 0; i < index && i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }

            return new IllegalArgumentException(
                    "Malformed JSON at line " + line + " column " + column + ": " + message
            );
        }
    }
}

abstract class JsonValue {
    abstract void write(StringBuilder sb, boolean pretty, int indent);

    String toJson() {
        return Json.write(this);
    }
}

final class JsonObject extends JsonValue {
    private final LinkedHashMap<String, JsonValue> values = new LinkedHashMap<>();

    JsonObject put(String key, JsonValue value) {
        values.put(key, value == null ? JsonNull.INSTANCE : value);
        return this;
    }

    JsonObject put(String key, String value) {
        return put(key, value == null ? JsonNull.INSTANCE : new JsonString(value));
    }

    JsonObject put(String key, long value) {
        return put(key, new JsonNumber(Long.toString(value)));
    }

    JsonObject put(String key, boolean value) {
        return put(key, value ? JsonBool.TRUE : JsonBool.FALSE);
    }

    JsonObject putNull(String key) {
        return put(key, JsonNull.INSTANCE);
    }

    JsonValue get(String key) {
        return values.get(key);
    }

    boolean has(String key) {
        return values.containsKey(key);
    }

    JsonValue require(String key) {
        JsonValue value = values.get(key);

        if (value == null) {
            throw new IllegalArgumentException("Missing required JSON field '" + key + "'");
        }

        return value;
    }

    JsonObject requireObject(String key) {
        JsonValue value = require(key);

        if (!(value instanceof JsonObject object)) {
            throw new IllegalArgumentException("JSON field '" + key + "' must be an object");
        }

        return object;
    }

    JsonArray requireArray(String key) {
        JsonValue value = require(key);

        if (!(value instanceof JsonArray array)) {
            throw new IllegalArgumentException("JSON field '" + key + "' must be an array");
        }

        return array;
    }

    String requireString(String key) {
        JsonValue value = require(key);

        if (!(value instanceof JsonString string)) {
            throw new IllegalArgumentException("JSON field '" + key + "' must be a string");
        }

        return string.value();
    }

    int requireInt(String key) {
        JsonValue value = require(key);

        if (!(value instanceof JsonNumber number)) {
            throw new IllegalArgumentException("JSON field '" + key + "' must be a number");
        }

        try {
            return new BigDecimal(number.value()).intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("JSON field '" + key + "' must be an integer");
        }
    }

    boolean requireBool(String key) {
        JsonValue value = require(key);

        if (!(value instanceof JsonBool bool)) {
            throw new IllegalArgumentException("JSON field '" + key + "' must be a boolean");
        }

        return bool.value();
    }

    String optString(String key) {
        JsonValue value = values.get(key);
        return value instanceof JsonString string ? string.value() : null;
    }

    JsonObject optObject(String key) {
        JsonValue value = values.get(key);
        return value instanceof JsonObject object ? object : null;
    }

    JsonArray optArray(String key) {
        JsonValue value = values.get(key);
        return value instanceof JsonArray array ? array : null;
    }

    Iterable<Map.Entry<String, JsonValue>> entries() {
        return values.entrySet();
    }

    int size() {
        return values.size();
    }

    @Override
    void write(StringBuilder sb, boolean pretty, int indent) {
        sb.append('{');

        if (!values.isEmpty()) {
            if (pretty) {
                sb.append('\n');
            }

            int i = 0;

            for (Map.Entry<String, JsonValue> entry : values.entrySet()) {
                if (pretty) {
                    JsonWriter.indent(sb, indent + 1);
                }

                JsonWriter.writeString(sb, entry.getKey());
                sb.append(pretty ? ": " : ":");
                entry.getValue().write(sb, pretty, indent + 1);
                i++;

                if (i < values.size()) {
                    sb.append(',');
                }

                if (pretty) {
                    sb.append('\n');
                }
            }

            if (pretty) {
                JsonWriter.indent(sb, indent);
            }
        }

        sb.append('}');
    }
}

final class JsonArray extends JsonValue {
    private final List<JsonValue> values = new ArrayList<>();

    JsonArray add(JsonValue value) {
        values.add(value == null ? JsonNull.INSTANCE : value);
        return this;
    }

    JsonArray add(String value) {
        return add(new JsonString(value));
    }

    JsonValue get(int index) {
        return values.get(index);
    }

    List<JsonValue> values() {
        return List.copyOf(values);
    }

    int size() {
        return values.size();
    }

    @Override
    void write(StringBuilder sb, boolean pretty, int indent) {
        sb.append('[');

        if (!values.isEmpty()) {
            if (pretty) {
                sb.append('\n');
            }

            for (int i = 0; i < values.size(); i++) {
                if (pretty) {
                    JsonWriter.indent(sb, indent + 1);
                }

                values.get(i).write(sb, pretty, indent + 1);

                if (i + 1 < values.size()) {
                    sb.append(',');
                }

                if (pretty) {
                    sb.append('\n');
                }
            }

            if (pretty) {
                JsonWriter.indent(sb, indent);
            }
        }

        sb.append(']');
    }
}

final class JsonString extends JsonValue {
    private final String value;

    JsonString(String value) {
        this.value = value == null ? "" : value;
    }

    String value() {
        return value;
    }

    @Override
    void write(StringBuilder sb, boolean pretty, int indent) {
        JsonWriter.writeString(sb, value);
    }
}

final class JsonNumber extends JsonValue {
    private final String value;

    JsonNumber(String value) {
        this.value = value;
    }

    String value() {
        return value;
    }

    @Override
    void write(StringBuilder sb, boolean pretty, int indent) {
        sb.append(value);
    }
}

final class JsonBool extends JsonValue {
    static final JsonBool TRUE = new JsonBool(true);
    static final JsonBool FALSE = new JsonBool(false);

    private final boolean value;

    private JsonBool(boolean value) {
        this.value = value;
    }

    boolean value() {
        return value;
    }

    @Override
    void write(StringBuilder sb, boolean pretty, int indent) {
        sb.append(value ? "true" : "false");
    }
}

final class JsonNull extends JsonValue {
    static final JsonNull INSTANCE = new JsonNull();

    private JsonNull() {
    }

    @Override
    void write(StringBuilder sb, boolean pretty, int indent) {
        sb.append("null");
    }
}

final class JsonWriter {
    private JsonWriter() {
    }

    static void indent(StringBuilder sb, int level) {
        sb.append("  ".repeat(Math.max(0, level)));
    }

    static void writeString(StringBuilder sb, String value) {
        sb.append('"');

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }

        sb.append('"');
    }
}
