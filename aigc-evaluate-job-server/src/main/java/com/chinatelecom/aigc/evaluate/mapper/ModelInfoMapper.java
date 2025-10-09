package com.chinatelecom.aigc.evaluate.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.ModelInfoDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionDO;
import com.chinatelecom.aigc.evaluate.dto.req.ModelInfoPageReq;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.mapper.BaseMapperX;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelInfoMapper extends BaseMapperX<ModelInfoDO> {

    default ModelInfoDO selectByAppName(String appName) {
        return selectOne(ModelInfoDO::getAppName, appName);
    }

    default ModelInfoDO selectByModelNameAndVersion(String appName, String modelName, String version) {
        return this.selectOne(new QueryWrapper<ModelInfoDO>()
                .eq("app_name", appName)
                .eq("model_name", modelName)
                .eq("model_version", version));
    }

    default ModelInfoDO selectByModelName(String modelName) {
        return selectOne(ModelInfoDO::getModelName, modelName);
    }

    default PageResult<ModelInfoDO> selectPage(ModelInfoPageReq reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ModelInfoDO>()
                .likeIfPresent(ModelInfoDO::getAppName, reqVO.getAppName())
                .likeIfPresent(ModelInfoDO::getModelName, reqVO.getModelName())
                .eqIfPresent(ModelInfoDO::getModelVersion, reqVO.getModelVersion())
                .orderByDesc(ModelInfoDO::getCreateTime, ModelInfoDO::getUpdateTime)
        );
    }
}
