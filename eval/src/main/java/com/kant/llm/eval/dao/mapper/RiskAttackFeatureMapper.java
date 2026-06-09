package com.kant.llm.eval.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.dao.entity.RiskAttackFeatureDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * L2 攻击特征知识 Mapper。
 */
@Mapper
public interface RiskAttackFeatureMapper extends BaseMapper<RiskAttackFeatureDO> {
}
