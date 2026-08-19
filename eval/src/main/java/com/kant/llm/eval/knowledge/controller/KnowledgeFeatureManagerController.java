package com.kant.llm.eval.knowledge.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureDocumentVersionSnapshotVO;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureDocumentVersionVO;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * L2 知识库聚合管理接口。
 */
@RestController
@RequestMapping("/knowledge/feature")
@RequiredArgsConstructor
public class KnowledgeFeatureManagerController {

    private final KnowledgeFeatureManagerService knowledgeFeatureManagerService;

    /**
     * 获取当前知识库版本信息
     * @return 当前知识库版本信息
     */
    @GetMapping("/get-current-version")
    public Result<KnowledgeFeatureDocumentVersionVO> getKnowledgeFeatureDocumentVersion() {
        return Results.success(knowledgeFeatureManagerService.getKnowledgeFeatureDocumentVersion());
    }

    /**
     * 获取知识库版本快照列表
     * @return 知识库版本快照列表
     */
    @GetMapping("/get-version-snapshot-list")
    public Result<List<KnowledgeFeatureDocumentVersionSnapshotVO>> getKnowledgeFeatureDocumentVersionSnapshotList() {
        return Results.success(knowledgeFeatureManagerService.getKnowledgeFeatureDocumentVersionSnapshotList());
    }
}
