package com.chinatelecom.aigc.evaluate.domain;

import cn.hutool.crypto.digest.MD5;
import com.baomidou.mybatisplus.annotation.*;
import com.chinatelecom.aigc.evaluate.common.util.snow.CodeUtils;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionImportReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionUpdateReq;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.dataobject.BaseDO;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

@TableName("question_info")
@KeySequence("question_info_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDO extends BaseDO {

    /**
     * ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 题目唯一编号
     */
    private String questionId;

    /**
     * 题目内容
     */
    private String title;

    /**
     * 所属题库
     */
    private String category;

    /**
     * 标签
     */
    private String tags;

    /**
     * 题目内容MD5哈希值
     */
    private String contentHash;

    /**
     * 题目难度
     */
    private String difficulty;

    /**
     * 攻击方式
     */
    private String attackMethod;

    /**
     * 题目版本号
     */
    private Integer version;

    /**
     * 数据来源
     */
    private String dataSource;

    /**
     * 描述(备注)
     */
    @TableField("`desc`")
    private String desc;

    /**
     * 创建题目
     * 充血模型
     *
     * @param req
     * @return
     */
    public static QuestionDO create(QuestionSaveReq req) {
        return QuestionDO.builder()
                .questionId(String.valueOf(CodeUtils.getSnowFlakeId()))
                .title(req.getTitle())
                .category(req.getCategory())
                .tags(req.getTags())
                .difficulty(req.getDifficulty())
                .attackMethod(StringUtils.isBlank(req.getAttackMethod()) ? "无" : req.getAttackMethod())
                .contentHash(generateMd5Hash(req.getTitle(), req.getCategory(), req.getTags(), req.getDifficulty(), req.getAttackMethod(), req.getDataSource(), req.getDesc()))
                .version(1)
                .dataSource(req.getDataSource())
                .desc(req.getDesc())
                .build();
    }


    /**
     * 导入-创建题目
     * 充血模型
     *
     * @param req
     * @return
     */
    public static QuestionDO createImportDO(QuestionImportReq req) {
        return QuestionDO.builder()
                .questionId(StringUtils.isNotBlank(req.getQuestionId()) ? req.getQuestionId() : String.valueOf(CodeUtils.getSnowFlakeId()))
                .title(req.getTitle())
                .category(req.getCategory())
                .tags(req.getTags())
                .difficulty(req.getDifficulty())
                .attackMethod(StringUtils.isBlank(req.getAttackMethod()) ? "无" : req.getAttackMethod())
                .contentHash(generateMd5Hash(req.getTitle(), req.getCategory(), req.getTags(), req.getDifficulty(), req.getAttackMethod(), req.getDataSource()))
                .dataSource(req.getDataSource())
                .version(1)
                .build();
    }

    /**
     * 修改题目
     * 充血模型
     *
     * @param req
     * @return
     */
    public static QuestionDO update(QuestionUpdateReq req, int version) {
        return QuestionDO.builder()
                .questionId(req.getQuestionId())
                .title(req.getTitle())
                .category(req.getCategory())
                .tags(req.getTags())
                .difficulty(req.getDifficulty())
                .attackMethod(StringUtils.isBlank(req.getAttackMethod()) ? "无" : req.getAttackMethod())
                .dataSource(req.getDataSource())
                .version(version)
                .desc(req.getDesc())
                .contentHash(generateMd5Hash(req.getTitle(), req.getCategory(), req.getTags(), req.getDifficulty(), req.getAttackMethod(), req.getDataSource(), req.getDesc()))
                .build();
    }

    /**
     * 导入修改题目
     * 充血模型
     *
     * @param req
     * @return
     */
    public static QuestionDO updateImportDO(QuestionImportReq req, Long id, int version) {
        return QuestionDO.builder()
                .id(id)
                .questionId(req.getQuestionId())
                .title(req.getTitle())
                .category(req.getCategory())
                .tags(req.getTags())
                .difficulty(req.getDifficulty())
                .attackMethod(StringUtils.isBlank(req.getAttackMethod()) ? "无" : req.getAttackMethod())
                .version(version + 1)
                .contentHash(generateMd5Hash(req.getTitle(), req.getCategory(), req.getTags(), req.getDifficulty(), req.getAttackMethod(), req.getDataSource()))
                .dataSource(req.getDataSource())
                .build();
    }

    /**
     * 生成MD5值，改为动态获取，防止后期业务变更
     *
     * @param params 参数
     * @return string
     */
    public static String generateMd5Hash(String... params) {
        // 将param返回成一个字符串，将内容进行拼接
        String context = String.join("&", params);
        return MD5.create().digestHex(context);
    }
}
