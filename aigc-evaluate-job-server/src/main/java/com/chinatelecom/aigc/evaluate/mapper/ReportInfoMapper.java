package com.chinatelecom.aigc.evaluate.mapper;

import com.chinatelecom.aigc.evaluate.domain.ReportInfoDO;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.mapper.BaseMapperX;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReportInfoMapper extends BaseMapperX<ReportInfoDO> {

    /**
     * 根据任务ID和模型ID获取数据量
     * @param userId 用户ID
     * @param taskId 任务ID
     * @param modelId 模型ID
     * @return 数据量
     */
    default Long selectCountReport(String userId,String taskId, String modelId){
        return selectCount(new LambdaQueryWrapperX<ReportInfoDO>()
                .eq(ReportInfoDO::getTaskId, taskId)
                .eq(ReportInfoDO::getModelId, modelId));
    }

    /**
     * 根据任务ID和模型ID获取数据
     * * @param userId 用户ID
     * @param taskId 任务ID
     * @param modelId 模型ID
     * @return 数据
     */
    default ReportInfoDO selectReport(String userId, String taskId, String modelId, String reportType){
        return selectOne(new LambdaQueryWrapperX<ReportInfoDO>()
                .eq(ReportInfoDO::getTaskId, taskId)
                .eq(ReportInfoDO::getUserId, userId)
                .eq(ReportInfoDO::getModelId, modelId)
                .eq(ReportInfoDO::getReportType, reportType));
    }
}
