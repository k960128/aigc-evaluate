package com.kant.springai.agent.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.springai.agent.dao.entity.SalesRegionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SalesRegionMapper extends BaseMapper<SalesRegionDO> {

    /**
     * 根据名称查询
     * @param name
     * @return
     */
    default List<SalesRegionDO> findByName(String name){
        return selectList(new LambdaQueryWrapper<SalesRegionDO>()
                .eq(SalesRegionDO::getName, name)
        );
    }
}
