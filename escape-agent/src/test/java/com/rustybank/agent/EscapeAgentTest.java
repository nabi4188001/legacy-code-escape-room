package com.rustybank.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The loop itself, with a scripted model and a verifier that needs no Maven. */
class EscapeAgentTest {

    private static final String TERMINAL =
            "rusty-bank/src/main/java/com/rustybank/ExactTellerTerminal.java";

    /** Calls the room escaped once the BigDecimal terminal exists. */
    private static Verifier fakeVerifier() {
        return workspace -> {
            boolean fixed = Files.exists(workspace.root().resolve(TERMINAL));
            List<VerifyResult.Failure> failures = fixed ? List.of() : List.of(
                    new VerifyResult.Failure("escape", "tenDimesMakeADollar",
                            "INSUFFICIENT FUNDS"));
            return new VerifyResult(workspace.brokenLocks(), true, 13, 13, fixed ? 9 : 8, 9,
                    failures, "");
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    void retriesWithFeedbackUntilItEscapes() throws Exception {
        Room room = Room.load(TestSupport.repoRoot(), "01-penny-room");
        Workspace workspace = TestSupport.freshWorkspace(room);
        TestSupport.ScriptedModel model = new TestSupport.ScriptedModel(
                TestSupport.resource("attempt1.txt"), TestSupport.resource("attempt2.txt"));
        List<String> story = new ArrayList<>();

        Map<String, Object> log = new EscapeAgent(room, model, fakeVerifier(), 5, story::add)
                .run(workspace);

        assertEquals(true, log.get("escaped"));
        assertEquals(2, log.get("attemptsUsed"));
        assertEquals(200L, log.get("promptTokens"));
        assertEquals(2, model.conversations.size());

        List<Object> attempts = (List<Object>) log.get("attempts");
        Map<String, Object> first = (Map<String, Object>) attempts.get(0);
        assertEquals(1, ((List<Object>) first.get("edits")).size());
        assertEquals(1, ((Map<String, String>) first.get("rejectedEdits")).size());

        String secondPrompt = model.conversations.get(1).get(1).content();
        assertTrue(secondPrompt.contains("Attempt 2 of 5"));
        assertTrue(secondPrompt.contains("tenDimesMakeADollar: INSUFFICIENT FUNDS"));
        assertTrue(secondPrompt.contains("this file is locked"));
        assertTrue(secondPrompt.contains("BigDecimal.valueOf(balance)"));
        assertTrue(story.get(story.size() - 1).startsWith("ESCAPED"));

        Object reread = Json.parse(Json.write(log));
        assertEquals(true, Json.at(reread, "attempts", 1, "result", "escaped"));
    }

    @Test
    void givesUpWhenAttemptsRunOut() throws Exception {
        Room room = Room.load(TestSupport.repoRoot(), "01-penny-room");
        Workspace workspace = TestSupport.freshWorkspace(room);
        TestSupport.ScriptedModel model = new TestSupport.ScriptedModel("I have no idea.");

        Map<String, Object> log = new EscapeAgent(room, model, fakeVerifier(), 3, line -> { })
                .run(workspace);

        assertEquals(false, log.get("escaped"));
        assertEquals(3, log.get("attemptsUsed"));
        assertFalse(Files.exists(workspace.root().resolve(TERMINAL)));
    }

    @Test
    void firstPromptShowsTheRoomTheCodeAndTheFailures() throws Exception {
        Room room = Room.load(TestSupport.repoRoot(), "01-penny-room");
        Workspace workspace = TestSupport.freshWorkspace(room);
        TestSupport.ScriptedModel model = new TestSupport.ScriptedModel("nothing");
        new EscapeAgent(room, model, fakeVerifier(), 1, line -> { }).run(workspace);

        String prompt = model.conversations.get(0).get(1).content();
        assertTrue(prompt.contains("# The Penny Room"));
        assertTrue(prompt.contains("### rusty-bank/src/main/java/com/rustybank/legacy/MoneyUtil.java"));
        assertTrue(prompt.contains("class PennyRoomEscapeTest"));
        assertTrue(prompt.contains("golden 13/13, escape 8/9"));
        assertEquals("system", model.conversations.get(0).get(0).role());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFailingModelCallEndsTheRunButKeepsTheLog() throws Exception {
        Room room = Room.load(TestSupport.repoRoot(), "01-penny-room");
        Workspace workspace = TestSupport.freshWorkspace(room);
        String partial = TestSupport.resource("attempt1.txt");
        int[] calls = {0};
        ChatModel flaky = new ChatModel() {
            public String name() {
                return "flaky";
            }

            public Reply chat(List<Message> messages) {
                if (++calls[0] == 2) {
                    throw new IllegalStateException("Model call failed with HTTP 429: quota");
                }
                return new Reply(partial, 10, 5);
            }
        };

        Map<String, Object> log = new EscapeAgent(room, flaky, fakeVerifier(), 5, line -> { })
                .run(workspace);

        assertEquals("error", log.get("status"));
        assertEquals("Model call failed with HTTP 429: quota", log.get("error"));
        assertEquals(false, log.get("escaped"));
        List<Object> attempts = (List<Object>) log.get("attempts");
        assertEquals(2, attempts.size());
        assertEquals("Model call failed with HTTP 429: quota",
                ((Map<String, Object>) attempts.get(1)).get("error"));
        assertEquals("completed", new EscapeAgent(room, new TestSupport.ScriptedModel(
                TestSupport.resource("attempt2.txt")), fakeVerifier(), 5, line -> { })
                .run(TestSupport.freshWorkspace(room)).get("status"));
    }
}
