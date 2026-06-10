package com.kant.llm.eval.service.l2.client;

import com.kant.llm.eval.service.l2.model.L2RecallRequest;
import com.kant.llm.eval.service.l2.model.L2RecallResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * L2 召回降级客户端。
 *
 * <p>当前不强依赖真实 ES/Milvus 配置，因此默认返回空召回结果，
 * 让主评测链路可以先完成 L2 节点闭环。</p>
 *
 * <p>当 {@code app.l2.mock-recall-enabled=false} 时注册该 Bean。
 * 它表示“没有任何召回证据”，不是“召回服务异常”：返回结果中的 degraded=true
 * 会进入 L2 低风险/降级日志分支，保证应用在没有外部检索系统时仍能启动。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.l2", name = "mock-recall-enabled", havingValue = "false")
public class DefaultL2RecallClient implements L2RecallClient {

    @Override
    public L2RecallResult recall(L2RecallRequest request) {
        // 真实 ES/Milvus 客户端接入前，该实现用于显式关闭 MySQL Mock 召回后的兜底。
        // 空命中会让 L2 主流程继续执行，不会阻塞批次进度。
        log.info("L2 召回客户端处于降级模式，返回空 ES/Milvus 命中，esTopK: {}, milvusTopK: {}",
                request.getEsTopK(), request.getMilvusTopK());
        return L2RecallResult.builder()
                .esHits(List.of())
                .milvusHits(List.of())
                .degraded(true)
                .build();
    }
}
