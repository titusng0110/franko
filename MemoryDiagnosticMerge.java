import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MemoryDiagnosticMerge {
    private static final class Entry {
        int casesWithDiagnostic;
        int errorCases;
        Diagnostic errorSample;
        Diagnostic warningSample;
    }

    private MemoryDiagnosticMerge() {
    }

    static void mergeInto(
            DiagnosticBag bag,
            List<List<Diagnostic>> perCase,
            int caseCount
    ) {
        if (perCase.isEmpty()) {
            return;
        }

        if (caseCount <= 1) {
            for (List<Diagnostic> diagnostics : perCase) {
                for (Diagnostic diagnostic : diagnostics) {
                    bag.add(diagnostic);
                }
            }

            return;
        }

        Map<String, Entry> entries = new LinkedHashMap<>();

        for (List<Diagnostic> diagnostics : perCase) {
            List<String> seenInCase = new ArrayList<>();

            for (Diagnostic diagnostic : diagnostics) {
                String key = keyOf(diagnostic);

                if (seenInCase.contains(key)) {
                    continue;
                }

                seenInCase.add(key);
                Entry entry = entries.computeIfAbsent(key, unused -> new Entry());
                entry.casesWithDiagnostic++;

                if (diagnostic.isError()) {
                    entry.errorCases++;

                    if (entry.errorSample == null) {
                        entry.errorSample = diagnostic;
                    }
                } else if (entry.warningSample == null) {
                    entry.warningSample = diagnostic;
                }
            }
        }

        for (Entry entry : entries.values()) {
            if (entry.casesWithDiagnostic == caseCount
                    && entry.errorCases == caseCount) {
                bag.add(entry.errorSample);
                continue;
            }

            if (entry.warningSample != null) {
                bag.add(entry.warningSample);
            } else if (entry.errorSample != null) {
                bag.add(Diagnostic.warning(
                        entry.errorSample.span,
                        possibleMessage(entry.errorSample.message),
                        entry.errorSample.code
                ));
            }
        }
    }

    private static String keyOf(Diagnostic diagnostic) {
        String code = diagnostic.code;

        if (code == null || code.isEmpty()) {
            code = normalize(diagnostic.message);
        }

        return diagnostic.span.format() + "|" + code;
    }

    private static String normalize(String message) {
        String result = message;

        if (result.startsWith("Intraprocedural: ")) {
            result = result.substring("Intraprocedural: ".length());
        } else if (result.startsWith("Interprocedural: ")) {
            result = result.substring("Interprocedural: ".length());
        }

        result = result.replace("Cannot ", "");
        result = result.replace("Possible ", "");
        result = result.replace("possibly ", "");
        result = result.replace("may ", "");
        result = result.replace("Deref of ", "");

        int quote = result.indexOf('\'');

        if (quote >= 0) {
            result = result.substring(0, quote);
        }

        return result.trim();
    }

    static String possibleMessage(String message) {
        String prefix = "";

        if (message.startsWith("Intraprocedural: ")) {
            prefix = "Intraprocedural: ";
            message = message.substring(prefix.length());
        } else if (message.startsWith("Interprocedural: ")) {
            prefix = "Interprocedural: ";
            message = message.substring(prefix.length());
        }

        if (message.startsWith("Cannot dereference null variable '")) {
            return prefix + "Deref of possibly null variable "
                    + message.substring("Cannot dereference null variable ".length());
        }

        if (message.equals("Cannot dereference a null pointer")) {
            return prefix + "Deref of a possibly null pointer";
        }

        if (message.startsWith("Passing null to parameter '")) {
            String parameter = between(
                    message,
                    "Passing null to parameter '",
                    "' of '"
            );
            String function = between(
                    message,
                    "' of '",
                    "' which"
            );

            if (parameter != null && function != null) {
                return prefix
                        + "Passing a possibly null value to parameter '"
                        + parameter
                        + "' of '"
                        + function
                        + "' which may be dereferenced";
            }
        }

        if (message.startsWith("Invalid free of dynamic array '")
                || message.startsWith("Double free of dynamic array '")) {
            int quote = message.lastIndexOf('\'');

            if (quote > 0) {
                return prefix
                        + "Possible invalid free or double free of dynamic array "
                        + message.substring(
                                message.indexOf('\''),
                                quote + 1
                        );
            }
        }

        if (message.startsWith("Use of uninitialized dynamic array '")
                && message.endsWith("' before init")) {
            return prefix + "Possible use of uninitialized dynamic array "
                    + message.substring(
                            message.indexOf('\''),
                            message.length() - " before init".length()
                    );
        }

        if (message.startsWith("Use of dynamic array '")
                && message.endsWith("' after uninit")) {
            return prefix + "Possible use of dynamic array "
                    + message.substring(
                            message.indexOf('\''),
                            message.length() - " after uninit".length()
                    )
                    + " after uninit";
        }

        if (message.startsWith("Passing uninitialized dynamic array '")) {
            return prefix + "Passing possibly uninitialized dynamic array "
                    + message.substring(
                            message.indexOf('\''),
                            message.lastIndexOf('\'') + 1
                    )
                    + message.substring(message.lastIndexOf('\'') + 1);
        }

        if (message.startsWith("Passing freed dynamic array '")) {
            return prefix + "Passing possibly uninitialized dynamic array "
                    + message.substring(
                            message.indexOf('\''),
                            message.lastIndexOf('\'') + 1
                    )
                    + message.substring(message.lastIndexOf('\'') + 1);
        }

        if (message.contains(" was invalidated by resize of dynamic array '")) {
            String pointer = message.substring(
                    message.indexOf('\'') + 1,
                    message.indexOf('\'', message.indexOf('\'') + 1)
            );
            String array = message.substring(
                    message.lastIndexOf('\'') + 1
            );

            if (array.endsWith("'")) {
                array = array.substring(0, array.length() - 1);
            }

            return prefix + "Pointer '" + pointer
                    + "' may have been invalidated by resize or uninit"
                    + " of dynamic array '" + array + "'";
        }

        if (message.startsWith("Cannot dereference pointer to ")) {
            String origins = between(
                    message,
                    "Cannot dereference pointer to ",
                    " after its scope ended"
            );

            if (origins != null) {
                return prefix + "Pointer may refer to " + origins
                        + " after its scope ended";
            }
        }

        if (message.startsWith("Cannot use pointer to ")) {
            String origins = between(
                    message,
                    "Cannot use pointer to ",
                    " after its scope ended"
            );

            if (origins != null) {
                return prefix + "Pointer may refer to " + origins
                        + " after its scope ended";
            }
        }

        if (message.startsWith("Returning a pointer to ")
                && message.contains("(dangling after return)")) {
            String rest = message.substring("Returning a pointer to ".length());
            int marker = rest.indexOf(" from function '");

            if (marker >= 0) {
                String origins = rest.substring(0, marker);
                String function = rest.substring(marker);
                function = function.substring(
                        0,
                        function.length() - "(dangling after return)".length()
                );
                return prefix + "Returning a pointer that may refer to "
                        + origins + function + "(possibly dangling after return)";
            }
        }

        if (message.startsWith("Cannot store a view of frame")) {
            return prefix + "May store a view of frame or operator-borrowed"
                    + " storage into a global or caller-owned location";
        }

        return prefix + "Possible: " + message;
    }

    private static String between(
            String text,
            String start,
            String end
    ) {
        int startIndex = text.indexOf(start);

        if (startIndex < 0) {
            return null;
        }

        int endIndex = text.indexOf(end, startIndex + start.length());

        if (endIndex < 0) {
            return null;
        }

        return text.substring(startIndex + start.length(), endIndex);
    }
}
