package io.github.eschoe.llmragapi.domain.chat;

import io.github.eschoe.llmragapi.client.LlmContextClient;
import io.github.eschoe.llmragapi.dao.EmbeddingQueryDao;
import io.github.eschoe.llmragapi.domain.LlmConstants;
import io.github.eschoe.llmragapi.util.ChatbotUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Service
public class ChatImplService implements ChatService {

    @Value("${APP_EMBEDDING_MODEL:text-embedding-3-small}")
    private String embeddingModel;

    private final ChatbotUtil chatbotUtil;
    private final LlmContextClient llmContextClient;
    private final EmbeddingQueryDao embeddingQueryDao;

    public ChatImplService(ChatbotUtil chatbotUtil, LlmContextClient llmContextClient, EmbeddingQueryDao embeddingQueryDao) {
        this.chatbotUtil = chatbotUtil;
        this.llmContextClient = llmContextClient;
        this.embeddingQueryDao = embeddingQueryDao;
    }

    @Override
    public Mono<String> chat(ChatBody chatBody) {

        String llmQuery = chatbotUtil.opt(chatBody.query());
        String llmProvider = !StringUtils.hasText(chatBody.provider()) ? LlmConstants.DEFAULT_PROVIDER : chatBody.provider();
        String llmModel = chatbotUtil.chooseModel(llmProvider, chatBody.model());

        int k = (chatBody.topK() != null && chatBody.topK() > 0) ? chatBody.topK() : 5;

        if (llmQuery.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));

        Mono<float[]> embedMono = (chatBody.embedding() != null && chatBody.embedding().length > 0)
                ? Mono.just(chatBody.embedding())
                : llmContextClient.embed(embeddingModel, llmQuery);

        return embedMono
            .flatMapMany(embed -> embeddingQueryDao.topKByCosine(embed, k))
            .collectList()
            .flatMap(embedRow -> {

                String _context = embedRow.stream()
                        .map(row -> "- " + chatbotUtil.safeSnippet(row.getContent()))
                        .collect(Collectors.joining("\n"));

                String systemPrompt = LlmConstants.SYSTEM_PROMPT;
                String userPromprt = "QUESTION:\n" + llmQuery + "\n\nCONTEXT:\n" + _context;

                return llmContextClient.chat(llmProvider, llmModel, systemPrompt, userPromprt);

            });

    }

}
