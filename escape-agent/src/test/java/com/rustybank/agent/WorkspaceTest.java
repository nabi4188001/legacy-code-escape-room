package com.rustybank.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkspaceTest {

    private static final String MAIN = "rusty-bank/src/main/java/com/rustybank/";
    private static final String ESCAPE_TEST =
            "rusty-bank/src/test/java/com/rustybank/rooms/penny/PennyRoomEscapeTest.java";

    @Test
    void theSandboxIsACopyWithIntactLocks() throws Exception {
        Room room = Room.load(TestSupport.repoRoot(), "01-penny-room");
        Workspace workspace = TestSupport.freshWorkspace(room);
        assertTrue(workspace.brokenLocks().isEmpty());
        assertTrue(workspace.editableFiles().containsKey(MAIN + "legacy/MoneyUtil.java"));
        assertFalse(workspace.editableFiles().containsKey(MAIN + "TellerTerminal.java"));
        assertEquals(3, workspace.lockedFiles().size());
    }

    @Test
    void editsInsideTheEditableAreaAreApplied() throws Exception {
        Room room = Room.load(TestSupport.repoRoot(), "01-penny-room");
        Workspace workspace = TestSupport.freshWorkspace(room);
        Workspace.Applied applied = workspace.apply(List.of(
                new Edit(MAIN + "Money.java", "package com.rustybank;\nclass Money {}\n"),
                new Edit(MAIN + "legacy/MoneyUtil.java", null)));
        assertEquals(2, applied.accepted().size());
        assertTrue(Files.exists(workspace.root().resolve(MAIN + "Money.java")));
        assertFalse(Files.exists(workspace.root().resolve(MAIN + "legacy/MoneyUtil.java")));
        assertEquals(null, applied.before().get(MAIN + "Money.java"));
        assertTrue(applied.before().get(MAIN + "legacy/MoneyUtil.java").contains("MoneyUtil"));
    }

    @Test
    void theAgentCannotCheat() throws Exception {
        Room room = Room.load(TestSupport.repoRoot(), "01-penny-room");
        Workspace workspace = TestSupport.freshWorkspace(room);
        String original = Files.readString(workspace.root().resolve(ESCAPE_TEST));
        Workspace.Applied applied = workspace.apply(List.of(
                new Edit(ESCAPE_TEST, "// all green now\n"),
                new Edit(MAIN + "TellerTerminal.java", "interface TellerTerminal {}\n"),
                new Edit("rusty-bank/pom.xml", "<project/>\n"),
                new Edit("rooms/01-penny-room/LOCKED.sha256", "\n"),
                new Edit(MAIN + "../../../../../escape.java", "class X {}\n"),
                new Edit("/tmp/escape.java", "class X {}\n"),
                new Edit(ESCAPE_TEST, null)));
        assertTrue(applied.accepted().isEmpty());
        assertEquals(6, applied.rejected().size());
        assertEquals(original, Files.readString(workspace.root().resolve(ESCAPE_TEST)));
        assertTrue(workspace.brokenLocks().isEmpty());
    }

    @Test
    void aTamperedLockedFileIsDetected() throws Exception {
        Room room = Room.load(TestSupport.repoRoot(), "01-penny-room");
        Workspace workspace = TestSupport.freshWorkspace(room);
        Path test = workspace.root().resolve(ESCAPE_TEST);
        Files.writeString(test, Files.readString(test) + "// sneaky\n");
        assertEquals(List.of(ESCAPE_TEST), workspace.brokenLocks());
    }

    @Test
    void theTimeTrapRoomIsReadyForTheAgent() throws Exception {
        Room room = Room.load(TestSupport.repoRoot(), "02-time-trap");
        Workspace workspace = TestSupport.freshWorkspace(room);
        assertEquals("rusty-payments", room.module());
        assertTrue(workspace.brokenLocks().isEmpty());
        assertEquals(3, workspace.lockedFiles().size());
        assertTrue(workspace.editableFiles().containsKey(
                "rusty-payments/src/main/java/com/rustypayments/legacy/DateUtil.java"));
        assertFalse(room.brief().contains("YYYY"), "hints must not leak into the brief");
        Workspace.Applied applied = workspace.apply(List.of(new Edit(
                "rusty-payments/src/main/java/com/rustypayments/PaymentCalendar.java",
                "interface PaymentCalendar {}\n")));
        assertTrue(applied.accepted().isEmpty());
    }

    @Test
    void everyRoomIsReadyForTheAgent() throws Exception {
        List<String> roomIds = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> dirs = Files.list(TestSupport.repoRoot().resolve("rooms"))) {
            dirs.filter(Files::isDirectory).forEach(d -> roomIds.add(d.getFileName().toString()));
        }
        assertTrue(roomIds.size() >= 4);
        for (String id : roomIds) {
            Room room = Room.load(TestSupport.repoRoot(), id);
            Workspace workspace = TestSupport.freshWorkspace(room);
            assertTrue(workspace.brokenLocks().isEmpty(), id);
            assertEquals(3, workspace.lockedFiles().size(), id);
            assertFalse(workspace.editableFiles().isEmpty(), id);
            assertFalse(room.brief().contains("Hints for humans"), id);
        }
    }
}
