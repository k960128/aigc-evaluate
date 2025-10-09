package com.chinatelecom.aigc.evaluate.common.handler;

import cn.hutool.core.util.ObjUtil;
import com.chinatelecom.aigc.evaluate.common.exception.ServiceException;
import com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil;
import com.chinatelecom.aigc.evaluate.common.pojo.CommonResult;
import com.chinatelecom.aigc.evaluate.common.util.collection.SetUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@ResponseBody
@ControllerAdvice
public class GlobalExceptionHandler {
    public static final Set<String> IGNORE_ERROR_MESSAGES = SetUtils.asSet("无效的刷新令牌");

    @ExceptionHandler(value = ServiceException.class)
    public CommonResult<?> handleRuntimeExceptions(ServiceException ex) {
        // 不包含的时候，才进行打印，避免 ex 堆栈过多
        if (!IGNORE_ERROR_MESSAGES.contains(ex.getMessage())) {
            // 即使打印，也只打印第一层 StackTraceElement，并且使用 warn 在控制台输出，更容易看到
            try {
                StackTraceElement[] stackTraces = ex.getStackTrace();
                for (StackTraceElement stackTrace : stackTraces) {
                    if (ObjUtil.notEqual(stackTrace.getClassName(), ServiceExceptionUtil.class.getName())) {
                        log.warn("[serviceExceptionHandler]\n\t{}", stackTrace);
                        break;
                    }
                }
            } catch (Exception ignored) {
                // 忽略日志，避免影响主流程
            }
        }
        return CommonResult.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParams(MissingServletRequestParameterException ex) {
        Map<String, String> errors = new HashMap<>();
        String paramName = ex.getParameterName();
        String errorMessage = "缺少必需的请求参数: " + paramName;
        errors.put(paramName, errorMessage);
        return ResponseEntity.badRequest().body(errors);
    }

}
