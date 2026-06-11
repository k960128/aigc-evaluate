package com.kant.llm.eval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.kant.llm.eval.common.constant.EsDocumentChunk;
import com.kant.llm.eval.service.es.ElasticSearchService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class ElasticSearchTest {

    @Autowired
    private ElasticSearchService elasticSearchService;

    @Test
    public void testSearch(){
        try {
            List<EsDocumentChunk> documentChunks = elasticSearchService.searchByKeyword("八股文");
            log.info("ES检索返回内容: {}", documentChunks);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
