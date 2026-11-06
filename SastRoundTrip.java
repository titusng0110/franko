import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SastRoundTrip {

    private SastRoundTrip() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: java -cp build/classes SastRoundTrip <directory>");
            System.exit(2);
        }

        Path root = Path.of(args[0]);
        int[] counts = new int[] {0, 0};
        visit(root, counts);

        System.out.println("checked " + counts[0] + " SAST artifact(s), "
                + counts[1] + " failure(s)");

        if (counts[1] != 0) {
            System.exit(1);
        }
    }

    private static void visit(Path path, int[] counts) throws Exception {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    visit(entry, counts);
                }
            }
            return;
        }

        if (!path.getFileName().toString().endsWith(".sast.json")) {
            return;
        }

        String original = Files.readString(path, StandardCharsets.UTF_8);
        SastArtifact artifact = SastSerializer.fromJson(original);
        String roundTripped = SastSerializer.toJson(artifact);
        String canonicalOriginal = Json.canonical(Json.parse(original));
        String canonicalRoundTrip = Json.canonical(Json.parse(roundTripped));

        counts[0]++;

        if (!canonicalOriginal.equals(canonicalRoundTrip)) {
            counts[1]++;
            System.err.println("FAIL: " + path);
        }
    }
}
