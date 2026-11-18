package com.rustybank.agent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to any provider that speaks the OpenAI chat-completions protocol: Gemini, Groq,
 * OpenRouter, Cerebras, Mistral, a local Ollama. Swapping models is a change of settings.
 */
public final class OpenAiCompatibleModel implements ChatModel {

    private static final int MAX_TRIES = 8;

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final boolean systemAsUser;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public OpenAiCompatibleModel(String baseUrl, String model, String apiKey) {
        this(baseUrl, model, apiKey, false);
    }

    /**
     * @param systemAsUser for models that refuse a system message: the instructions are
     *                     put at the top of the first user message instead
     */
    public OpenAiCompatibleModel(String baseUrl, String model, String apiKey,
                                 boolean systemAsUser) {
        this.systemAsUser = systemAsUser;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return model;
    }

    @Override
    public Reply chat(List<Message> messages) {
        List<Object> wire = new ArrayList<>();
        for (Message message : systemAsUser ? foldSystemIntoUser(messages) : messages) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", message.role());
            m.put("content", message.content());
            wire.add(m);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", wire);

        String response = send("POST", "/chat/completions", Json.write(body));
        Object json = Json.parse(response);
        Object content = Json.at(json, "choices", 0, "message", "content");
        if (!(content instanceof String text)) {
            throw new IllegalStateException("The model sent no text. Response: " + shorten(response));
        }
        return new Reply(text, asLong(Json.at(json, "usage", "prompt_tokens")),
                asLong(Json.at(json, "usage", "completion_tokens")));
    }

    static List<Message> foldSystemIntoUser(List<Message> messages) {
        String separator = "\n\n---\n\n";
        List<String> instructions = new ArrayList<>();
        List<Message> folded = new ArrayList<>();
        for (Message message : messages) {
            if (message.role().equals("system")) {
                instructions.add(message.content());
            } else if (!instructions.isEmpty() && message.role().equals("user")) {
                folded.add(Message.user(String.join(separator, instructions) + separator
                        + message.content()));
                instructions.clear();
            } else {
                folded.add(message);
            }
        }
        if (!instructions.isEmpty()) {
            folded.add(0, Message.user(String.join(separator, instructions)));
        }
        return folded;
    }

    /** Model ids the provider offers, for when a configured model name turns out to be wrong. */
    public List<String> listModels() {
        Object json = Json.parse(send("GET", "/models", null));
        List<String> ids = new ArrayList<>();
        if (Json.at(json, "data") instanceof List<?> data) {
            for (Object entry : data) {
                if (Json.at(entry, "id") instanceof String id) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private String send(String method, String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json");
        HttpRequest request = (body == null
                ? builder.GET()
                : builder.method(method, HttpRequest.BodyPublishers.ofString(body))).build();

        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                if (attempt >= MAX_TRIES) {
                    throw new IllegalStateException("Could not reach " + baseUrl + ": " + e, e);
                }
                pause(5L * attempt);
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while calling the model", e);
            }
            int status = response.statusCode();
            if (status / 100 == 2) {
                return response.body();
            }
            boolean retryable = status == 429 || status >= 500;
            if (!retryable || attempt >= MAX_TRIES) {
                throw new IllegalStateException("Model call failed with HTTP " + status + ": "
                        + shorten(response.body()));
            }
            long wait = response.headers().firstValue("retry-after")
                    .map(OpenAiCompatibleModel::parseSeconds).orElse(20L * attempt);
            pause(Math.min(wait, 120));
        }
    }

    private static long parseSeconds(String value) {
        try {
            return (long) Math.ceil(Double.parseDouble(value.trim()));
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    private static void pause(long seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry", e);
        }
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0;
    }

    private static String shorten(String s) {
        return s.length() <= 600 ? s : s.substring(0, 600) + "...";
    }
}
