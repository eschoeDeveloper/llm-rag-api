package io.github.eschoe.llmragapi.domain.ask;

import io.github.eschoe.llmragapi.client.LlmContextClient;
import io.github.eschoe.llmragapi.domain.LlmConstants;
import io.github.eschoe.llmragapi.util.ChatbotUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Service
public class AskImplService implements AskService {

    private final ChatbotUtil chatbotUtil;
    private final LlmContextClient llmContextClient;

    public AskImplService(ChatbotUtil chatbotUtil, LlmContextClient llmContextClient) {
        this.chatbotUtil = chatbotUtil;
        this.llmContextClient = llmContextClient;
    }

    @Override
    public Mono<String> ask(AskBody ask) {

        String llmQuery = chatbotUtil.opt(ask.query());
        String llmProvider = !StringUtils.hasText(ask.provider()) ? LlmConstants.DEFAULT_PROVIDER : ask.provider();
        String llmModel = chatbotUtil.chooseModel(llmProvider, ask.model());

        String systemPrompt = LlmConstants.SYSTEM_PROMPT;

        if (llmQuery.isBlank()) return Mono.error(new IllegalArgumentException("query is required"));

        return llmContextClient.chat(llmProvider, llmModel, systemPrompt, llmQuery);

    }

}
