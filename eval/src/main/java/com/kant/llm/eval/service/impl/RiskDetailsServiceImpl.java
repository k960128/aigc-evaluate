package com.kant.llm.eval.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.RiskDetailsDO;
import com.kant.llm.eval.dao.mapper.RiskDetailsMapper;
import com.kant.llm.eval.dto.resp.RiskDetailsVO;
import com.kant.llm.eval.service.RiskDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class RiskDetailsServiceImpl extends ServiceImpl<RiskDetailsMapper, RiskDetailsDO> implements RiskDetailsService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RiskDetailsServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<RiskDetailsVO> detailsList() {
        return detailsListCache();
    }

    List<RiskDetailsVO> detailsListCache() {
        List<RiskDetailsVO> detailsList = (List<RiskDetailsVO>) redisTemplate.opsForValue().get("riskDetailsList");
        if (null == detailsList) {
            List<RiskDetailsDO> list = list();
            detailsList = new ArrayList<>();
            if (list.isEmpty()) {
                return detailsList;
            }
            detailsList = BeanUtil.copyToList(list, RiskDetailsVO.class);
            redisTemplate.opsForValue().set("riskDetailsList", detailsList);
        }
        return detailsList;
    }
}