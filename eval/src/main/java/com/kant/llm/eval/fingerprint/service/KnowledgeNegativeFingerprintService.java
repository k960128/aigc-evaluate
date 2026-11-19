package com.kant.llm.eval.fingerprint.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.fingerprint.dao.entity.KnowledgeNegativeFingerprintDO;
import com.kant.llm.eval.fingerprint.dto.req.KnowledgeNegativeFingerprintPageRequest;
import com.kant.llm.eval.fingerprint.dto.req.KnowledgeNegativeFingerprintRequest;
import com.kant.llm.eval.fingerprint.dto.resp.KnowledgeNegativeFingerprintVO;

public interface KnowledgeNegativeFingerprintService extends IService<KnowledgeNegativeFingerprintDO> {
    /**
     * 新增负向指纹
     * @param request request
     * @return resp
     */
    Boolean saveNegativeFingerprint(KnowledgeNegativeFingerprintRequest request);

    /**
     * 查询负向指纹分页
     * @param request request
     * @return pageResult
     */
    Page<KnowledgeNegativeFingerprintVO> pageNegativeFingerprint(KnowledgeNegativeFingerprintPageRequest request);

    /**
     * 发布负向指纹
     * @return result
     */
    Boolean publishNegativeFingerprint();
}
