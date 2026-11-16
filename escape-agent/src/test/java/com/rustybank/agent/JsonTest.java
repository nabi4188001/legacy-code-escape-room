package com.rustybank.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void readsAChatCompletion() {
        String response = """
                {"id":"x","choices":[{"index":0,"message":{"role":"assistant",
                "content":"PLAN: fix \\"it\\"\\nline two \\u00e9"}}],
                "usage":{"prompt_tokens":1234,"completion_tokens":56,"cost":0.5e-3}}
                """;
        Object json = Json.parse(response);
        assertEquals("PLAN: fix \"it\"\nline two é",
                Json.at(json, "choices", 0, "message", "content"));
        assertEquals(1234L, Json.at(json, "usage", "prompt_tokens"));
        assertEquals(0.5e-3, Json.at(json, "usage", "cost"));
        assertEquals(null, Json.at(json, "choices", 3, "message"));
    }

    @Test
    void whatItWritesItCanReadBack() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("text", "tab\there \"quoted\" back\\slash\nnewline");
        value.put("count", 3L);
        value.put("ok", true);
        value.put("nothing", null);
        value.put("list", List.of("a", 2L, List.of(), Map.of()));
        assertEquals(value, Json.parse(Json.write(value)));
    }

    @Test
    void rejectsBrokenJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": }"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": 1} extra"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"open"));
    }
}
