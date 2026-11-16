package com.kant.llm.eval.simple.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.simple.dao.entity.AgentSampleSeedDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * agent_sample_seed表的内部MyBatis-Plus映射，不直接暴露为业务接口。
 * 主体内创建键保证幂等，读取必须限制租户和操作者，正文需经服务层摘要核对。
 * BaseMapper本身不提供业务授权，不能绕过Service使用通用CRUD破坏历史事实。
 */
@Mapper
public interface AgentSampleSeedMapper extends BaseMapper<AgentSampleSeedDO> {}
