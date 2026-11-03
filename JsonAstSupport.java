import java.math.BigInteger;
import java.util.List;

final class JsonAstSupport {

    static JsonObject requireDocument(
            String json,
            String kind,
            String format,
            int version
    ) {
        JsonValue value = Json.parse(json);

        if (!(value instanceof JsonObject document)) {
            throw new IllegalArgumentException(
                    kind + " document must be a JSON object"
            );
        }

        String actualFormat = document.requireString("format");

        if (!format.equals(actualFormat)) {
            throw new IllegalArgumentException(
                    "Unsupported document format '"
                            + actualFormat
                            + "', expected '"
                            + format
                            + "'"
            );
        }

        int actualVersion = document.requireInt("version");

        if (actualVersion != version) {
            throw new IllegalArgumentException(
                    "Unsupported " + kind + " document version " + actualVersion
            );
        }

        return document;
    }

    static JsonArray sourceArray(SourceTable sources) {
        JsonArray array = new JsonArray();

        for (String source : sources.names()) {
            array.add(source);
        }

        return array;
    }

    static SourceTable sourceTable(JsonArray array, String kind) {
        SourceTable table = new SourceTable();

        for (JsonValue value : array.values()) {
            if (!(value instanceof JsonString string)) {
                throw new IllegalArgumentException(
                        kind + " source entry must be a string"
                );
            }

            table.intern(string.value());
        }

        return table;
    }

    static JsonValue bigIntegerArray(List<BigInteger> values) {
        if (values == null) {
            return JsonNull.INSTANCE;
        }

        JsonArray array = new JsonArray();

        for (BigInteger value : values) {
            array.add(value.toString(10));
        }

        return array;
    }

    static JsonObject requireObject(JsonValue value, String description) {
        if (!(value instanceof JsonObject object)) {
            throw new IllegalArgumentException(
                    "Expected " + description + " to be an object"
            );
        }

        return object;
    }

    static <T extends Enum<T>> T requireEnumName(String name, Class<T> type) {
        try {
            return Enum.valueOf(type, name.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown enum name '" + name + "'",
                    e
            );
        }
    }
}
