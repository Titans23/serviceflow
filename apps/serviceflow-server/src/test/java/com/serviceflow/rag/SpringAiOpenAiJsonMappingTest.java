package com.serviceflow.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.api.OpenAiApi;

class SpringAiOpenAiJsonMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsNestedEmbeddingElementToSpringAiType() throws Exception {
        var type = objectMapper
                .getTypeFactory()
                .constructParametricType(OpenAiApi.EmbeddingList.class, OpenAiApi.Embedding.class);

        OpenAiApi.EmbeddingList<?> response = objectMapper.readValue(
                """
                {"object":"list","data":[{"object":"embedding","index":0,"embedding":[0.1,0.2]}],
                 "model":"text-embedding-v4","usage":{"prompt_tokens":1,"total_tokens":1}}
                """,
                type);

        assertThat(response.data()).hasSize(1);
        assertThat(response.data().getFirst()).isInstanceOf(OpenAiApi.Embedding.class);
    }

    @Test
    void mapsNestedChatChoiceToSpringAiType() throws Exception {
        OpenAiApi.ChatCompletion response = objectMapper.readValue(
                """
                {"model":"qwen-plus","id":"chat-1","choices":[{"message":{"content":"OK","role":"assistant"},
                 "index":0,"finish_reason":"stop"}],"created":1,"object":"chat.completion"}
                """,
                OpenAiApi.ChatCompletion.class);

        assertThat(response.choices()).hasSize(1);
        assertThat(response.choices().getFirst()).isInstanceOf(OpenAiApi.ChatCompletion.Choice.class);
    }
}
