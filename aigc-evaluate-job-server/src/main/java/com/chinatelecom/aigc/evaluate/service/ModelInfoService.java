package com.chinatelecom.aigc.evaluate.service;

import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.ModelInfoDO;
import com.chinatelecom.aigc.evaluate.dto.req.ModelInfoPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.ModelInfoSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.ModelInfoUpdateReq;

import javax.validation.Valid;

public interface ModelInfoService {

    /**
     * 创建模型信息
     *
     * @param createReqVO 创建信息
     * @return 模型编号
     */
    Long createModelInfo(@Valid ModelInfoSaveReq createReqVO);

    /**
     * 测试模型链接
     *
     * @param createReqVO 创建信息
     * @return 模型编号
     */
    String connectModelInfo(@Valid ModelInfoSaveReq createReqVO);

    /**
     * 获取模型信息分页
     * @param pageVO 分页查询请求
     * @return 分页结果
     */
    PageResult<ModelInfoDO> getModelInfoPage(ModelInfoPageReq pageVO);

    /**
     * 更新模型信息
     *
     * @param updateReqVO 更新信息
     */
    void updateModelInfo(ModelInfoUpdateReq updateReqVO);

    /**
     * 获取模型信息
     *
     * @param id 模型编号
     * @return 模型信息
     */
    ModelInfoDO getModelInfo(Long id);

    /**
     * 删除模型信息
     *
     * @param id 模型编号
     */
    void deleteModelInfo(Long id);
}
