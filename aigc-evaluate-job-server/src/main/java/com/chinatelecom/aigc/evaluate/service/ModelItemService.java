package com.chinatelecom.aigc.evaluate.service;

import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.ModelInfoDO;
import com.chinatelecom.aigc.evaluate.domain.ModelItemDO;
import com.chinatelecom.aigc.evaluate.dto.req.ModelInfoPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.ModelInfoSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.ModelInfoUpdateReq;
import com.chinatelecom.aigc.evaluate.dto.req.ModelItemPageReq;

import javax.validation.Valid;
import java.util.List;

public interface ModelItemService {
    /**
     * 获取模型信息分页
     * @param pageVO 分页查询请求
     * @return 分页结果
     */
    PageResult<ModelItemDO> getModelItemPage(ModelItemPageReq pageVO);

    /**
     * 获取模型信息
     *
     * @param id 模型编号
     * @return 模型信息
     */
    ModelItemDO getModelItem(Long id);

    List<ModelItemDO> getAllModelItems();
}
