package com.kant.llm.eval.agent.production.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleContentIndexDO;
import org.apache.ibatis.annotations.Mapper;
/**
 * agent_sample_content_index表的内部MyBatis-Plus映射，不直接暴露为业务接口。
 * 唯一索引竞争由结算服务处理，重复时引用首候选，不重发生成请求。
 * BaseMapper本身不提供业务授权，不能绕过Service使用通用CRUD破坏历史事实。
 */
@Mapper
public interface AgentSampleContentIndexMapper extends BaseMapper<AgentSampleContentIndexDO> {}
