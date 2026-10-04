package com.kant.llm.eval.agent.production.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleScheduleFireDO;
import org.apache.ibatis.annotations.Mapper;
/**
 * agent_sample_schedule_fire表的内部MyBatis-Plus映射，不直接暴露为业务接口。
 * 计划行锁和槽位唯一性约束保护审计，查询前须验证计划归属。
 * BaseMapper本身不提供业务授权，不能绕过Service使用通用CRUD破坏历史事实。
 */
@Mapper
public interface AgentSampleScheduleFireMapper extends BaseMapper<AgentSampleScheduleFireDO> {}
