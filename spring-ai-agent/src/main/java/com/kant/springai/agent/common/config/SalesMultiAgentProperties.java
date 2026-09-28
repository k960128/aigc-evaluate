package com.kant.springai.agent.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 销售 Supervisor 多 Agent Demo 配置。
 *
 * <p>API Key 必须通过外部配置提供，避免把密钥写入代码仓库。</p>
 */
@Component
@ConfigurationProperties(prefix = "sales-agent.multi-agent")
public class SalesMultiAgentProperties {

    /** OpenAI 兼容接口的 API Key。 */
    private String apiKey = "";

    /** OpenAI 兼容接口地址。 */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode";

    /** Supervisor 和 Worker 共享的底层模型名称。 */
    private String model = "qwen3.8-max";

    /** 单次请求最多允许 Supervisor 交接给 Worker 的次数。 */
    private int maxDelegations = 6;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxDelegations() {
        return maxDelegations;
    }

    public void setMaxDelegations(int maxDelegations) {
        this.maxDelegations = maxDelegations;
    }
}
