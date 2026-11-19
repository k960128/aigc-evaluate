package com.kant.llm.eval.task.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class RetryEvalResultRequest {
    @NotNull
    @Positive
    private Long resultDetailId;

    @NotNull
    private Mode mode = Mode.AUTO;

    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}", message = "requestId须为1至64位字母、数字、下划线或连字符")
    private String requestId;

    public enum Mode {
        AUTO, FULL
    }
}
