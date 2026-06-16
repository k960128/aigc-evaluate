package com.kant.ai.infra.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class RoutingEmbeddingService implements EmbeddingService {

    @Override
    public float[] embed(String text) {
        return getEmbeddingClient().embed(text);
    }

    @Override
    public float[] embed(String text, String modelId) {
        return getEmbeddingClient().embed(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return getEmbeddingClient().embed(texts);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts, String modelId) {
        return getEmbeddingClient().embed(texts);
    }

    @Override
    public EmbeddingModel getEmbeddingClient() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey("sk-3398a71a31f04728a19922dcbb8af1f9")
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                .build();

        OpenAiEmbeddingOptions embeddingOptions = OpenAiEmbeddingOptions.builder()
                .model("text-embedding-v4")
                .dimensions(1024)
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, embeddingOptions);
    }
}
