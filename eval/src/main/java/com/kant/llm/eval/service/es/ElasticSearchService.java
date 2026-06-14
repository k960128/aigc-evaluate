package com.kant.llm.eval.service.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.llm.eval.common.constant.EsDocumentChunk;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;


/**
 * ES 风险攻击特征索引服务
 */
@Slf4j
@Service
public class ElasticSearchService {


    private final ElasticsearchClient client;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String INDEX_NAME = "risk_attack_feature";

    private static final String FIELD_CONTENT = "featureText";

    public ElasticSearchService(ElasticsearchClient client) {
        this.client = client;
    }

    @PostConstruct
    public void init() {
        try {
            if (!indexExists(INDEX_NAME)) {
                createIndex();
                log.info("ES index [{}] created with IK analyzer!", INDEX_NAME);
            } else {
                log.info("ES index [{}] already exists, skip creation.", INDEX_NAME);
            }
        } catch (Exception e) {
            log.error("Failed to create ES index: {}", e.getMessage(), e);
        }
    }

    /**
     * 创建索引（IK 分词 + 停用词 + lowercase）
     */
    public void createIndex() throws Exception {
        // 1. 设置索引配置（settings）和 mapping
        String settingsAndMappingJson = """
                {
                   "settings": {
                     "number_of_shards": 1,
                     "number_of_replicas": 0
                   },
                   "mappings": {
                     "dynamic": false,
                     "properties": {
                       "featureId": {
                         "type": "long"
                       },
                       "riskDetailsId": {
                         "type": "long"
                       },
                       "categoryId": {
                         "type": "long"
                       },
                       "featureCode": {
                         "type": "keyword",
                         "ignore_above": 256
                       },
                       "featureText": {
                         "type": "text",
                         "analyzer": "ik_max_word",
                         "search_analyzer": "ik_smart",
                         "fields": {
                           "keyword": {
                             "type": "keyword",
                             "ignore_above": 512
                           }
                         }
                       },
                       "normalizedText": {
                         "type": "text",
                         "analyzer": "ik_max_word",
                         "search_analyzer": "ik_smart",
                         "fields": {
                           "keyword": {
                             "type": "keyword",
                             "ignore_above": 512
                           }
                         }
                       },
                       "featureType": {
                         "type": "keyword",
                         "ignore_above": 64
                       },
                       "polarity": {
                         "type": "keyword",
                         "ignore_above": 64
                       },
                       "riskLevel": {
                         "type": "integer"
                       },
                       "language": {
                         "type": "keyword",
                         "ignore_above": 32
                       },
                       "tags": {
                         "type": "text",
                         "analyzer": "ik_max_word",
                         "search_analyzer": "ik_smart",
                         "fields": {
                           "keyword": {
                             "type": "keyword",
                             "ignore_above": 512
                           }
                         }
                       },
                       "source": {
                         "type": "keyword",
                         "ignore_above": 128
                       },
                       "weight": {
                         "type": "scaled_float",
                         "scaling_factor": 1000
                       },
                       "contentHash": {
                         "type": "keyword",
                         "ignore_above": 128
                       },
                       "version": {
                         "type": "integer"
                       },
                       "status": {
                         "type": "integer"
                       },
                       "createTime": {
                         "type": "date",
                         "format": "yyyy-MM-dd HH:mm:ss||strict_date_optional_time||epoch_millis"
                       },
                       "updateTime": {
                         "type": "date",
                         "format": "yyyy-MM-dd HH:mm:ss||strict_date_optional_time||epoch_millis"
                       }
                     }
                   },
                   "aliases": {
                     "l2_risk_attack_feature": {}
                   }
                 }
                
                """;

        CreateIndexRequest request = CreateIndexRequest.of(b -> b
                .index(INDEX_NAME)
                .withJson(new StringReader(settingsAndMappingJson))
        );

        // 3. 创建索引
        client.indices().create(request);
    }

    /**
     * 单条存储
     */
    public void indexSingle(EsDocumentChunk doc) throws Exception {
        if (doc == null || doc.getId() == null) {
            throw new IllegalArgumentException("Document or ID cannot be null");
        }

        String docJson = mapper.writeValueAsString(doc);

        IndexRequest<EsDocumentChunk> request = IndexRequest.of(b -> b
                .index(INDEX_NAME)
                .id(doc.getId())
                .withJson(new StringReader(docJson))
                .refresh(Refresh.True)
        );

        client.index(request);
        log.debug("Indexed doc id={}", doc.getId());
    }

    /**
     * 根据文档 ID 删除单条 ES 索引。
     */
    public void deleteById(String id) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("Document ID cannot be null");
        }
        try {
            DeleteRequest request = DeleteRequest.of(b -> b
                    .index(INDEX_NAME)
                    .id(id)
                    .refresh(Refresh.True));
            client.delete(request);
            log.debug("Deleted doc id={}", id);
        } catch (ElasticsearchException ex) {
            if (ex.status() != 404) {
                throw ex;
            }
            log.debug("ES doc id={} not found, skip delete", id);
        }
    }

    public void bulkIndex(List<EsDocumentChunk> docs, String indexName) throws Exception {
        if (docs == null || docs.isEmpty()) return;

        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

        for (EsDocumentChunk doc : docs) {
            bulkBuilder.operations(op -> op
                    .index(idx -> idx
                            .index(indexName)
                            .id(doc.getId())
                            .document(doc)
                    )
            );
        }

        bulkBuilder.refresh(Refresh.True);

        BulkResponse response = client.bulk(bulkBuilder.build());
        if (response.errors()) {
            log.error("Bulk indexing completed with failures");
            response.items().forEach(item -> {
                if (item.error() != null) {
                    log.error("Failed to index doc {}: {}", item.id(), item.error().reason());
                }
            });
        } else {
            log.info("Successfully indexed {} documents", docs.size());
        }
    }

    /**
     * 批量存储
     */
    public void bulkIndex(List<EsDocumentChunk> docs) throws Exception {
        bulkIndex(docs, INDEX_NAME);
    }

    public boolean indexExists(String indexName) throws IOException {
        ExistsRequest request = ExistsRequest.of(b -> b.index(indexName));
        return client.indices().exists(request).value();
    }

    /**
     * 中文检索 - ik_max_word 建库 + ik_smart 检索
     */
    public List<EsDocumentChunk> searchByKeyword(String keyword) throws Exception {
        return searchByKeyword(keyword, INDEX_NAME);
    }

    public List<EsDocumentChunk> searchByKeyword(String keyword, String indexName) throws Exception {
        return searchByKeyword(keyword, 5, true, indexName);
    }

    /**
     * 中文检索：ik_max_word / ik_smart 切换
     */
    public List<EsDocumentChunk> searchByKeyword(String keyword, int size, boolean useSmartAnalyzer, String indexName) throws Exception {
        SearchRequest request = SearchRequest.of(b -> b
                .index(indexName)
                .query(q -> q
                        .match(m -> m
                                .field(FIELD_CONTENT)
                                .query(keyword)
                        )
                )
                .size(size)
        );

        SearchResponse<EsDocumentChunk> response = client.search(request, EsDocumentChunk.class);

        List<EsDocumentChunk> result = new ArrayList<>();
        response.hits().hits().forEach(hit -> {
            if (hit.source() != null) {
                result.add(hit.source());
            }
        });

        return result;
    }
}
