package com.rustybank.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns the state of the room into the conversation the model sees on each attempt. */
public final class PromptBuilder {

    private static final int MAX_FAILURES_SHOWN = 30;

    private PromptBuilder() {
    }

    static final String SYSTEM = """
            You are a senior Java engineer locked inside a legacy banking codebase.
            The only way out is to fix the code until every test passes.

            Rules of the room:
            - You may create, rewrite or delete .java files in the editable area only.
            - Locked files (the tests and the public contract) cannot be changed.
            - Behaviour covered by the golden tests must keep working.
            - Fix the real problem. Never special-case the inputs used by the tests.
            - Java 21, standard library only.

            Reply in exactly this format and nothing else:

            PLAN: two or three sentences on what is wrong and what you will change.

            === FILE: path/from/repo/root/File.java ===
            the complete new content of the file
            === END FILE ===

            === DELETE: path/from/repo/root/Old.java ===

            Always send whole files, never fragments or diffs. Do not use markdown fences.
            Only send files you are changing.
            """;

    public static List<ChatModel.Message> build(Room room, Workspace workspace,
                                                VerifyResult lastResult,
                                                Map<String, String> lastRejected,
                                                List<String> previousPlans, int attempt,
                                                int maxAttempts) {
        StringBuilder user = new StringBuilder();
        user.append("# ").append(room.name()).append("\n\n");
        user.append("Attempt ").append(attempt).append(" of ").append(maxAttempts).append(".\n\n");
        user.append("## Room brief\n\n").append(room.brief().strip()).append("\n\n");

        user.append("## Editable area\n\n");
        for (String prefix : room.editable()) {
            user.append("- ").append(prefix).append('\n');
        }
        user.append('\n');

        user.append("## Locked files (read only)\n\n");
        appendFiles(user, workspace.lockedFiles());

        user.append("## Current editable files\n\n");
        appendFiles(user, workspace.editableFiles());

        if (!previousPlans.isEmpty()) {
            user.append("## Your earlier attempts in this room\n\n");
            for (int i = 0; i < previousPlans.size(); i++) {
                user.append(i + 1).append(". ").append(previousPlans.get(i)).append('\n');
            }
            user.append("\nThe files above already contain those changes.\n\n");
        }

        user.append("## Latest test run\n\n").append(describe(lastResult)).append('\n');

        if (lastRejected != null && !lastRejected.isEmpty()) {
            user.append("\n## Edits that were refused last time\n\n");
            lastRejected.forEach((path, why) ->
                    user.append("- ").append(path).append(": ").append(why).append('\n'));
        }

        List<ChatModel.Message> messages = new ArrayList<>();
        messages.add(ChatModel.Message.system(SYSTEM));
        messages.add(ChatModel.Message.user(user.toString()));
        return messages;
    }

    private static void appendFiles(StringBuilder out, Map<String, String> files) {
        files.forEach((path, content) -> out.append("### ").append(path).append("\n\n")
                .append(content.stripTrailing()).append("\n\n"));
    }

    static String describe(VerifyResult result) {
        StringBuilder out = new StringBuilder();
        out.append("Result: ").append(result.headline()).append('\n');
        if (!result.compiled()) {
            out.append("\nBuild output:\n").append(result.buildOutput()).append('\n');
            return out.toString();
        }
        int shown = 0;
        for (VerifyResult.Failure failure : result.failures()) {
            if (shown++ == MAX_FAILURES_SHOWN) {
                out.append("- ... and more\n");
                break;
            }
            String message = failure.message().replaceAll("\\s+", " ");
            if (message.length() > 300) {
                message = message.substring(0, 300) + "...";
            }
            out.append("- [").append(failure.suite()).append("] ").append(failure.test())
                    .append(": ").append(message).append('\n');
        }
        return out.toString();
    }
}
