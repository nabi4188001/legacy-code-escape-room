package com.rustybank.agent;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Runs the room's real test suite with Maven and reads the Surefire reports. */
public final class MavenVerifier implements Verifier {

    private static final int OUTPUT_LINES_KEPT = 60;

    private final Room room;

    public MavenVerifier(Room room) {
        this.room = room;
    }

    @Override
    public VerifyResult verify(Workspace workspace) {
        List<String> brokenLocks = workspace.brokenLocks();
        Path module = workspace.moduleDir();
        Path reports = module.resolve("target").resolve("surefire-reports");
        deleteRecursively(reports);

        String output = runMaven(module);
        if (!Files.isDirectory(reports)) {
            return new VerifyResult(brokenLocks, false, 0, 0, 0, 0, List.of(), output);
        }
        return readReports(reports, brokenLocks, output, room);
    }

    private static String runMaven(Path module) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        ProcessBuilder builder = new ProcessBuilder(
                windows ? "mvn.cmd" : "mvn", "-B", "-q", "test");
        builder.directory(module.toFile());
        builder.redirectErrorStream(true);
        try {
            File log = File.createTempFile("escape-room-mvn", ".log");
            builder.redirectOutput(log);
            Process process = builder.start();
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return "Maven timed out after 10 minutes.";
            }
            List<String> lines = Files.readAllLines(log.toPath());
            Files.deleteIfExists(log.toPath());
            return relevantLines(lines);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not run Maven. Is mvn on the PATH?", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running Maven", e);
        }
    }

    /** Keeps the error lines, which is where compiler messages live. */
    static String relevantLines(List<String> lines) {
        List<String> errors = lines.stream()
                .filter(line -> line.startsWith("[ERROR]"))
                .filter(line -> !line.contains("-> [Help") && !line.contains("re-run Maven"))
                .collect(Collectors.toList());
        List<String> kept = errors.isEmpty() ? lines : errors;
        return String.join("\n", kept.subList(0, Math.min(kept.size(), OUTPUT_LINES_KEPT)));
    }

    static VerifyResult readReports(Path reports, List<String> brokenLocks, String output,
                                    Room room) {
        int goldenPassed = 0;
        int goldenTotal = 0;
        int escapePassed = 0;
        int escapeTotal = 0;
        List<VerifyResult.Failure> failures = new ArrayList<>();
        try (Stream<Path> files = Files.list(reports)) {
            List<Path> xmlFiles = files
                    .filter(f -> f.getFileName().toString().matches("TEST-.*\\.xml"))
                    .sorted().toList();
            for (Path xml : xmlFiles) {
                Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                        .parse(xml.toFile());
                NodeList cases = document.getElementsByTagName("testcase");
                for (int i = 0; i < cases.getLength(); i++) {
                    Element testCase = (Element) cases.item(i);
                    String suite = testCase.getAttribute("classname");
                    boolean golden = suite.equals(room.goldenTests());
                    boolean escape = suite.equals(room.escapeTests());
                    if (!golden && !escape) {
                        continue;
                    }
                    Element problem = firstChild(testCase, "failure");
                    if (problem == null) {
                        problem = firstChild(testCase, "error");
                    }
                    boolean passed = problem == null && firstChild(testCase, "skipped") == null;
                    if (golden) {
                        goldenTotal++;
                        goldenPassed += passed ? 1 : 0;
                    } else {
                        escapeTotal++;
                        escapePassed += passed ? 1 : 0;
                    }
                    if (problem != null) {
                        String message = problem.getAttribute("message");
                        if (message.isBlank()) {
                            message = problem.getAttribute("type");
                        }
                        failures.add(new VerifyResult.Failure(
                                golden ? "golden" : "escape",
                                testCase.getAttribute("name"), message));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the Surefire reports", e);
        }
        return new VerifyResult(brokenLocks, true, goldenPassed, goldenTotal, escapePassed,
                escapeTotal, failures, output);
    }

    private static Element firstChild(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
