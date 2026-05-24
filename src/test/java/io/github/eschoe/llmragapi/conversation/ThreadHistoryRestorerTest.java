package io.github.eschoe.llmragapi.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eschoe.llmragapi.chat.history.ChatHistoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThreadHistoryRestorerTest {

    private ChatHistoryStore chatHistoryStore;
    private ThreadRepository repository;
    private ThreadHistoryRestorer restorer;

    @BeforeEach
    void setUp() {
        chatHistoryStore = mock(ChatHistoryStore.class);
        repository = mock(ThreadRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        restorer = new ThreadHistoryRestorer(chatHistoryStore, objectMapper, repository);

        // repository.save 는 받은 thread 를 그대로 반환
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void loadsHistoryMessagesIntoNewThread() {
        when(chatHistoryStore.recent(anyString(), anyLong())).thenReturn(Flux.just(
                "{\"role\":\"user\",\"content\":\"안녕\",\"timestamp\":\"2026-05-23T10:00:00\"}",
                "{\"role\":\"assistant\",\"content\":\"반가워요\",\"timestamp\":\"2026-05-23T10:00:01\"}"
        ));

        ConversationThread thread = restorer.loadThreadFromHistory("t1", "s1").block();

        assertThat(thread).isNotNull();
        assertThat(thread.getSessionId()).isEqualTo("s1");
        assertThat(thread.getMessages()).hasSize(2);
        assertThat(thread.getMessages().get(0).getRole())
                .isEqualTo(ConversationThread.Message.MessageRole.USER);
        assertThat(thread.getMessages().get(0).getContent()).isEqualTo("안녕");
        assertThat(thread.getMessages().get(1).getRole())
                .isEqualTo(ConversationThread.Message.MessageRole.ASSISTANT);
    }

    @Test
    void skipsUnparseableMessages() {
        when(chatHistoryStore.recent(anyString(), anyLong())).thenReturn(Flux.just(
                "{\"role\":\"user\",\"content\":\"ok\",\"timestamp\":\"2026-05-23T10:00:00\"}",
                "not valid json{{{"
        ));

        ConversationThread thread = restorer.loadThreadFromHistory("t1", "s1").block();

        assertThat(thread).isNotNull();
        assertThat(thread.getMessages()).hasSize(1); // 깨진 메시지는 skip
        assertThat(thread.getMessages().get(0).getContent()).isEqualTo("ok");
    }

    @Test
    void unknownRoleFallsBackToSystem() {
        when(chatHistoryStore.recent(anyString(), anyLong())).thenReturn(Flux.just(
                "{\"role\":\"weirdo\",\"content\":\"x\",\"timestamp\":\"2026-05-23T10:00:00\"}"
        ));

        ConversationThread thread = restorer.loadThreadFromHistory("t1", "s1").block();

        assertThat(thread.getMessages()).hasSize(1);
        assertThat(thread.getMessages().get(0).getRole())
                .isEqualTo(ConversationThread.Message.MessageRole.SYSTEM);
    }

    @Test
    void emptyHistoryCreatesEmptyThread() {
        when(chatHistoryStore.recent(anyString(), anyLong())).thenReturn(Flux.empty());

        ConversationThread thread = restorer.loadThreadFromHistory("t1", "s1").block();

        assertThat(thread).isNotNull();
        assertThat(thread.getSessionId()).isEqualTo("s1");
        assertThat(thread.getMessages()).isEmpty();
    }
}
