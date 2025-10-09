package com.chinatelecom.aigc.evaluate.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.ModelItemDO;
import com.chinatelecom.aigc.evaluate.dto.req.ModelInfoPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.ModelItemPageReq;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.mapper.BaseMapperX;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelItemMapper extends BaseMapperX<ModelItemDO> {
    default ModelItemDO selectByModelNameAndVersion(String modelName, String version) {
        return this.selectOne(new QueryWrapper<ModelItemDO>()
                .eq("model_name", modelName)
                .eq("model_version", version));
    }

    default PageResult<ModelItemDO> selectPage(ModelItemPageReq reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ModelItemDO>()
                .likeIfPresent(ModelItemDO::getModelName, reqVO.getModelName())
                .eqIfPresent(ModelItemDO::getModelVersion, reqVO.getModelVersion())
                .orderByDesc(ModelItemDO::getCreateTime, ModelItemDO::getUpdateTime)
        );
    }
}
