package com.kant.llm.eval.simple.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.simple.dao.entity.AgentSampleFactoryTaskDetailLogsDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 样本扩充-任务详情执行批次详情日志 Mapper
 */
@Mapper
public interface AgentSampleFactoryTaskDetailLogsMapper extends BaseMapper<AgentSampleFactoryTaskDetailLogsDO> {
}
