package com.kant.llm.eval.task.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.task.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.task.dao.mapper.EvalTaskDetailMapper;
import com.kant.llm.eval.task.service.EvalTaskDetailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EvalTaskDetailServiceImpl extends ServiceImpl<EvalTaskDetailMapper, EvalTaskDetailDO> implements EvalTaskDetailService {
}
