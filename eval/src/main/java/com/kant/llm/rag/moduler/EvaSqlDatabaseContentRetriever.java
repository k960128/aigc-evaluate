package com.kant.llm.rag.moduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.List;

@Slf4j
public class EvaSqlDatabaseContentRetriever implements DocumentRetriever {
    @Override
    public List<Document> retrieve(Query query) {
        log.info("retrieve query: {}", query);
        return List.of();
    }
}
