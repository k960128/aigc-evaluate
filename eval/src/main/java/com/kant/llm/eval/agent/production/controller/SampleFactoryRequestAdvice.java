package com.kant.llm.eval.agent.production.controller;

import com.kant.framework.convention.Result;
import com.kant.llm.eval.agent.production.error.SampleFactoryErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** D05参数解析单独收口，避免Jackson异常包含种子正文或非法JSON原文；不影响其他模块。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = SampleFactoryController.class)
public class SampleFactoryRequestAdvice {
    /**
     * 只处理D05 Controller的参数解析异常，不干涉模型调用异常和其他模块的全局异常处理。
     * 受限输入不原样回显，避免非法JSON或校验失败消息携带种子、候选正文。
     */
    /** 请求JSON、Bean校验或路径/查询参数转换失败统一返回A053004，不回显非法输入和解析异常。 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class})
    public Result<Void> invalidRequest(Exception ignored) {
        var error = SampleFactoryErrorCode.INVALID_REQUEST;
        return new Result<Void>().setCode(error.code()).setMessage(error.message());
    }
}
