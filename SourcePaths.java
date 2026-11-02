import java.nio.file.Path;

public final class SourcePaths {

    private SourcePaths() {
    }

    public static String display(Path path) {
        if (path == null) {
            return "";
        }

        Path absolute = path.toAbsolutePath().normalize();
        Path cwd = Path.of("").toAbsolutePath().normalize();

        String text;

        try {
            if (absolute.startsWith(cwd)) {
                text = cwd.relativize(absolute).toString();
            } else {
                text = absolute.toString();
            }
        } catch (IllegalArgumentException e) {
            text = absolute.toString();
        }

        return text.replace('\\', '/');
    }
}
