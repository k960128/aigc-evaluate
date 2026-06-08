package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.DataSetDO;
import com.kant.llm.eval.dao.mapper.DataSetMapper;
import com.kant.llm.eval.service.DataSetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 数据集信息服务实现类。
 *
 * <p>基于 MyBatis-Plus 基础服务实现数据集信息表的通用操作。</p>
 */
@Slf4j
@Service
public class DataSetServiceImpl extends ServiceImpl<DataSetMapper, DataSetDO> implements DataSetService {
}
