package com.kant.llm.eval.fingerprint.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.fingerprint.dao.entity.KnowledgeNegativeFingerprintDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 负向指纹表 (黑名单：高危红线违禁词) Mapper。
 */
@Mapper
public interface KnowledgeNegativeFingerprintMapper extends BaseMapper<KnowledgeNegativeFingerprintDO> {
}
