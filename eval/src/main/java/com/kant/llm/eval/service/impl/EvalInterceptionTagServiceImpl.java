package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.EvalInterceptionTagDO;
import com.kant.llm.eval.dao.mapper.EvalInterceptionTagMapper;
import com.kant.llm.eval.service.EvalInterceptionTagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EvalInterceptionTagServiceImpl extends ServiceImpl<EvalInterceptionTagMapper, EvalInterceptionTagDO> implements EvalInterceptionTagService {
}