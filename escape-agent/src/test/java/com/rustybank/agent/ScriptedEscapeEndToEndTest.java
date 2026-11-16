package com.rustybank.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The whole machine, minus the language model: a scripted "model" first makes a partial
 * fix and tries to cheat, then delivers a real fix. The edits are applied in a sandbox
 * and judged by the real Maven build of Rusty Bank.
 */
@Tag("e2e")
class ScriptedEscapeEndToEndTest {

    @Test
    void aScriptedAgentEscapesThePennyRoom() throws Exception {
        Room room = Room.load(TestSupport.repoRoot(), "01-penny-room");
        Workspace workspace = TestSupport.freshWorkspace(room);
        TestSupport.ScriptedModel model = new TestSupport.ScriptedModel(
                TestSupport.resource("attempt1.txt"), TestSupport.resource("attempt2.txt"));
        List<String> story = new ArrayList<>();

        Map<String, Object> log = new EscapeAgent(room, model, new MavenVerifier(room), 5,
                line -> {
                    story.add(line);
                    System.out.println("[escape-room] " + line);
                }).run(workspace);

        assertEquals("golden 13/13, escape 0/9", Json.at(log, "baseline", "headline"));
        assertEquals("golden 13/13, escape 3/9", Json.at(log, "attempts", 0, "result", "headline"));
        assertEquals("golden 13/13, escape 9/9", Json.at(log, "attempts", 1, "result", "headline"));
        assertEquals(true, log.get("escaped"));
        assertEquals(2, log.get("attemptsUsed"));
        assertTrue(workspace.brokenLocks().isEmpty());
    }
}
