package com.kant.springai.agent.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.springai.agent.dao.entity.SaProductDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;
import java.util.List;

@Mapper
public interface SaProductMapper extends BaseMapper<SaProductDO> {

    default Optional<SaProductDO> findBySkuCode(String skuCode){
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<SaProductDO>()
                .eq(SaProductDO::getSkuCode, skuCode)));
    }

    default List<SaProductDO> findByCategory(String category){
        return selectList(new LambdaQueryWrapper<SaProductDO>()
                .eq(SaProductDO::getCategory, category));
    }

    default List<SaProductDO> findByStatus(String status){
        return selectList(new LambdaQueryWrapper<SaProductDO>()
                .eq(SaProductDO::getStatus, status));
    }
}
