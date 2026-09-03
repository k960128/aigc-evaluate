package com.kant.llm.eval.fingerprint.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.fingerprint.dao.entity.KnowledgePositiveFingerprintDO;
import com.kant.llm.eval.fingerprint.dto.req.KnowledgePositiveFingerprintPageRequest;
import com.kant.llm.eval.fingerprint.dto.req.KnowledgePositiveFingerprintRequest;
import com.kant.llm.eval.fingerprint.dto.resp.KnowledgePositiveFingerprintVO;

public interface KnowledgePositiveFingerprintService extends IService<KnowledgePositiveFingerprintDO> {
    /**
     * 创建正向指纹
     * @param request request
     * @return result
     */
    Boolean savePositiveFingerprint(KnowledgePositiveFingerprintRequest request);

    /**
     * 分页查询正向指纹列表
     * @param request request
     * @return pageResult
     */
    Page<KnowledgePositiveFingerprintVO> pagePositiveFingerprint(KnowledgePositiveFingerprintPageRequest request);
}
