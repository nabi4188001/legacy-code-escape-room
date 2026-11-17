package com.rustyledger.rooms.god;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rustyledger.Ledger;
import com.rustyledger.RustyLedger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * ROOM 4 - THE GOD CLASS - the lock on the door.
 *
 * <p><b>LOCKED FILE.</b> Every test here fails against the legacy code: one class that does
 * everything and keeps the whole bank in static fields. The room is escaped when all of
 * them pass and the golden master is still green.
 *
 * <p>The size limits are measured on the text of the source files in
 * {@code src/main/java}: a file may have at most {@value #MAX_FILE_LINES} lines, a method
 * (or any other braced block directly inside a class) at most {@value #MAX_BLOCK_LINES}
 * lines, and there must be at least {@value #MIN_FILES} source files.
 */
@Tag("escape")
@DisplayName("God Class: the shape of the code")
class LedgerEscapeTest {

    private static final int MAX_FILE_LINES = 150;
    private static final int MAX_BLOCK_LINES = 30;
    private static final int MIN_FILES = 8;

    @Test
    @DisplayName("two ledgers are two banks")
    void ledgersAreIndependent() {
        Ledger first = RustyLedger.openLedger();
        first.openAccount("A", "STANDARD", "PLN", "100.00");
        first.submit("DEPOSIT|2026-03-02|A|50.00");

        Ledger second = RustyLedger.openLedger();
        second.openAccount("A", "PREMIUM", "EUR", "5.00");

        assertEquals("150.00 PLN", first.balance("A"));
        assertEquals("5.00 EUR", second.balance("A"));
        assertEquals(1, first.auditLog().size());
        assertEquals(0, second.auditLog().size());
        second.submit("DEPOSIT|2026-03-02|A|1.00");
        assertEquals("OK TX-000002 fee=0.00 PLN", first.submit("DEPOSIT|2026-03-02|A|1.00"));
    }

    @Test
    @DisplayName("no source file is longer than 150 lines")
    void noGodFiles() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : sources()) {
            int lines = Files.readAllLines(file).size();
            if (lines > MAX_FILE_LINES) {
                offenders.add(file.getFileName() + " has " + lines + " lines");
            }
        }
        assertEquals(List.of(), offenders);
    }

    @Test
    @DisplayName("no method is longer than 30 lines")
    void noEssayMethods() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : sources()) {
            int longest = longestBlock(Files.readString(file));
            if (longest > MAX_BLOCK_LINES) {
                offenders.add(file.getFileName() + " has a block of " + longest + " lines");
            }
        }
        assertEquals(List.of(), offenders);
    }

    @Test
    @DisplayName("the work is shared out over at least 8 source files")
    void workIsSharedOut() throws IOException {
        List<Path> files = sources();
        assertEquals(true, files.size() >= MIN_FILES,
                "only " + files.size() + " source files: " + files.stream().map(Path::getFileName).toList());
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> walk = Files.walk(Path.of("src/main/java"))) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    /**
     * The number of lines of the longest braced block that sits directly inside a type, that
     * is a method, a constructor, an initialiser or a nested type. Braces inside comments,
     * strings and character literals do not count.
     */
    static int longestBlock(String source) {
        int depth = 0;
        int line = 1;
        int startLine = 0;
        int longest = 0;
        boolean lineComment = false;
        boolean blockComment = false;
        boolean string = false;
        boolean textBlock = false;
        boolean character = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (c == '\n') {
                line++;
                lineComment = false;
                string = false;
                continue;
            }
            if (lineComment) {
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (textBlock) {
                if (c == '"' && next == '"' && i + 2 < source.length() && source.charAt(i + 2) == '"') {
                    textBlock = false;
                    i += 2;
                }
                continue;
            }
            if (string || character) {
                if (c == '\\') {
                    i++;
                } else if (string && c == '"') {
                    string = false;
                } else if (character && c == '\'') {
                    character = false;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                lineComment = true;
                i++;
            } else if (c == '/' && next == '*') {
                blockComment = true;
                i++;
            } else if (c == '"' && next == '"' && i + 2 < source.length() && source.charAt(i + 2) == '"') {
                textBlock = true;
                i += 2;
            } else if (c == '"') {
                string = true;
            } else if (c == '\'') {
                character = true;
            } else if (c == '{') {
                depth++;
                if (depth == 2) {
                    startLine = line;
                }
            } else if (c == '}') {
                if (depth == 2) {
                    longest = Math.max(longest, line - startLine + 1);
                }
                depth--;
            }
        }
        return longest;
    }
}
