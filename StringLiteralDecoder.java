import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class StringLiteralDecoder {
    private StringLiteralDecoder() {}

    public static List<BigInteger> toUtf8Bytes(StringLiteralNode node) {
        if (node == null) {
            throw new RuntimeException("Cannot decode null StringLiteralNode");
        }

        String decoded = decodeRawContent(node.rawContent);
        byte[] bytes = decoded.getBytes(StandardCharsets.UTF_8);

        List<BigInteger> elements = new ArrayList<>();

        for (byte b : bytes) {
            elements.add(BigInteger.valueOf(b & 0xFF));
        }

        return elements;
    }

    public static String decodeRawContent(String rawContent) {
        if (rawContent == null) {
            throw new RuntimeException("Cannot decode null string literal content");
        }

        StringBuilder out = new StringBuilder();

        for (int i = 0; i < rawContent.length(); i++) {
            char c = rawContent.charAt(i);

            if (c != '\\') {
                out.append(c);
                continue;
            }

            if (i + 1 >= rawContent.length()) {
                throw new RuntimeException("Invalid string literal: trailing backslash");
            }

            char esc = rawContent.charAt(++i);

            switch (esc) {
                case '\\':
                    out.append('\\');
                    break;

                case '"':
                    out.append('"');
                    break;

                case 'a':
                    out.append('\u0007');
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

                case 'v':
                    out.append('\u000B');
                    break;

                default:
                    throw new RuntimeException(
                        "Unsupported string escape sequence: \\" + esc
                    );
            }
        }

        return out.toString();
    }
}
