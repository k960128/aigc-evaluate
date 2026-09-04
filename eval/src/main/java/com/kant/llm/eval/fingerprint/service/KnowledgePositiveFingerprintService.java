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

    /**
     * 发布正向指纹
     * @return result
     */
    Boolean publishPositiveFingerprint();

    /**
     * 刷新正向指纹的SimHash值
     * @return result
     */
    Boolean refreshPositiveFingerprintSimHash();

    /**
     * 核心校验方法：判断是否为标准拒答
     * @param riskDetailsId 风险分类标识
     * @param checkText 检测文本
     * @return result
     */
    Boolean validateStandardRefusal(Long riskDetailsId, String checkText);
}
