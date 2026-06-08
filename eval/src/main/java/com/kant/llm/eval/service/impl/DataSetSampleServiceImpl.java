package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.DataSetSampleDO;
import com.kant.llm.eval.dao.mapper.DataSetSampleMapper;
import com.kant.llm.eval.service.DataSetSampleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 数据集样本服务实现类。
 *
 * <p>基于 MyBatis-Plus 基础服务实现数据集样本表的通用查询操作。</p>
 */
@Slf4j
@Service
public class DataSetSampleServiceImpl extends ServiceImpl<DataSetSampleMapper, DataSetSampleDO> implements DataSetSampleService {
}
