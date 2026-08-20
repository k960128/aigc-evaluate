package com.kant.llm.eval.knowledge.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.knowledge.dto.req.KnowledgeFeatureDocumentPageReq;
import com.kant.llm.eval.knowledge.dto.req.KnowledgeFeatureDocumentReq;
import com.kant.llm.eval.knowledge.dto.req.KnowledgeFeatureVersionReq;
import com.kant.llm.eval.knowledge.dto.req.PublishVersionReq;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureDocumentVO;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureDocumentVersionSnapshotVO;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureDocumentVersionVO;
import com.kant.llm.eval.knowledge.dto.resp.RiskAttackFeatureVO;
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
     *
     * @return 当前知识库版本信息
     */
    @PostMapping("/current-version")
    public Result<KnowledgeFeatureDocumentVersionVO> getKnowledgeFeatureDocumentVersion(@RequestBody KnowledgeFeatureVersionReq req) {
        return Results.success(knowledgeFeatureManagerService.getKnowledgeFeatureDocumentVersion(req));
    }

    /**
     * 获取知识库版本快照列表
     *
     * @return 知识库版本快照列表
     */
    @PostMapping("/version-snapshot-list")
    public Result<List<KnowledgeFeatureDocumentVersionSnapshotVO>> getKnowledgeFeatureDocumentVersionSnapshotList(@RequestBody KnowledgeFeatureVersionReq req) {
        return Results.success(knowledgeFeatureManagerService.getKnowledgeFeatureDocumentVersionSnapshotList(req));
    }

    /**
     * 获取知识库特征文档分页列表
     *
     * @return 知识库特征文档分页列表
     */
    @PostMapping("/page")
    public Result<Page<KnowledgeFeatureDocumentVO>> getKnowledgeFeatureDocumentPage(@RequestBody KnowledgeFeatureDocumentPageReq pageReq) {
        return Results.success(knowledgeFeatureManagerService.getKnowledgeFeatureDocumentPage(pageReq));
    }

    /**
     * 保存攻击语义知识库特征文档
     *
     * @param req 请求体
     * @return Boolean 结果
     */
    @PostMapping("/save")
    public Result<Boolean> saveKnowledgeFeatureDocument(@RequestBody KnowledgeFeatureDocumentReq req) {
        return Results.success(knowledgeFeatureManagerService.saveKnowledgeFeatureDocument(req));
    }

    /**
     * 发布版本
     *
     * @param req 请求体
     */
    @PostMapping("/publish-version")
    public Result<Boolean> publishVersion(@RequestBody PublishVersionReq req) {
        return Results.success(knowledgeFeatureManagerService.publishVersion(req), "已提交发布版本请求");
    }
}
