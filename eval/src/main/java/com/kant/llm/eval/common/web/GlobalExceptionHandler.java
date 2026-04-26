package com.kant.llm.eval.common.web;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.kant.llm.eval.common.convention.Result;
import com.kant.llm.eval.common.errorcode.BaseErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Optional;

/**
 * 全局异常处理器
 * 拦截指定异常并通过优雅构建方式返回前端信息
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.servlet.multipart.max-file-size:50MB}")
    private String maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size:100MB}")
    private String maxRequestSize;

    /**
     * 拦截参数验证异常
     */
    @SneakyThrows
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<Void> validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        FieldError firstFieldError = CollectionUtil.getFirst(bindingResult.getFieldErrors());
        String exceptionStr = Optional.ofNullable(firstFieldError)
                .map(FieldError::getDefaultMessage)
                .orElse(StrUtil.EMPTY);
        log.error("[{}] {} [ex] {}", request.getMethod(), getUrl(request), exceptionStr);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), exceptionStr);
    }

    /**
     * 拦截文件上传大小超限异常
     */
    @ExceptionHandler(value = MaxUploadSizeExceededException.class)
    public Result<Void> maxUploadSizeExceededException(HttpServletRequest request, MaxUploadSizeExceededException ex) {
        log.warn("[{}] {} [upload] 文件上传大小超限: {}", request.getMethod(), getUrl(request), ex.getMessage());
        String message;
        if (ex.getCause() instanceof IllegalStateException
                && ex.getCause().getCause() instanceof FileSizeLimitExceededException) {
            message = "上传文件大小超过限制，单个文件最大允许 " + maxFileSize;
        } else {
            message = "上传请求大小超过限制，单次请求最大允许 " + maxRequestSize;
        }
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), message);
    }

    /**
     * 拦截未捕获异常
     */
//    @ExceptionHandler(value = Exception.class)
//    public Result<Void> defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
//        log.error("[{}] {} ", request.getMethod(), getUrl(request), throwable);
//        return Results.failure();
//    }

    private String getUrl(HttpServletRequest request) {
        if (StrUtil.isBlank(request.getQueryString())) {
            return request.getRequestURL().toString();
        }
        return request.getRequestURL().toString() + "?" + request.getQueryString();
    }
}
