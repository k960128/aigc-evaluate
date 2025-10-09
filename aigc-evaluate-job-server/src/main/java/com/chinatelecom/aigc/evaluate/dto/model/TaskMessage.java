package com.chinatelecom.aigc.evaluate.dto.model;

import com.chinatelecom.aigc.evaluate.domain.ModelInfoDO;
import com.chinatelecom.aigc.evaluate.dto.resp.JobResp;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionSetItemResp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskMessage {

    Long taskId;
    ModelInfoDO modelInfo;
    QuestionSetItemResp question;
    JobResp jobResp;
    LocalDateTime createDateTime;
}
