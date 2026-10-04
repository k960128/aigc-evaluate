package com.kant.llm.eval.agent.production.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleRunDO;
import org.apache.ibatis.annotations.Mapper;
/**
 * agent_sample_run表的内部MyBatis-Plus映射，不直接暴露为业务接口。
 * 主体查询与后台租约领取分开；状态写入须由运行服务维护行锁、版本和执行权。
 * BaseMapper本身不提供业务授权，不能绕过Service使用通用CRUD破坏历史事实。
 */
@Mapper
public interface AgentSampleRunMapper extends BaseMapper<AgentSampleRunDO> {}
