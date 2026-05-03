package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.dao.mapper.EvalResultDetailMapper;
import com.kant.llm.eval.service.EvalResultDetailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EvalResultDetailServiceImpl extends ServiceImpl<EvalResultDetailMapper, EvalResultDetailDO> implements EvalResultDetailService {
}