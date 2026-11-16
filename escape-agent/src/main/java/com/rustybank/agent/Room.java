package com.rustybank.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** One room of the escape room, as described by its {@code room.json}. */
public record Room(String id, String name, String module, List<String> editable,
                   List<String> locked, String goldenTests, String escapeTests,
                   String brief, String lockFile) {

    public static Room load(Path repoRoot, String roomId) throws IOException {
        Path dir = repoRoot.resolve("rooms").resolve(roomId);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("No such room: " + roomId);
        }
        Object json = Json.parse(Files.readString(dir.resolve("room.json")));
        return new Room(
                text(json, "id"),
                text(json, "name"),
                text(json, "module"),
                strings(Json.at(json, "editable")),
                strings(Json.at(json, "locked")),
                text(json, "goldenTests"),
                text(json, "escapeTests"),
                Files.readString(dir.resolve("ROOM.md")),
                Files.readString(dir.resolve("LOCKED.sha256")));
    }

    private static String text(Object json, String key) {
        if (Json.at(json, key) instanceof String s) {
            return s;
        }
        throw new IllegalArgumentException("room.json is missing \"" + key + "\"");
    }

    private static List<String> strings(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
        }
        return List.copyOf(out);
    }
}
