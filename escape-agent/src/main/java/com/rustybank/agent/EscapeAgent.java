package com.rustybank.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The loop: look at the room, ask the model for a fix, apply it in the sandbox, run the
 * tests, and go again with the failures until the room is escaped or attempts run out.
 * Everything that happens is recorded, so a run can be replayed step by step later.
 */
public final class EscapeAgent {

    private final Room room;
    private final ChatModel model;
    private final Verifier verifier;
    private final int maxAttempts;
    private final Consumer<String> narrator;

    public EscapeAgent(Room room, ChatModel model, Verifier verifier, int maxAttempts,
                       Consumer<String> narrator) {
        this.room = room;
        this.model = model;
        this.verifier = verifier;
        this.maxAttempts = maxAttempts;
        this.narrator = narrator;
    }

    /** Runs the escape attempt and returns the full run log, ready to be written as JSON. */
    public Map<String, Object> run(Workspace workspace) {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("room", room.id());
        log.put("roomName", room.name());
        log.put("model", model.name());
        log.put("startedAt", Instant.now().toString());
        log.put("maxAttempts", maxAttempts);

        long runStart = System.nanoTime();
        VerifyResult result = verifier.verify(workspace);
        log.put("baseline", toMap(result));
        narrator.accept("Locked in. " + result.headline());

        List<Object> attempts = new ArrayList<>();
        List<String> plans = new ArrayList<>();
        Map<String, String> rejected = Map.of();
        long promptTokens = 0;
        long completionTokens = 0;

        for (int n = 1; n <= maxAttempts && !result.escaped(); n++) {
            Map<String, Object> attempt = new LinkedHashMap<>();
            attempt.put("number", n);

            long modelStart = System.nanoTime();
            ChatModel.Reply reply;
            try {
                reply = model.chat(PromptBuilder.build(
                        room, workspace, result, rejected, plans, n, maxAttempts));
            } catch (RuntimeException e) {
                attempt.put("modelSeconds", seconds(modelStart));
                attempt.put("error", String.valueOf(e.getMessage()));
                attempts.add(attempt);
                log.put("status", "error");
                log.put("error", String.valueOf(e.getMessage()));
                narrator.accept("Attempt " + n + ": the model call failed: " + e.getMessage());
                break;
            }
            attempt.put("modelSeconds", seconds(modelStart));
            attempt.put("promptTokens", reply.promptTokens());
            attempt.put("completionTokens", reply.completionTokens());
            promptTokens += reply.promptTokens();
            completionTokens += reply.completionTokens();

            EditParser.Parsed parsed = EditParser.parse(reply.content());
            String plan = parsed.plan().isBlank() ? "(no plan given)" : parsed.plan();
            plans.add(plan);
            attempt.put("plan", plan);
            narrator.accept("Attempt " + n + " plan: " + plan);

            Workspace.Applied applied = workspace.apply(parsed.edits());
            rejected = applied.rejected();
            List<Object> edits = new ArrayList<>();
            for (Edit edit : applied.accepted()) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("path", edit.path());
                String before = applied.before().get(edit.path());
                e.put("action", edit.isDelete() ? "delete" : before == null ? "create" : "rewrite");
                e.put("before", before);
                e.put("after", edit.content());
                edits.add(e);
            }
            attempt.put("edits", edits);
            attempt.put("rejectedEdits", new LinkedHashMap<>(rejected));

            if (applied.accepted().isEmpty()) {
                attempt.put("note", "The reply contained no usable edits, so nothing was run.");
                narrator.accept("Attempt " + n + ": no usable edits.");
            } else {
                long verifyStart = System.nanoTime();
                result = verifier.verify(workspace);
                attempt.put("verifySeconds", seconds(verifyStart));
                narrator.accept("Attempt " + n + ": " + result.headline());
            }
            attempt.put("result", toMap(result));
            attempts.add(attempt);
        }

        log.putIfAbsent("status", "completed");
        log.put("attempts", attempts);
        log.put("escaped", result.escaped());
        log.put("attemptsUsed", attempts.size());
        log.put("promptTokens", promptTokens);
        log.put("completionTokens", completionTokens);
        log.put("totalSeconds", seconds(runStart));
        if (!"completed".equals(log.get("status"))) {
            narrator.accept("Run ended early: " + log.get("error"));
            return log;
        }
        narrator.accept(result.escaped()
                ? "ESCAPED " + room.name() + " in " + attempts.size() + " attempt(s)."
                : "Still locked in " + room.name() + " after " + attempts.size() + " attempt(s).");
        return log;
    }

    private static double seconds(long startNanos) {
        return Math.round((System.nanoTime() - startNanos) / 1e7) / 100.0;
    }

    private static Map<String, Object> toMap(VerifyResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("escaped", result.escaped());
        map.put("headline", result.headline());
        map.put("locksIntact", result.brokenLocks().isEmpty());
        map.put("compiled", result.compiled());
        map.put("goldenPassed", result.goldenPassed());
        map.put("goldenTotal", result.goldenTotal());
        map.put("escapePassed", result.escapePassed());
        map.put("escapeTotal", result.escapeTotal());
        List<Object> failures = new ArrayList<>();
        for (VerifyResult.Failure failure : result.failures()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("suite", failure.suite());
            f.put("test", failure.test());
            f.put("message", failure.message());
            failures.add(f);
        }
        map.put("failures", failures);
        if (!result.compiled()) {
            map.put("buildOutput", result.buildOutput());
        }
        return map;
    }
}
