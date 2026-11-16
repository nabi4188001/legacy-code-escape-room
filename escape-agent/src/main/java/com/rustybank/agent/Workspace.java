package com.rustybank.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The agent's sandbox: a private copy of the room's module. The agent can only write
 * where the room allows, and the original repository is never touched.
 */
public final class Workspace {

    private final Path root;
    private final Room room;

    private Workspace(Path root, Room room) {
        this.root = root;
        this.room = room;
    }

    public static Workspace copyOf(Path repoRoot, Room room, Path target) throws IOException {
        Path source = repoRoot.resolve(room.module());
        Path destination = target.resolve(room.module());
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                if (dir.getFileName().toString().equals("target")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(destination.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.copy(file, destination.resolve(source.relativize(file).toString()));
                return FileVisitResult.CONTINUE;
            }
        });
        return new Workspace(target, room);
    }

    public Path root() {
        return root;
    }

    public Path moduleDir() {
        return root.resolve(room.module());
    }

    public record Applied(List<Edit> accepted, Map<String, String> rejected,
                          Map<String, String> before) {
    }

    /** Applies what the room allows and reports, per path, why the rest was refused. */
    public Applied apply(List<Edit> edits) {
        List<Edit> accepted = new ArrayList<>();
        Map<String, String> rejected = new LinkedHashMap<>();
        Map<String, String> before = new LinkedHashMap<>();
        for (Edit edit : edits) {
            String refusal = refusalFor(edit.path());
            if (refusal != null) {
                rejected.put(edit.path(), refusal);
                continue;
            }
            Path file = root.resolve(edit.path());
            try {
                before.put(edit.path(), Files.exists(file) ? Files.readString(file) : null);
                if (edit.isDelete()) {
                    Files.deleteIfExists(file);
                } else {
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, edit.content());
                }
                accepted.add(edit);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return new Applied(accepted, rejected, before);
    }

    private String refusalFor(String path) {
        if (path.isBlank() || path.contains("\\") || path.startsWith("/")
                || path.contains("..") || path.contains(":")) {
            return "not a valid relative path";
        }
        if (room.locked().contains(path)) {
            return "this file is locked";
        }
        if (!path.endsWith(".java")) {
            return "only .java files may be changed";
        }
        for (String prefix : room.editable()) {
            if (path.startsWith(prefix) && root.resolve(path).normalize().startsWith(root)) {
                return null;
            }
        }
        return "outside the editable area " + room.editable();
    }

    /** Every editable file, path to content, in a stable order. */
    public Map<String, String> editableFiles() {
        Map<String, String> files = new TreeMap<>();
        for (String prefix : room.editable()) {
            Path dir = root.resolve(prefix);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    if (relative.endsWith(".java") && !room.locked().contains(relative)) {
                        files.put(relative, Files.readString(file));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files;
    }

    public Map<String, String> lockedFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        for (String path : room.locked()) {
            try {
                files.put(path, Files.readString(root.resolve(path)));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files;
    }

    /**
     * Checks the locked files against the fingerprints taken from the original
     * repository, so the agent cannot rewrite the fingerprints either.
     */
    public List<String> brokenLocks() {
        List<String> broken = new ArrayList<>();
        for (String line : room.lockFile().split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.trim().split("\\s+", 2);
            String path = parts[1].startsWith("*") ? parts[1].substring(1) : parts[1];
            Path file = root.resolve(path);
            if (!Files.isRegularFile(file) || !sha256(file).equalsIgnoreCase(parts[0])) {
                broken.add(path);
            }
        }
        return broken;
    }

    private static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
