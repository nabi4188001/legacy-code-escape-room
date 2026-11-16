package com.rustybank.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EditParserTest {

    @Test
    void readsPlanFilesAndDeletes() {
        String reply = "PLAN: swap double for BigDecimal.\n\n"
                + "=== FILE: a/B.java ===\nclass B {\n}\n=== END FILE ===\n\n"
                + "=== DELETE: a/Old.java ===\n";
        EditParser.Parsed parsed = EditParser.parse(reply);
        assertEquals("swap double for BigDecimal.", parsed.plan());
        assertEquals(2, parsed.edits().size());
        assertEquals("a/B.java", parsed.edits().get(0).path());
        assertEquals("class B {\n}\n", parsed.edits().get(0).content());
        assertEquals("a/Old.java", parsed.edits().get(1).path());
        assertTrue(parsed.edits().get(1).isDelete());
    }

    @Test
    void toleratesMarkdownFencesAndWindowsLineEndings() {
        String reply = "PLAN: x\r\n=== FILE: a/B.java ===\r\n```java\r\nclass B {}\r\n```\r\n"
                + "=== END FILE ===\r\n";
        EditParser.Parsed parsed = EditParser.parse(reply);
        assertEquals("class B {}\n", parsed.edits().get(0).content());
    }

    @Test
    void aReplyWithNoBlocksIsAllPlan() {
        EditParser.Parsed parsed = EditParser.parse("I am not sure what to do.");
        assertEquals("I am not sure what to do.", parsed.plan());
        assertTrue(parsed.edits().isEmpty());
    }
}
