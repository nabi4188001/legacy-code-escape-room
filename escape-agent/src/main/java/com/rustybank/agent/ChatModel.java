package com.rustybank.agent;

import java.util.List;

/** Anything that can answer a conversation. Real models and scripted test doubles alike. */
public interface ChatModel {

    /** Name shown in run logs and on the leaderboard. */
    String name();

    Reply chat(List<Message> messages);

    record Message(String role, String content) {
        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }
    }

    record Reply(String content, long promptTokens, long completionTokens) {
    }
}
