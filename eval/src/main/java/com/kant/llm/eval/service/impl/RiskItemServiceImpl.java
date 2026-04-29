package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.RiskItemDO;
import com.kant.llm.eval.dao.mapper.RiskItemMapper;
import com.kant.llm.eval.service.RiskItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RiskItemServiceImpl extends ServiceImpl<RiskItemMapper, RiskItemDO> implements RiskItemService {
}