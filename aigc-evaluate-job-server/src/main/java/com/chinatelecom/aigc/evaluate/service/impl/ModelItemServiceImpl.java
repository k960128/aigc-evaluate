package com.chinatelecom.aigc.evaluate.service.impl;

import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.ModelItemDO;
import com.chinatelecom.aigc.evaluate.dto.req.ModelItemPageReq;
import com.chinatelecom.aigc.evaluate.mapper.ModelItemMapper;
import com.chinatelecom.aigc.evaluate.service.ModelItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.chinatelecom.aigc.evaluate.common.exception.enums.ErrorCodeConstants.MODEL_NOT_EXISTS_ERROR;
import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;

@Slf4j
@Service
public class ModelItemServiceImpl implements ModelItemService {

    private final ModelItemMapper modelItemMapper;

    public ModelItemServiceImpl(ModelItemMapper modelItemMapper) {
        this.modelItemMapper = modelItemMapper;
    }

    @Override
    public PageResult<ModelItemDO> getModelItemPage(ModelItemPageReq pageReqVO) {
        return modelItemMapper.selectPage(pageReqVO);
    }

    @Override
    public ModelItemDO getModelItem(Long id) {
        ModelItemDO modelItem = modelItemMapper.selectById(id);
        if (modelItem == null) {
            throw exception(MODEL_NOT_EXISTS_ERROR);
        }

        return modelItem;
    }

    public List<ModelItemDO> getAllModelItems() {
        return modelItemMapper.selectList(null); // 查询所有数据
    }


    private ModelItemDO validateModelInfoExists(Long id) {
        ModelItemDO modelItem  = modelItemMapper.selectById(id);
        if (modelItem == null) {
            throw exception(MODEL_NOT_EXISTS_ERROR);
        }
        return modelItem;
    }
}
