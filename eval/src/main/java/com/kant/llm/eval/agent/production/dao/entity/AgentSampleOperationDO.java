package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/**
 * 一次物理生成或评审请求的固定输入、发送者、确认结果及计量事实。
 * SUCCEEDED仅表示响应已确认；CALLING/UNKNOWN不得自动重发，预留须保留到确认结算。
 * 对应agent_sample_operation表，不向外开放通用覆盖或删除接口；数据库时间约定为UTC微秒精度。
 */
@Getter @Setter @TableName("agent_sample_operation")
public class AgentSampleOperationDO {
    /**
     * 操作表是D05最重要的物理调用审计表：一条记录对应一次固定的Actor或Critic调用。
     * 它与批次进度解耦，目的是在超时、重启和租约切换时区分“未发送”“已发送但未知”“已确认”。
     */
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 由runId、ordinal、rewriteRound和角色派生的固定操作ID。 */ private String operationId;
    /** 生产批次。 */ private String runId;
    /** 0基根槽位。 */ private Integer ordinal;
    /** 当前质量改写轮次。 */ private Integer rewriteRound;
    /** GENERATE生成/改写或REFLECT模型评审；同轮两种调用分别预留、确认与结算。 */ private String operationKind;
    /** 原父候选。 */ private String parentCandidateId;
    /** 固定模型命令，含受限种子与反馈，不公开。 */ private String commandJson;
    /** 原始输入估算加输出上限的Token预留；结算只释放批次账本，不覆盖此历史值。 */ private Long reservationTokens;
    /** ACCEPTED已预留、CALLING已进入发送、SUCCEEDED响应确认、UNKNOWN结果未知、CANCELLED未发送已取消。 */ private String status;
    /** 首次调用线程身份，迟到事实只能由原调用者追加。 */ private String dispatchWorkerId;
    /** 按operationKind保存生成Result或反思Result；无确认响应时为空，不存非法原始响应或内部思维链。 */ private String resultJson;
    /** UTC首次预留时间。 */ private LocalDateTime createdAt;
}
