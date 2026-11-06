import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CompilerJsonSelfTest {

    private CompilerJsonSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: java -cp build/classes CompilerJsonSelfTest <artifact.json>");
            System.exit(2);
        }

        Path path = Path.of(args[0]);
        String original = Files.readString(path, StandardCharsets.UTF_8);
        JsonValue parsed = Json.parse(original);

        if (!parsed.isObject()) {
            fail(path, "artifact root is not a JSON object");
            return;
        }

        JsonValue object = parsed;

        String format = object.requireString("format");
        String roundTripped;

        switch (format) {
            case "franko-ast" -> roundTripped = ASTSerializer.toJson(ASTSerializer.fromJson(original));
            case "franko-sast" -> roundTripped = SastSerializer.toJson(SastSerializer.fromJson(original));
            case "franko-diagnostics" -> roundTripped = Json.write(parsed, true);
            default -> {
                fail(path, "unknown artifact format '" + format + "'");
                return;
            }
        }

        String canonicalOriginal = Json.canonical(Json.parse(original));
        String canonicalRoundTrip = Json.canonical(Json.parse(roundTripped));

        if (!canonicalOriginal.equals(canonicalRoundTrip)) {
            fail(path, "round-trip changed canonical JSON");
        }

        System.out.println("OK: " + path);
    }

    private static void fail(Path path, String message) {
        System.err.println("FAIL: " + path + ": " + message);
        System.exit(1);
    }
}
