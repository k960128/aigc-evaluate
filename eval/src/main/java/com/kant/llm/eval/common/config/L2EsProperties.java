package com.kant.llm.eval.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * L2 Elasticsearch 配置。
 *
 * <p>用于真实关键词/BM25 召回和知识库索引同步。
 * 当前索引初始化脚本要求 ES 8.x 并安装 IK 分词插件。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.l2.es")
public class L2EsProperties {

    /** ES HTTP 地址列表，例如 http://127.0.0.1:9200。 */
    private List<String> uris = new ArrayList<>(List.of("http://127.0.0.1:9200"));

    /** ES 用户名，未开启认证时可为空。 */
    private String username;

    /** ES 密码，未开启认证时可为空。 */
    private String password;

    /** L2 攻击特征索引名，对应 doc/sql/es 下的初始化脚本。 */
    private String indexName = "l2_risk_attack_feature_v1";

    /** 连接超时时间，单位毫秒；真实召回失败时外层会降级保留另一条召回链路。 */
    private int connectTimeout = 3000;

    /** Socket 读写超时时间，单位毫秒；避免 ES 慢查询长期占用评测线程。 */
    private int socketTimeout = 10000;
}
