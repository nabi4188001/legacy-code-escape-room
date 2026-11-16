package com.rustybank.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class TestSupport {

    private TestSupport() {
    }

    /** The repository root, whether tests run from the module or from the root. */
    static Path repoRoot() {
        Path here = Path.of("").toAbsolutePath();
        return Files.isDirectory(here.resolve("rooms")) ? here : here.getParent();
    }

    static Workspace freshWorkspace(Room room) throws IOException {
        return Workspace.copyOf(repoRoot(), room, Files.createTempDirectory("escape-test-"));
    }

    static String resource(String name) throws IOException {
        try (InputStream in = TestSupport.class.getResourceAsStream("/scripted/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** A model that replays prepared replies and remembers what it was asked. */
    static final class ScriptedModel implements ChatModel {
        private final List<String> replies;
        final List<List<Message>> conversations = new ArrayList<>();

        ScriptedModel(String... replies) {
            this.replies = List.of(replies);
        }

        @Override
        public String name() {
            return "scripted";
        }

        @Override
        public Reply chat(List<Message> messages) {
            conversations.add(messages);
            String reply = replies.get(Math.min(conversations.size(), replies.size()) - 1);
            return new Reply(reply, 100, 50);
        }
    }
}
