package com.kant.llm.eval.fingerprint.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.fingerprint.dto.req.KnowledgeNegativeFingerprintPageRequest;
import com.kant.llm.eval.fingerprint.dto.req.KnowledgeNegativeFingerprintRequest;
import com.kant.llm.eval.fingerprint.dto.req.KnowledgePositiveFingerprintPageRequest;
import com.kant.llm.eval.fingerprint.dto.req.KnowledgePositiveFingerprintRequest;
import com.kant.llm.eval.fingerprint.dto.resp.KnowledgeNegativeFingerprintVO;
import com.kant.llm.eval.fingerprint.dto.resp.KnowledgePositiveFingerprintVO;
import com.kant.llm.eval.fingerprint.service.KnowledgeNegativeFingerprintService;
import com.kant.llm.eval.fingerprint.service.KnowledgePositiveFingerprintService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/finger-print")
public class FingerprintController {

    private final KnowledgeNegativeFingerprintService knowledgeNegativeFingerprintService;
    private final KnowledgePositiveFingerprintService knowledgePositiveFingerprintService;

    public FingerprintController(KnowledgeNegativeFingerprintService knowledgeNegativeFingerprintService,
                                 KnowledgePositiveFingerprintService knowledgePositiveFingerprintService) {
        this.knowledgeNegativeFingerprintService = knowledgeNegativeFingerprintService;
        this.knowledgePositiveFingerprintService = knowledgePositiveFingerprintService;
    }

    /**
     * 新增正向指纹
     */
    @PostMapping("/positive/save")
    public Result<Boolean> savePositiveFingerprint(@RequestBody KnowledgePositiveFingerprintRequest request) {
        return Results.success(knowledgePositiveFingerprintService.savePositiveFingerprint(request));
    }

    /**
     * 根据风险分类ID查询正向指纹分页
     */
    @PostMapping("/positive/page")
    public Result<Page<KnowledgePositiveFingerprintVO>> pagePositiveFingerprint(@RequestBody KnowledgePositiveFingerprintPageRequest request) {
        return Results.success(knowledgePositiveFingerprintService.pagePositiveFingerprint(request));
    }

    /**
     * 发布正向指纹
     */
    @PostMapping("/positive/publish")
    public Result<Boolean> publishPositiveFingerprint() {
        return Results.success(knowledgePositiveFingerprintService.publishPositiveFingerprint());
    }

    /**
     * 刷新正向指纹的SimHash值
     * @return result
     */
    @PostMapping("/positive/refresh-sim-hash")
    public Result<Boolean> refreshPositiveFingerprintSimHash(){
        return Results.success(knowledgePositiveFingerprintService.refreshPositiveFingerprintSimHash());
    }


// =============================================== 负向指纹领域

    /**
     * 新增负向指纹
     */
    @PostMapping("/negative/save")
    public Result<Boolean> saveNegativeFingerprint(@RequestBody KnowledgeNegativeFingerprintRequest request) {
        return Results.success(knowledgeNegativeFingerprintService.saveNegativeFingerprint(request));
    }

    /**
     * 根据风险分类ID查询负向指纹分页
     */
    @PostMapping("/negative/page")
    public Result<Page<KnowledgeNegativeFingerprintVO>> pageNegativeFingerprint(@RequestBody KnowledgeNegativeFingerprintPageRequest request) {
        return Results.success(knowledgeNegativeFingerprintService.pageNegativeFingerprint(request));
    }

    /**
     * 发布负向指纹
     * @return result
     */
    @PostMapping("/negative/publish")
    public Result<Boolean> publishNegativeFingerprint() {
        return Results.success(knowledgeNegativeFingerprintService.publishNegativeFingerprint());
    }

    // =============================================== 公共领域
    /**
     * 刷新正负向指纹的Hash值
     * @return result
     */
    @PostMapping("/positive/refresh-hash")
    public Result<Boolean> refreshFingerprintHash(){
        knowledgePositiveFingerprintService.refreshFingerprintHash();
        knowledgeNegativeFingerprintService.refreshFingerprintHash();
        return Results.success(true);
    }
}
