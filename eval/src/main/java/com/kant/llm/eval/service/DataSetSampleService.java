package com.kant.llm.eval.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.dao.entity.DataSetSampleDO;

/**
 * 数据集样本服务接口。
 *
 * <p>提供数据集样本的查询能力，用于按数据集 ID 获取样本列表。</p>
 */
public interface DataSetSampleService extends IService<DataSetSampleDO> {
}
