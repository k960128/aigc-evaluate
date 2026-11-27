package com.kant.llm.eval.dataset.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dataset.dao.entity.DataSetSampleItemDO;
import com.kant.llm.eval.dataset.dao.mapper.DataSetSampleItemMapper;
import com.kant.llm.eval.dataset.service.DataSetSampleItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 数据集样本关联关系表服务实现
 */
@Slf4j
@Service
public class DataSetSampleItemServiceImpl
        extends ServiceImpl<DataSetSampleItemMapper, DataSetSampleItemDO>
        implements DataSetSampleItemService {
}
