package com.chinatelecom.aigc.evaluate.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.chinatelecom.aigc.evaluate.common.util.snow.CodeUtils;
import com.chinatelecom.aigc.evaluate.dto.req.ReportSaveReq;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.dataobject.BaseDO;
import lombok.*;

@TableName("report_info")
@KeySequence("report_info_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportInfoDO extends BaseDO {

    /**
     * ID
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 模型ID
     */
    private Long modelId;

    /**
     * 文件名称
     */
    @TableField("`file_name`")
    private String fileName;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 报告类型
     */
    private String reportType;

    /**
     * 阅读状态
     */
    private Boolean readingStatus;

    @TableField("`desc`")
    private String desc;

    @TableField("`manual_desc`")
    private String manualDesc;

    // 充血模型
    public static ReportInfoDO create(ReportSaveReq req){
        return ReportInfoDO.builder()
                .id(CodeUtils.getSnowFlakeId())
                .userId(Long.valueOf(req.getUserId()))
                .taskId(Long.valueOf(req.getTaskId()))
                .modelId(Long.valueOf(req.getModelId()))
                .fileName(req.getName())
                .readingStatus(false)
                .desc(req.getDesc())
                .manualDesc(req.getManualDesc())
                .reportType(req.getReportType())
                .build();
    }
}
