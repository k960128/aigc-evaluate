package com.kant.springai.agent.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.springai.agent.dao.entity.SalesRepDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SalesRepMapper extends BaseMapper<SalesRepDO> {

    default List<SalesRepDO> findByRegionId(Long regionId){
        return selectList(new LambdaQueryWrapper<SalesRepDO>()
                .eq(SalesRepDO::getRegionId, regionId));
    }

    default List<SalesRepDO> findByRole(String role){
        return selectList(new LambdaQueryWrapper<SalesRepDO>()
                .eq(SalesRepDO::getRole, role));
    }

    default Optional<SalesRepDO> findByName(String name){
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<SalesRepDO>()
                .eq(SalesRepDO::getName, name)));
    }
}
