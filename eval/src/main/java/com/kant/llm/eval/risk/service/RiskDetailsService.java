package com.kant.llm.eval.risk.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.risk.dao.entity.RiskDetailsDO;
import com.kant.llm.eval.risk.dto.resp.RiskDetailsVO;

import java.util.List;

public interface RiskDetailsService extends IService<RiskDetailsDO> {

    List<RiskDetailsVO> detailsList();
}