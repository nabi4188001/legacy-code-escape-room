package com.rustybank.agent;

import java.util.List;

/** What happened when the room's tests were run against the agent's current code. */
public record VerifyResult(List<String> brokenLocks, boolean compiled, int goldenPassed,
                           int goldenTotal, int escapePassed, int escapeTotal,
                           List<Failure> failures, String buildOutput) {

    public record Failure(String suite, String test, String message) {
    }

    public boolean escaped() {
        return brokenLocks.isEmpty() && compiled
                && goldenTotal > 0 && goldenPassed == goldenTotal
                && escapeTotal > 0 && escapePassed == escapeTotal;
    }

    public String headline() {
        if (!brokenLocks.isEmpty()) {
            return "locked files were modified: " + brokenLocks;
        }
        if (!compiled) {
            return "the code does not compile";
        }
        return "golden " + goldenPassed + "/" + goldenTotal
                + ", escape " + escapePassed + "/" + escapeTotal;
    }
}
