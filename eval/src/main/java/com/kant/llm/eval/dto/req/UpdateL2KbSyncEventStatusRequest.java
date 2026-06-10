package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L2 知识库同步事件状态更新请求。
 *
 * <p>这是后续真实索引器回写同步结果的统一入参。
 * 当前阶段可以人工调用该接口模拟 ES/Milvus 同步成功或失败，从而验证特征表状态回写。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateL2KbSyncEventStatusRequest {

    /** 同步事件 ID，对应 kb_sync_event.event_id，必填。 */
    private String eventId;

    /** ES 处理状态：0-待处理，1-成功，2-失败；不传则保持原状态。 */
    private Integer esStatus;

    /** Milvus 处理状态：0-待处理，1-成功，2-失败；不传则保持原状态。 */
    private Integer milvusStatus;

    /** 失败原因；成功时可为空，失败时用于后台排查索引写入异常。 */
    private String lastError;
}
