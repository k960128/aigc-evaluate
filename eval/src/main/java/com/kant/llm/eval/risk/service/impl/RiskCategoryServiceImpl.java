package com.kant.llm.eval.risk.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.risk.dao.entity.RiskCategoryDO;
import com.kant.llm.eval.risk.dao.mapper.RiskCategoryMapper;
import com.kant.llm.eval.risk.service.RiskCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RiskCategoryServiceImpl extends ServiceImpl<RiskCategoryMapper, RiskCategoryDO> implements RiskCategoryService {
}