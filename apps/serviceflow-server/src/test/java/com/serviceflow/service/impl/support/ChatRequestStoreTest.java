package com.serviceflow.service.impl.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.serviceflow.exception.ErrorCode;
import com.serviceflow.exception.ServiceFlowException;
import com.serviceflow.mapper.ChatMapper;
import com.serviceflow.model.ChatModels;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatRequestStoreTest {
    private ChatMapper chats;
    private ChatRequestStore store;

    @BeforeEach
    void setUp() {
        chats = mock(ChatMapper.class);
        store = new ChatRequestStore(chats);
    }

    @Test
    void firstRequestIsClaimedAndUserMessageAttached() {
        when(chats.insertRequest(9L, "request-1")).thenReturn(1);

        var result = store.begin(9L, "request-1", "你好");

        assertThat(result.started()).isTrue();
        assertThat(result.completed()).isNull();
        verify(chats).insertMessage(anyString(), eq(9L), isNull(), eq("USER"), eq("你好"), isNull(), eq("{}"));
        verify(chats).attachUserMessage(eq(9L), eq("request-1"), anyString());
    }

    @Test
    void completedRequestIsReplayedWithoutWritingAgain() {
        var message = new ChatModels.Message("a-1", "ASSISTANT", "已完成", "CHAT", "{}", Instant.now());
        when(chats.insertRequest(9L, "request-2")).thenReturn(0);
        when(chats.request(9L, "request-2"))
                .thenReturn(new ChatMapper.RequestRow("COMPLETED", "u-1", "a-1", Instant.now()));
        when(chats.completed(9L, "request-2")).thenReturn(message);

        var result = store.begin(9L, "request-2", "重复提交");

        assertThat(result.started()).isFalse();
        assertThat(result.completed()).isSameAs(message);
        verify(chats, never()).restartRequest(anyLong(), anyString(), any());
    }

    @Test
    void staleRequestCanBeReclaimedAndFreshRequestIsRejected() {
        when(chats.insertRequest(9L, "request-3")).thenReturn(0);
        when(chats.request(9L, "request-3"))
                .thenReturn(new ChatMapper.RequestRow("PROCESSING", "u-1", null, Instant.now()));
        when(chats.restartRequest(eq(9L), eq("request-3"), any())).thenReturn(1);
        assertThat(store.begin(9L, "request-3", "接管").started()).isTrue();

        when(chats.restartRequest(eq(9L), eq("request-4"), any())).thenReturn(0);
        assertThatThrownBy(() -> store.begin(9L, "request-4", "并发"))
                .isInstanceOf(ServiceFlowException.class)
                .satisfies(error -> {
                    var exception = (ServiceFlowException) error;
                    assertThat(exception.code()).isEqualTo(ErrorCode.REQUEST_IN_PROGRESS);
                    assertThat(exception.retryable()).isTrue();
                });
    }

    @Test
    void completionPersistsAssistantAndUpdatesStateAtomically() {
        when(chats.completeRequest(9L, "request-5", "a-5")).thenReturn(1);

        store.complete(9L, "request-5", "a-5", "答案", "CHAT", "{}");

        verify(chats).insertMessage("a-5", 9L, "request-5", "ASSISTANT", "答案", "CHAT", "{}");
        verify(chats).completeRequest(9L, "request-5", "a-5");
    }
}
