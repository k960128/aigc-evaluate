package com.kant.llm.eval.agent.production.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleOperationDO;
import org.apache.ibatis.annotations.Mapper;
/**
 * agent_sample_operation表的内部MyBatis-Plus映射，不直接暴露为业务接口。
 * 按操作身份读写前须授权所属批次；发送、迟到确认和结算有各自状态条件。
 * BaseMapper本身不提供业务授权，不能绕过Service使用通用CRUD破坏历史事实。
 */
@Mapper
public interface AgentSampleOperationMapper extends BaseMapper<AgentSampleOperationDO> {}
