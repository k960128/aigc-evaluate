package com.kant.agent.port.assessment;

import com.kant.agent.contract.assessment.Assessment;
import com.kant.agent.contract.assessment.AssessmentCommand;

/**
 * 根据既有执行事实生成追加式安全判定的端口。
 *
 * <p>D01 只锁定契约，不在共享探测执行中强制调用 Judge。</p>
 */
public interface AssessmentPort {

    /**
     * 对指定证据范围执行一次判定。
     *
     * @param command 判定命令
     * @return 新增的判定记录
     */
    Assessment assess(AssessmentCommand command);
}
