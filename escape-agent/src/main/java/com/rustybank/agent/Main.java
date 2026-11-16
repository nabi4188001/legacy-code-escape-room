package com.rustybank.agent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Command line entry point.
 *
 * <pre>
 * LLM_API_KEY=... java -jar escape-agent.jar --room 01-penny-room
 * </pre>
 *
 * Settings come from the environment so the same build can run any provider:
 * LLM_BASE_URL, LLM_MODEL, LLM_API_KEY.
 */
public final class Main {

    static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai";
    static final String DEFAULT_MODEL = "gemini-3-flash-preview";

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String roomId = "01-penny-room";
        Path repo = Path.of(".");
        int maxAttempts = 5;
        boolean listModels = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--room" -> roomId = args[++i];
                case "--repo" -> repo = Path.of(args[++i]);
                case "--max-attempts" -> maxAttempts = Integer.parseInt(args[++i]);
                case "--list-models" -> listModels = true;
                default -> {
                    System.err.println("Unknown option: " + args[i]);
                    System.err.println("Usage: --room <id> [--repo <path>] [--max-attempts <n>]"
                            + " | --list-models");
                    System.exit(2);
                }
            }
        }

        String baseUrl = env("LLM_BASE_URL", DEFAULT_BASE_URL);
        String modelName = env("LLM_MODEL", DEFAULT_MODEL);
        String apiKey = env("LLM_API_KEY", "");
        if (apiKey.isBlank()) {
            System.err.println("Set LLM_API_KEY to your model provider's API key.");
            System.exit(2);
        }
        OpenAiCompatibleModel model = new OpenAiCompatibleModel(baseUrl, modelName, apiKey);
        if (listModels) {
            model.listModels().forEach(System.out::println);
            return;
        }

        repo = repo.toAbsolutePath().normalize();
        Room room = Room.load(repo, roomId);
        Path sandbox = Files.createTempDirectory("escape-room-");
        Workspace workspace = Workspace.copyOf(repo, room, sandbox);

        System.out.println("Room:    " + room.name());
        System.out.println("Model:   " + modelName);
        System.out.println("Sandbox: " + sandbox);

        EscapeAgent agent = new EscapeAgent(room, model, new MavenVerifier(room), maxAttempts,
                System.out::println);
        Map<String, Object> log = agent.run(workspace);

        String stamp = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String slug = modelName.replaceAll("[^A-Za-z0-9.-]+", "-");
        Path out = repo.resolve("runs").resolve(roomId).resolve(stamp + "-" + slug + ".json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, Json.write(log) + "\n");
        System.out.println("Run log: " + repo.relativize(out));

        System.exit(Boolean.TRUE.equals(log.get("escaped")) ? 0 : 1);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
