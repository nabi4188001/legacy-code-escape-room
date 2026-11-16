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
 */
public final class EditParser {

    private static final Pattern BLOCK = Pattern.compile(
            "^=== FILE: (.+?) ===[ \\t]*\\R(.*?)^=== END FILE ===[ \\t]*$"
                    + "|^=== DELETE: (.+?) ===[ \\t]*$",
            Pattern.MULTILINE | Pattern.DOTALL);

    private EditParser() {
    }

    public record Parsed(String plan, List<Edit> edits) {
    }

    public static Parsed parse(String reply) {
        String text = reply.replace("\r\n", "\n");
        List<Edit> edits = new ArrayList<>();
        Matcher matcher = BLOCK.matcher(text);
        int firstBlock = -1;
        while (matcher.find()) {
            if (firstBlock < 0) {
                firstBlock = matcher.start();
            }
            if (matcher.group(1) != null) {
                edits.add(new Edit(matcher.group(1).trim(), stripFences(matcher.group(2))));
            } else {
                edits.add(new Edit(matcher.group(3).trim(), null));
            }
        }
        String plan = (firstBlock < 0 ? text : text.substring(0, firstBlock)).trim();
        if (plan.regionMatches(true, 0, "PLAN:", 0, 5)) {
            plan = plan.substring(5).trim();
        }
        return new Parsed(plan, List.copyOf(edits));
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
