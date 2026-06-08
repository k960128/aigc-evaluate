package com.kant.llm.eval.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.dao.entity.RiskDetailsDO;
import com.kant.llm.eval.dto.resp.RiskDetailsVO;

import java.util.List;

public interface RiskDetailsService extends IService<RiskDetailsDO> {

    List<RiskDetailsVO> detailsList();
}