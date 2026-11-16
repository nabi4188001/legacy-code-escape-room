package com.rustybank.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MavenVerifierTest {

    @Test
    void readsSurefireReports() throws Exception {
        Room room = Room.load(TestSupport.repoRoot(), "01-penny-room");
        Path reports = Files.createTempDirectory("surefire-");
        Files.writeString(reports.resolve("TEST-golden.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="g" tests="2" failures="0" errors="0">
                  <testcase name="a" classname="com.rustybank.rooms.penny.PennyRoomGoldenMasterTest" time="0.01"/>
                  <testcase name="b" classname="com.rustybank.rooms.penny.PennyRoomGoldenMasterTest" time="0.01"/>
                </testsuite>
                """);
        Files.writeString(reports.resolve("TEST-escape.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="e" tests="3" failures="1" errors="1">
                  <testcase name="ok" classname="com.rustybank.rooms.penny.PennyRoomEscapeTest" time="0.01"/>
                  <testcase name="wrongCent" classname="com.rustybank.rooms.penny.PennyRoomEscapeTest" time="0.01">
                    <failure message="expected: &lt;0.28&gt; but was: &lt;0.27&gt;" type="org.opentest4j.AssertionFailedError">trace</failure>
                  </testcase>
                  <testcase name="blewUp" classname="com.rustybank.rooms.penny.PennyRoomEscapeTest" time="0.01">
                    <error message="INSUFFICIENT FUNDS" type="java.lang.IllegalStateException">trace</error>
                  </testcase>
                  <testcase name="other" classname="com.example.Unrelated" time="0.01"/>
                </testsuite>
                """);

        VerifyResult result = MavenVerifier.readReports(reports, List.of(), "", room);

        assertTrue(result.compiled());
        assertEquals(2, result.goldenPassed());
        assertEquals(2, result.goldenTotal());
        assertEquals(1, result.escapePassed());
        assertEquals(3, result.escapeTotal());
        assertEquals(2, result.failures().size());
        assertEquals("expected: <0.28> but was: <0.27>", result.failures().get(0).message());
        assertEquals("golden 2/2, escape 1/3", result.headline());
        assertFalse(result.escaped());
    }

    @Test
    void keepsCompilerErrorsFromTheBuildOutput() {
        String kept = MavenVerifier.relevantLines(List.of(
                "[INFO] Compiling 8 source files",
                "[ERROR] /x/Money.java:[12,9] cannot find symbol",
                "[ERROR] -> [Help 1]",
                "[ERROR] To see the full stack trace, re-run Maven with the -e switch."));
        assertEquals("[ERROR] /x/Money.java:[12,9] cannot find symbol", kept);
    }
}
