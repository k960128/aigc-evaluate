package com.kant.llm.eval.es.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.kant.llm.eval.es.document.EvalPipelineNodeDetailEsDocument;
import com.kant.llm.eval.es.service.EvalPipelineNodeDetailEsSyncService;
import com.kant.llm.eval.pipeline.dao.entity.EvalPipelineNodeDetailDO;
import com.kant.llm.eval.pipeline.dao.mapper.EvalPipelineNodeDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 流水线节点日志 ES 投影同步服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalPipelineNodeDetailEsSyncServiceImpl implements EvalPipelineNodeDetailEsSyncService {

    private static final String PIPELINE_NODE_INDEX_ALIAS = "eval_pipeline_node_detail";

    private final EvalPipelineNodeDetailMapper evalPipelineNodeDetailMapper;
    private final ElasticsearchClient elasticsearchClient;

    @Override
    public void syncUpsert(Long nodeRecordId) {
        EvalPipelineNodeDetailDO nodeDetail = evalPipelineNodeDetailMapper.selectById(nodeRecordId);
        if (nodeDetail == null) {
            log.warn("流水线节点 ES 投影跳过，MySQL 节点日志不存在，nodeRecordId: {}", nodeRecordId);
            return;
        }
        EvalPipelineNodeDetailEsDocument document = EvalPipelineNodeDetailEsDocument.builder()
                .id(nodeDetail.getId())
                .taskId(nodeDetail.getTaskId())
                .taskDetailId(nodeDetail.getTaskDetailId())
                .resultDetailId(nodeDetail.getResultDetailId())
                .sampleId(nodeDetail.getSampleId())
                .nodeCode(nodeDetail.getNodeCode())
                .status(nodeDetail.getStatus())
                .startTime(nodeDetail.getStartTime())
                .endTime(nodeDetail.getEndTime())
                .latency(nodeDetail.getLatency())
                .errorMsg(nodeDetail.getErrorMsg())
                .inputSnapshot(nodeDetail.getInputSnapshot())
                .outputSnapshot(nodeDetail.getOutputSnapshot())
                .nodeResult(nodeDetail.getNodeResult())
                .createTime(nodeDetail.getCreateTime())
                .updateTime(nodeDetail.getUpdateTime())
                .build();
        try {
            elasticsearchClient.index(request -> request
                    .index(PIPELINE_NODE_INDEX_ALIAS)
                    .id(String.valueOf(document.getId()))
                    .document(document));
        } catch (IOException ex) {
            throw new IllegalStateException("流水线节点 ES 投影写入失败，nodeRecordId=" + nodeRecordId, ex);
        }
        log.info("流水线节点 ES 投影写入完成，nodeRecordId: {}, nodeCode: {}, status: {}",
                nodeDetail.getId(), nodeDetail.getNodeCode(), nodeDetail.getStatus());
    }
}
