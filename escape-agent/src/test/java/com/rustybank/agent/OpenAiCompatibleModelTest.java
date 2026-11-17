package com.rustybank.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleModelTest {

    @Test
    void foldsTheSystemMessageIntoTheFirstUserMessage() {
        List<ChatModel.Message> folded = OpenAiCompatibleModel.foldSystemIntoUser(List.of(
                ChatModel.Message.system("Be brief."),
                ChatModel.Message.user("Fix the bank.")));
        assertEquals(1, folded.size());
        assertEquals("user", folded.get(0).role());
        assertEquals("Be brief.\n\n---\n\nFix the bank.", folded.get(0).content());
    }

    @Test
    void leavesConversationsWithoutASystemMessageAlone() {
        List<ChatModel.Message> messages = List.of(ChatModel.Message.user("Hello"));
        assertEquals(messages, OpenAiCompatibleModel.foldSystemIntoUser(messages));
    }

    @Test
    void aLoneSystemMessageBecomesAUserMessage() {
        List<ChatModel.Message> folded = OpenAiCompatibleModel.foldSystemIntoUser(
                List.of(ChatModel.Message.system("Only this.")));
        assertEquals(List.of(ChatModel.Message.user("Only this.")), folded);
    }
}
