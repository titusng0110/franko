import java.math.BigInteger;
import java.util.List;

final class JsonAstSupport {

    static JsonValue requireDocument(
            String json,
            String kind,
            String format,
            int version
    ) {
        JsonValue value = Json.parse(json);

        if (!value.isObject()) {
            throw new IllegalArgumentException(
                    kind + " document must be a JSON object"
            );
        }

        JsonValue document = value;

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

    static JsonValue sourceArray(SourceTable sources) {
        JsonValue array = JsonValue.array();

        for (String source : sources.names()) {
            array.add(source);
        }

        return array;
    }

    static SourceTable sourceTable(JsonValue array, String kind) {
        SourceTable table = new SourceTable();

        for (JsonValue value : array.values()) {
            if (!value.isString()) {
                throw new IllegalArgumentException(
                        kind + " source entry must be a string"
                );
            }

            table.intern(value.value());
        }

        return table;
    }

    static JsonValue bigIntegerArray(List<BigInteger> values) {
        if (values == null) {
            return JsonValue.nullValue();
        }

        JsonValue array = JsonValue.array();

        for (BigInteger value : values) {
            array.add(value.toString(10));
        }

        return array;
    }

    static JsonValue requireObject(JsonValue value, String description) {
        if (!value.isObject()) {
            throw new IllegalArgumentException(
                    "Expected " + description + " to be an object"
            );
        }

        return value;
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
