package com.kant.llm.eval.es.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.kant.llm.eval.es.document.EvalResultDetailEsDocument;
import com.kant.llm.eval.es.service.EvalResultDetailEsSyncService;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.task.dao.mapper.EvalResultDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 评测结果明细 ES 投影同步服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalResultDetailEsSyncServiceImpl implements EvalResultDetailEsSyncService {

    private static final String RESULT_DETAIL_INDEX_ALIAS = "eval_result_detail";

    private final EvalResultDetailMapper evalResultDetailMapper;
    private final ElasticsearchClient elasticsearchClient;

    @Override
    public void syncUpsert(Long resultDetailId) {
        EvalResultDetailDO resultDetail = evalResultDetailMapper.selectById(resultDetailId);
        if (resultDetail == null) {
            log.warn("评测结果 ES 投影跳过，MySQL 结果明细不存在，resultDetailId: {}", resultDetailId);
            return;
        }
        EvalResultDetailEsDocument document = EvalResultDetailEsDocument.builder()
                .id(resultDetail.getId())
                .taskId(resultDetail.getTaskId())
                .taskDetailId(resultDetail.getTaskDetailId())
                .sampleId(resultDetail.getSampleId())
                .riskDetailsId(resultDetail.getRiskDetailsId())
                .inputText(resultDetail.getInputText())
                .modelOutput(resultDetail.getModelOutput())
                .rawResponse(resultDetail.getRawResponse())
                .latency(resultDetail.getLatency())
                .isSafe(resultDetail.getIsSafe())
                .status(resultDetail.getStatus())
                .errorMsg(resultDetail.getErrorMsg())
                .createTime(resultDetail.getCreateTime())
                .updateTime(resultDetail.getUpdateTime())
                .build();
        try {
            elasticsearchClient.index(request -> request
                    .index(RESULT_DETAIL_INDEX_ALIAS)
                    .id(String.valueOf(document.getId()))
                    .document(document));
        } catch (IOException ex) {
            throw new IllegalStateException("评测结果 ES 投影写入失败，resultDetailId=" + resultDetailId, ex);
        }
        log.info("评测结果 ES 投影写入完成，resultDetailId: {}, status: {}",
                resultDetail.getId(), resultDetail.getStatus());
    }
}
