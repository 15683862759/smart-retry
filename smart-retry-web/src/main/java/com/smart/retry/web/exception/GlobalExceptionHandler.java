package com.smart.retry.web.exception;

import com.smart.retry.web.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>将参数校验错误映射为 400，业务异常透出业务文案；
 * 运行时异常和未知异常只记录详细日志，对客户端返回统一文案，避免泄露内部细节。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 处理业务异常，返回异常携带的业务状态码和可展示文案。
     *
     * @param e 业务异常
     * @return 错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("[GlobalExceptionHandler#handleBusinessException]业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }
    
    /**
     * 处理 @Valid 请求体校验异常，并合并字段错误信息。
     *
     * @param e 参数校验异常
     * @return 400 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[GlobalExceptionHandler#handleValidationException]参数校验失败: {}", message);
        return Result.error(400, message);
    }
    
    /**
     * 处理表单绑定异常，并合并字段错误信息。
     *
     * @param e 绑定异常
     * @return 400 错误响应
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[GlobalExceptionHandler#handleBindException]参数绑定失败: {}", message);
        return Result.error(400, message);
    }
    
    /**
     * 处理 Bean Validation 约束违反异常。
     *
     * @param e 约束违反异常
     * @return 400 错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("[GlobalExceptionHandler#handleConstraintViolationException]约束违反: {}", message);
        return Result.error(400, message);
    }
    
    /**
     * 处理非法参数异常。
     *
     * @param e 非法参数异常
     * @return 400 错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("[GlobalExceptionHandler#handleIllegalArgumentException]非法参数: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }
    
    /**
     * 处理未捕获的运行时异常。详细堆栈只写日志，客户端收到统一文案。
     *
     * @param e 运行时异常
     * @return 500 错误响应
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("[GlobalExceptionHandler#handleRuntimeException]运行时异常", e);
        return Result.error("系统异常，请联系管理员");
    }
    
    /**
     * 处理其他未知异常。详细堆栈只写日志，客户端收到统一文案。
     *
     * @param e 未知异常
     * @return 500 错误响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("[GlobalExceptionHandler#handleException]未知异常", e);
        return Result.error("系统异常，请联系管理员");
    }
}
