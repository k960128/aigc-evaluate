package com.kant.springai.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;
@TableName("sa_sales_region")
@KeySequence("sa_sales_region_seq") // 适配Oracle/PostgreSQL等数据库主键自增，MySQL可移除
@Data
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesRegionDO {

    @Id
    private Long id;

    private String name;

    private Long parentRegionId;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Boolean deleted;
}