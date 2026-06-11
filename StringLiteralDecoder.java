import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class StringLiteralDecoder {
    private StringLiteralDecoder() {}

    /**
     * Converts a StringLiteralNode into an ArrayLiteralNode of unsigned UTF-8 bytes.
     *
     * Example:
     *   "ABC"      -> [65, 66, 67]
     *   "你好"      -> [228, 189, 160, 229, 165, 189]
     *   "a\nb"     -> [97, 10, 98]
     *
     * No null terminator is appended.
     */
    public static ArrayLiteralNode toUtf8ByteArrayLiteral(StringLiteralNode node) {
        if (node == null) {
            throw new RuntimeException("Cannot decode null StringLiteralNode");
        }

        String decoded = decodeRawContent(node.rawContent);
        byte[] bytes = decoded.getBytes(StandardCharsets.UTF_8);

        List<ASTNode> elements = new ArrayList<>();

        for (byte b : bytes) {
            /*
             * Java bytes are signed: -128..127.
             * Franko byte literals should be emitted as unsigned byte values: 0..255.
             */
            int unsignedByte = b & 0xFF;
            elements.add(new IntNode(Integer.toString(unsignedByte)));
        }

        return new ArrayLiteralNode(elements);
    }

    /**
     * Decodes only Franko-supported string escapes.
     *
     * Supported:
     *   \\  backslash
     *   \"  double quote
     *   \a  ASCII bell
     *   \b  ASCII backspace
     *   \f  ASCII form feed
     *   \n  line feed
     *   \r  carriage return
     *   \t  horizontal tab
     *   \v  vertical tab
     *
     * Unsupported:
     *   \u1234
     *   \xFF
     *   \0
     *   \q
     *
     * Literal Unicode characters, literal tabs, and literal newlines are preserved.
     */
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
