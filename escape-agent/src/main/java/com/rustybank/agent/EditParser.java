package com.rustybank.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the model's reply. The format is plain text on purpose, so every model can
 * produce it without provider-specific tool calling:
 *
 * <pre>
 * PLAN: what I am about to do
 *
 * === FILE: path/to/File.java ===
 * (complete new content)
 * === END FILE ===
 *
 * === DELETE: path/to/Old.java ===
 * </pre>
 *
 * The reader works line by line and accepts small deviations in the markers (a missing closing
 * "===", different case, "=== END ===", indentation), because those say nothing about whether a
 * model can solve the room. A file block that never ends is dropped: it is most likely cut off,
 * and half a file is worse than none.
 */
public final class EditParser {

    private static final Pattern FILE_HEADER =
            Pattern.compile("^\\s*={3,}\\s*FILE:\\s*(.+?)\\s*(?:={3,})?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_HEADER =
            Pattern.compile("^\\s*={3,}\\s*DELETE:\\s*(.+?)\\s*(?:={3,})?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern END_MARKER =
            Pattern.compile("^\\s*={3,}\\s*END(?:\\s+FILE)?\\s*(?:={3,})?\\s*$", Pattern.CASE_INSENSITIVE);

    private EditParser() {
    }

    public record Parsed(String plan, List<Edit> edits) {
    }

    public static Parsed parse(String reply) {
        String[] lines = reply.replace("\r\n", "\n").split("\n", -1);
        List<Edit> edits = new ArrayList<>();
        StringBuilder plan = new StringBuilder();
        String openPath = null;
        StringBuilder body = new StringBuilder();
        boolean seenBlock = false;

        for (String line : lines) {
            Matcher file = FILE_HEADER.matcher(line);
            Matcher delete = DELETE_HEADER.matcher(line);
            boolean isFile = file.matches();
            boolean isDelete = !isFile && delete.matches();

            if (openPath != null && END_MARKER.matcher(line).matches()) {
                edits.add(new Edit(openPath, stripFences(body.toString())));
                openPath = null;
                body.setLength(0);
                continue;
            }
            if (isFile || isDelete) {
                if (openPath != null) {
                    // The previous block forgot its end marker but a new one starts: keep it.
                    edits.add(new Edit(openPath, stripFences(body.toString())));
                    body.setLength(0);
                    openPath = null;
                }
                seenBlock = true;
                if (isDelete) {
                    edits.add(new Edit(delete.group(1).trim(), null));
                } else {
                    openPath = file.group(1).trim();
                }
                continue;
            }
            if (openPath != null) {
                body.append(line).append('\n');
            } else if (!seenBlock) {
                plan.append(line).append('\n');
            }
        }

        String planText = plan.toString().trim();
        if (planText.regionMatches(true, 0, "PLAN:", 0, 5)) {
            planText = planText.substring(5).trim();
        }
        return new Parsed(planText, List.copyOf(edits));
    }

    /** Models like to wrap code in markdown fences even when told not to. */
    private static String stripFences(String content) {
        String body = content;
        if (body.startsWith("```")) {
            int newline = body.indexOf('\n');
            body = newline < 0 ? "" : body.substring(newline + 1);
            String trimmed = body.stripTrailing();
            if (trimmed.endsWith("```")) {
                body = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        body = body.stripTrailing();
        return body.isEmpty() ? "" : body + "\n";
    }
}
