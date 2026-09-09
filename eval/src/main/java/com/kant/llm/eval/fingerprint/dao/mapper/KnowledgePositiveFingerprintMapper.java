package com.kant.llm.eval.fingerprint.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.fingerprint.dao.entity.KnowledgePositiveFingerprintDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 正向指纹表 (白名单：标准安全拒答) Mapper。
 */
@Mapper
public interface KnowledgePositiveFingerprintMapper extends BaseMapper<KnowledgePositiveFingerprintDO> {
}
