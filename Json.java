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
        if (value.isObject()) {
            JsonValue sorted = JsonValue.object();
            Map<String, JsonValue> keys = new TreeMap<>();

            for (Map.Entry<String, JsonValue> entry : value.entries()) {
                keys.put(entry.getKey(), sortKeys(entry.getValue()));
            }

            for (Map.Entry<String, JsonValue> entry : keys.entrySet()) {
                sorted.put(entry.getKey(), entry.getValue());
            }

            return sorted;
        }

        if (value.isArray()) {
            JsonValue sorted = JsonValue.array();

            for (JsonValue element : value.values()) {
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

            if (c == '{') {
                return parseObject();
            }

            if (c == '[') {
                return parseArray();
            }

            if (c == '"') {
                return JsonValue.string(parseString());
            }

            if (c == 't') {
                return parseKeyword("true", JsonValue.bool(true));
            }

            if (c == 'f') {
                return parseKeyword("false", JsonValue.bool(false));
            }

            if (c == 'n') {
                return parseKeyword("null", JsonValue.nullValue());
            }

            return parseNumber();
        }

        private JsonValue parseKeyword(String keyword, JsonValue value) {
            if (!text.startsWith(keyword, index)) {
                throw error("expected '" + keyword + "'");
            }

            index += keyword.length();
            return value;
        }

        private JsonValue parseObject() {
            expect('{');
            JsonValue object = JsonValue.object();
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

        private JsonValue parseArray() {
            expect('[');
            JsonValue array = JsonValue.array();
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

        private JsonValue parseNumber() {
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

            return JsonValue.number(text.substring(start, index));
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

enum JsonKind {
    OBJECT,
    ARRAY,
    STRING,
    NUMBER,
    BOOL,
    NULL
}

final class JsonValue {
    final JsonKind kind;

    private final LinkedHashMap<String, JsonValue> objectValues;
    private final List<JsonValue> arrayValues;
    private final String text;
    private final boolean boolValue;

    private JsonValue(
            JsonKind kind,
            LinkedHashMap<String, JsonValue> objectValues,
            List<JsonValue> arrayValues,
            String text,
            boolean boolValue
    ) {
        this.kind = kind;
        this.objectValues = objectValues;
        this.arrayValues = arrayValues;
        this.text = text;
        this.boolValue = boolValue;
    }

    static JsonValue object() {
        return new JsonValue(
                JsonKind.OBJECT,
                new LinkedHashMap<>(),
                null,
                null,
                false
        );
    }

    static JsonValue array() {
        return new JsonValue(
                JsonKind.ARRAY,
                null,
                new ArrayList<>(),
                null,
                false
        );
    }

    static JsonValue string(String value) {
        return new JsonValue(
                JsonKind.STRING,
                null,
                null,
                value == null ? "" : value,
                false
        );
    }

    static JsonValue number(String value) {
        return new JsonValue(JsonKind.NUMBER, null, null, value, false);
    }

    static JsonValue bool(boolean value) {
        return new JsonValue(JsonKind.BOOL, null, null, null, value);
    }

    static JsonValue nullValue() {
        return new JsonValue(JsonKind.NULL, null, null, null, false);
    }

    boolean isObject() {
        return kind == JsonKind.OBJECT;
    }

    boolean isArray() {
        return kind == JsonKind.ARRAY;
    }

    boolean isString() {
        return kind == JsonKind.STRING;
    }

    boolean isNumber() {
        return kind == JsonKind.NUMBER;
    }

    boolean isBool() {
        return kind == JsonKind.BOOL;
    }

    boolean isNull() {
        return kind == JsonKind.NULL;
    }

    JsonValue put(String key, JsonValue value) {
        objectValues.put(key, value == null ? nullValue() : value);
        return this;
    }

    JsonValue put(String key, String value) {
        return put(key, value == null ? nullValue() : string(value));
    }

    JsonValue put(String key, long value) {
        return put(key, number(Long.toString(value)));
    }

    JsonValue put(String key, boolean value) {
        return put(key, bool(value));
    }

    JsonValue putNull(String key) {
        return put(key, nullValue());
    }

    JsonValue get(String key) {
        return objectValues.get(key);
    }

    boolean has(String key) {
        return objectValues.containsKey(key);
    }

    JsonValue require(String key) {
        JsonValue value = objectValues.get(key);

        if (value == null) {
            throw new IllegalArgumentException("Missing required JSON field '" + key + "'");
        }

        return value;
    }

    JsonValue requireObject(String key) {
        JsonValue value = require(key);

        if (!value.isObject()) {
            throw new IllegalArgumentException("JSON field '" + key + "' must be an object");
        }

        return value;
    }

    JsonValue requireArray(String key) {
        JsonValue value = require(key);

        if (!value.isArray()) {
            throw new IllegalArgumentException("JSON field '" + key + "' must be an array");
        }

        return value;
    }

    String requireString(String key) {
        JsonValue value = require(key);

        if (!value.isString()) {
            throw new IllegalArgumentException("JSON field '" + key + "' must be a string");
        }

        return value.value();
    }

    int requireInt(String key) {
        JsonValue value = require(key);

        if (!value.isNumber()) {
            throw new IllegalArgumentException("JSON field '" + key + "' must be a number");
        }

        try {
            return new BigDecimal(value.value()).intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("JSON field '" + key + "' must be an integer");
        }
    }

    boolean requireBool(String key) {
        JsonValue value = require(key);

        if (!value.isBool()) {
            throw new IllegalArgumentException("JSON field '" + key + "' must be a boolean");
        }

        return value.boolValue;
    }

    String optString(String key) {
        JsonValue value = objectValues.get(key);
        return value != null && value.isString() ? value.value() : null;
    }

    JsonValue optObject(String key) {
        JsonValue value = objectValues.get(key);
        return value != null && value.isObject() ? value : null;
    }

    JsonValue optArray(String key) {
        JsonValue value = objectValues.get(key);
        return value != null && value.isArray() ? value : null;
    }

    Iterable<Map.Entry<String, JsonValue>> entries() {
        return objectValues.entrySet();
    }

    int size() {
        if (kind == JsonKind.OBJECT) {
            return objectValues.size();
        }

        return arrayValues.size();
    }

    JsonValue add(JsonValue value) {
        arrayValues.add(value == null ? nullValue() : value);
        return this;
    }

    JsonValue add(String value) {
        return add(string(value));
    }

    JsonValue get(int index) {
        return arrayValues.get(index);
    }

    List<JsonValue> values() {
        return List.copyOf(arrayValues);
    }

    String value() {
        return text;
    }

    boolean boolValue() {
        return boolValue;
    }

    String toJson() {
        return Json.write(this);
    }

    void write(StringBuilder sb, boolean pretty, int indent) {
        if (kind == JsonKind.OBJECT) {
            writeObject(sb, pretty, indent);
            return;
        }

        if (kind == JsonKind.ARRAY) {
            writeArray(sb, pretty, indent);
            return;
        }

        if (kind == JsonKind.STRING) {
            JsonWriter.writeString(sb, text);
            return;
        }

        if (kind == JsonKind.NUMBER) {
            sb.append(text);
            return;
        }

        if (kind == JsonKind.BOOL) {
            sb.append(boolValue ? "true" : "false");
            return;
        }

        sb.append("null");
    }

    private void writeObject(StringBuilder sb, boolean pretty, int indent) {
        sb.append('{');

        if (!objectValues.isEmpty()) {
            if (pretty) {
                sb.append('\n');
            }

            int i = 0;

            for (Map.Entry<String, JsonValue> entry : objectValues.entrySet()) {
                if (pretty) {
                    JsonWriter.indent(sb, indent + 1);
                }

                JsonWriter.writeString(sb, entry.getKey());
                sb.append(pretty ? ": " : ":");
                entry.getValue().write(sb, pretty, indent + 1);
                i++;

                if (i < objectValues.size()) {
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

    private void writeArray(StringBuilder sb, boolean pretty, int indent) {
        sb.append('[');

        if (!arrayValues.isEmpty()) {
            if (pretty) {
                sb.append('\n');
            }

            for (int i = 0; i < arrayValues.size(); i++) {
                if (pretty) {
                    JsonWriter.indent(sb, indent + 1);
                }

                arrayValues.get(i).write(sb, pretty, indent + 1);

                if (i + 1 < arrayValues.size()) {
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
