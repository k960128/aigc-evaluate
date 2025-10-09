package com.chinatelecom.aigc.evaluate.mapper;

import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.JobDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionDO;
import com.chinatelecom.aigc.evaluate.dto.req.JobPageReq;
import com.chinatelecom.aigc.evaluate.dto.resp.JobLogResp;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.mapper.BaseMapperX;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobMapper extends BaseMapperX<JobDO> {

    default JobDO selectByHandlerName(String handlerName) {
        return selectOne(JobDO::getHandlerName, handlerName);
    }

    default JobDO selectByName(String name) {
        return selectOne(JobDO::getName, name);
    }

    default PageResult<JobDO> selectPage(JobPageReq reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<JobDO>()
                .likeIfPresent(JobDO::getName, reqVO.getName())
                .eqIfPresent(JobDO::getStatus, reqVO.getStatus())
                .orderByDesc(JobDO::getStartTime, JobDO::getId)
        );
    }
}
