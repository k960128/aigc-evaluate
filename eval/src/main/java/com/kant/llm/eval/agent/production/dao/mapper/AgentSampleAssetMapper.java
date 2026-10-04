package com.kant.llm.eval.agent.production.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleAssetDO;
import org.apache.ibatis.annotations.Mapper;
/**
 * agent_sample_asset表的内部MyBatis-Plus映射，不直接暴露为业务接口。
 * 只由人工审核事务新增，读取时必须同时限制租户、操作者和精确版本。
 * BaseMapper本身不提供业务授权，不能绕过Service使用通用CRUD破坏历史事实。
 */
@Mapper
public interface AgentSampleAssetMapper extends BaseMapper<AgentSampleAssetDO> {}
