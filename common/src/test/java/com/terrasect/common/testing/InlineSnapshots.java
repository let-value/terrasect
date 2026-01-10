package com.terrasect.common.testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;

public final class InlineSnapshots {
    private static final String UPDATE_PROPERTY = "terrasect.inlineSnapshots";
    private static final String UPDATE_ALIAS = "terrasect.snapshots.inline";

    private InlineSnapshots() {}

    public static void assertInlineSnapshot(Object actual, String expected) {
        assertInlineSnapshot(String.valueOf(actual), expected);
    }

    public static void assertInlineSnapshot(String actual, String expected) {
        String normalizedExpected = normalizeExpected(expected, actual);
        if (Objects.equals(normalizedExpected, actual)) {
            return;
        }

        UpdateMode mode = UpdateMode.fromSystemProperties();
        if (mode == UpdateMode.NONE) {
            Assertions.assertEquals(
                    normalizedExpected,
                    actual,
                    "Inline snapshot mismatch. Re-run with -D" + UPDATE_PROPERTY + "=update to refresh.");
            return;
        }

        updateInlineSnapshot(actual);
        if (mode == UpdateMode.UPDATE_AND_PASS) {
            return;
        }
        Assertions.fail("Inline snapshot updated. Re-run tests without -D" + UPDATE_PROPERTY + " to verify.");
    }

    private static void updateInlineSnapshot(String actual) {
        StackWalker.StackFrame frame = findCallerFrame();
        Path source = findSourceFile(frame.getDeclaringClass());
        String content;
        try {
            content = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read source file: " + source, e);
        }

        int lineOffset = lineStartOffset(content, frame.getLineNumber());
        int textBlockStart = findTextBlockStart(content, lineOffset);
        if (textBlockStart < 0) {
            throw new IllegalStateException("Inline snapshot must use a text block (\"\"\") in " + source);
        }

        int textBlockEnd = findTextBlockEnd(content, textBlockStart + 3);
        if (textBlockEnd < 0) {
            throw new IllegalStateException("Unterminated text block in " + source);
        }

        String newline = content.contains("\r\n") ? "\r\n" : "\n";
        String indent = lineIndentation(content, textBlockEnd);
        String escaped = escapeTextBlock(actual);
        String updatedBlock = buildTextBlock(escaped, indent, newline);

        String updated = content.substring(0, textBlockStart + 3) + updatedBlock + content.substring(textBlockEnd);

        try {
            Files.writeString(source, updated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to update inline snapshot in " + source, e);
        }
    }

    private static String normalizeExpected(String expected, String actual) {
        if (expected == null || actual == null) {
            return expected;
        }
        if (actual.endsWith("\n") || actual.endsWith("\r\n")) {
            return expected;
        }
        if (expected.endsWith("\r\n")) {
            return expected.substring(0, expected.length() - 2);
        }
        if (expected.endsWith("\n")) {
            return expected.substring(0, expected.length() - 1);
        }
        return expected;
    }

    private static StackWalker.StackFrame findCallerFrame() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(stream -> stream.filter(frame -> !frame.getClassName().equals(InlineSnapshots.class.getName()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Unable to locate inline snapshot callsite")));
    }

    private static int findTextBlockStart(String content, int fromIndex) {
        int callIndex = content.indexOf("assertInlineSnapshot", fromIndex);
        int searchFrom = callIndex >= 0 ? callIndex : fromIndex;
        return content.indexOf("\"\"\"", searchFrom);
    }

    private static int findTextBlockEnd(String content, int fromIndex) {
        int searchIndex = fromIndex;
        while (searchIndex < content.length()) {
            int next = content.indexOf("\"\"\"", searchIndex);
            if (next < 0) {
                return -1;
            }
            if (next == 0 || content.charAt(next - 1) != '\\') {
                return next;
            }
            searchIndex = next + 3;
        }
        return -1;
    }

    private static int lineStartOffset(String content, int lineNumber) {
        if (lineNumber <= 1) {
            return 0;
        }
        int line = 1;
        int offset = 0;
        while (line < lineNumber && offset < content.length()) {
            int next = content.indexOf('\n', offset);
            if (next < 0) {
                return 0;
            }
            offset = next + 1;
            line++;
        }
        return offset;
    }

    private static String lineIndentation(String content, int offset) {
        int lineStart = content.lastIndexOf('\n', offset);
        int start = lineStart < 0 ? 0 : lineStart + 1;
        int i = start;
        while (i < content.length()) {
            char c = content.charAt(i);
            if (c != ' ' && c != '\t') {
                break;
            }
            i++;
        }
        return content.substring(start, i);
    }

    private static String buildTextBlock(String value, String indent, String newline) {
        String normalized = value.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        StringBuilder sb = new StringBuilder(normalized.length() + indent.length() * lines.length + 16);
        sb.append(newline);
        for (int i = 0; i < lines.length; i++) {
            sb.append(indent).append(lines[i]);
            if (i < lines.length - 1) {
                sb.append(newline);
            }
        }
        sb.append(newline).append(indent);
        return sb.toString();
    }

    private static String escapeTextBlock(String value) {
        String escaped = value.replace("\\", "\\\\");
        return escaped.replace("\"\"\"", "\\\"\\\"\\\"");
    }

    private static Path findSourceFile(Class<?> testClass) {
        Path projectRoot = findProjectRoot(Paths.get(System.getProperty("user.dir", ".")));
        Path relative = Paths.get(testClass.getName().replace('.', '/') + ".java");

        List<Path> roots = new ArrayList<>();
        roots.add(projectRoot.resolve("common/src/test/java"));
        roots.add(projectRoot.resolve("fabric/src/test/java"));
        roots.add(projectRoot.resolve("neoforge/src/test/java"));
        roots.add(projectRoot.resolve("src/test/java"));

        for (Path root : roots) {
            Path candidate = root.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        Path versionsDir = projectRoot.resolve("versions");
        if (Files.isDirectory(versionsDir)) {
            try (Stream<Path> stream = Files.find(versionsDir, 12, (path, attrs) -> path.endsWith(relative))) {
                Optional<Path> found = stream.findFirst();
                if (found.isPresent()) {
                    return found.get();
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to search versioned sources under " + versionsDir, e);
            }
        }

        throw new IllegalStateException("Unable to locate source file for " + testClass.getName());
    }

    private static Path findProjectRoot(Path start) {
        Path current = start.toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle"))
                    || Files.exists(current.resolve("settings.gradle.kts"))
                    || Files.exists(current.resolve(".git"))) {
                return current;
            }
            current = current.getParent();
        }
        return start.toAbsolutePath();
    }

    private enum UpdateMode {
        NONE,
        UPDATE_AND_FAIL,
        UPDATE_AND_PASS;

        static UpdateMode fromSystemProperties() {
            String value = System.getProperty(UPDATE_PROPERTY);
            if (value == null || value.isBlank()) {
                value = System.getProperty(UPDATE_ALIAS);
            }
            if (value == null || value.isBlank()) {
                return NONE;
            }
            if ("update-and-pass".equalsIgnoreCase(value)) {
                return UPDATE_AND_PASS;
            }
            if ("update".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "1".equalsIgnoreCase(value)) {
                return UPDATE_AND_FAIL;
            }
            return NONE;
        }
    }
}
