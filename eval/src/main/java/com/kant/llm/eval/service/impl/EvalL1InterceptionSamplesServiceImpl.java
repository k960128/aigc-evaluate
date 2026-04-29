package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.EvalL1InterceptionSamplesDO;
import com.kant.llm.eval.dao.mapper.EvalL1InterceptionSamplesMapper;
import com.kant.llm.eval.service.EvalL1InterceptionSamplesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EvalL1InterceptionSamplesServiceImpl extends ServiceImpl<EvalL1InterceptionSamplesMapper, EvalL1InterceptionSamplesDO> implements EvalL1InterceptionSamplesService {
}