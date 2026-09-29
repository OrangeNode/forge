package cn.orangenode.forge.framework.web.error;

import java.util.ArrayList;
import java.util.List;

import com.baomidou.dynamic.datasource.exception.CannotFindDataSourceException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.exception.DependencyUnavailableException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.core.response.FieldError;
import cn.orangenode.forge.framework.error.ValidationErrorBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局异常处理器。
 *
 * <p>覆盖业务异常、参数校验、请求格式、路径与方法、数据库约束、依赖不可用及未知异常，
 * 全部通过 {@link WebErrorWriter} 写出 HTTP 200 + 对应 {@code body.code}，
 * 使前端只依据响应体即可完成失败分流。</p>
 *
 * <p>处理器写出错误响应后立即返回，业务方法不会再被执行；未知异常只对外给出通用说明，
 * 内部细节与堆栈只写入服务端日志，并通过 traceId 关联。</p>
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 统一错误响应写出组件，ControllerAdvice 与其他错误出口共用。
     */
    private final WebErrorWriter errorWriter;

    /**
     * 构造全局异常处理器。
     *
     * @param errorWriter 统一错误响应写出组件
     */
    public GlobalExceptionHandler(WebErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    /**
     * 处理业务规则失败。
     *
     * <p>业务异常自带结果码，直接透传，不改变语义；堆栈按警告记录便于定位规则来源。</p>
     *
     * @param exception 业务异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Void> handleBusinessException(BusinessException exception, HttpServletRequest request,
            HttpServletResponse response) {
        log.warn("业务处理失败，traceId={}，code={}，message={}", errorWriter.resolveTraceId(request), exception.getCode(),
                exception.getMessage());
        errorWriter.write(request, response, exception.getCode(), exception.getMessage());
        return ResponseEntity.ok().build();
    }

    /**
     * 处理请求体上 {@code @Valid} 触发的字段校验失败。
     *
     * @param exception 参数校验异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpServletRequest request, HttpServletResponse response) {
        writeValidationFailure(ValidationErrorBuilder.fromBindingResult(exception.getBindingResult()), request,
                response);
        return ResponseEntity.ok().build();
    }

    /**
     * 处理方法参数校验失败，例如路径变量或查询参数上的约束注解。
     *
     * @param exception 方法参数校验异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Void> handleHandlerMethodValidation(HandlerMethodValidationException exception,
            HttpServletRequest request, HttpServletResponse response) {
        List<FieldError> errors = new ArrayList<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            String field = result.getMethodParameter().getParameterName();
            result.getResolvableErrors()
                    .forEach(item -> errors.add(ValidationErrorBuilder.fieldError(field, item.getDefaultMessage())));
        }
        writeValidationFailure(errors, request, response);
        return ResponseEntity.ok().build();
    }

    /**
     * 处理服务层方法上约束注解触发的校验失败。
     *
     * @param exception 约束校验异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Void> handleConstraintViolation(ConstraintViolationException exception,
            HttpServletRequest request, HttpServletResponse response) {
        List<FieldError> errors = exception.getConstraintViolations().stream()
                .map(this::toFieldError)
                .toList();
        writeValidationFailure(errors, request, response);
        return ResponseEntity.ok().build();
    }

    /**
     * 处理 JSON 请求体解析失败，例如语法错误或类型不匹配。
     *
     * @param exception 请求体读取异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Void> handleMessageNotReadable(HttpMessageNotReadableException exception,
            HttpServletRequest request, HttpServletResponse response) {
        log.warn("请求体解析失败，traceId={}，原因={}", errorWriter.resolveTraceId(request), exception.getMessage());
        errorWriter.write(request, response, ErrorCode.BAD_REQUEST, "请求体格式不正确，请检查 JSON 结构");
        return ResponseEntity.ok().build();
    }

    /**
     * 处理路径变量或查询参数类型转换失败。
     *
     * @param exception 类型不匹配异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Void> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
            HttpServletRequest request, HttpServletResponse response) {
        writeValidationFailure(List.of(ValidationErrorBuilder.fieldError(exception.getName(),
                "参数类型不正确，无法转换为期望类型")), request, response);
        return ResponseEntity.ok().build();
    }

    /**
     * 处理缺少必需请求参数的情况。
     *
     * @param exception 参数缺失异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Void> handleMissingParameter(MissingServletRequestParameterException exception,
            HttpServletRequest request, HttpServletResponse response) {
        writeValidationFailure(List.of(ValidationErrorBuilder.fieldError(exception.getParameterName(), "缺少必需的请求参数")),
                request, response);
        return ResponseEntity.ok().build();
    }

    /**
     * 处理上传内容超过配置上限的情况。
     *
     * @param exception 上传超限异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Void> handleMaxUploadSize(MaxUploadSizeExceededException exception,
            HttpServletRequest request, HttpServletResponse response) {
        log.warn("上传内容超过上限，traceId={}", errorWriter.resolveTraceId(request));
        errorWriter.write(request, response, ErrorCode.PAYLOAD_TOO_LARGE, "上传内容超过允许的大小上限");
        return ResponseEntity.ok().build();
    }

    /**
     * 处理请求媒体类型不受支持的情况。
     *
     * @param exception 媒体类型异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request, HttpServletResponse response) {
        errorWriter.write(request, response, ErrorCode.UNSUPPORTED_MEDIA_TYPE, "请求的媒体类型不受支持");
        return ResponseEntity.ok().build();
    }

    /**
     * 处理请求方法不被支持的情况。
     *
     * @param exception 方法不支持异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request, HttpServletResponse response) {
        errorWriter.write(request, response, ErrorCode.METHOD_NOT_ALLOWED, "请求方法不被支持");
        return ResponseEntity.ok().build();
    }

    /**
     * 处理静态资源与未匹配路径的 404。
     *
     * @param exception 资源不存在异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException exception, HttpServletRequest request,
            HttpServletResponse response) {
        errorWriter.write(request, response, ErrorCode.NOT_FOUND, "请求的路径不存在");
        return ResponseEntity.ok().build();
    }

    /**
     * 处理数据库唯一约束冲突。
     *
     * @param exception 唯一键冲突异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Void> handleDuplicateKey(DuplicateKeyException exception, HttpServletRequest request,
            HttpServletResponse response) {
        log.warn("唯一约束冲突，traceId={}", errorWriter.resolveTraceId(request), exception);
        errorWriter.write(request, response, ErrorCode.CONFLICT, "数据已存在，请勿重复提交");
        return ResponseEntity.ok().build();
    }

    /**
     * 处理其他数据完整性冲突，例如非空或外键约束。
     *
     * @param exception 数据完整性异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Void> handleDataIntegrityViolation(DataIntegrityViolationException exception,
            HttpServletRequest request, HttpServletResponse response) {
        log.warn("数据完整性冲突，traceId={}", errorWriter.resolveTraceId(request), exception);
        errorWriter.write(request, response, ErrorCode.CONFLICT, "数据状态冲突，请刷新后重试");
        return ResponseEntity.ok().build();
    }

    /**
     * 处理 Redis 连接失败。
     *
     * <p>依赖不可用时明确失败，不使用放行或降级分支；认证相关逻辑不得因 Redis 故障而跳过校验。</p>
     *
     * @param exception Redis 连接异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<Void> handleRedisConnectionFailure(RedisConnectionFailureException exception,
            HttpServletRequest request, HttpServletResponse response) {
        log.error("Redis 连接失败，traceId={}", errorWriter.resolveTraceId(request), exception);
        errorWriter.write(request, response, ErrorCode.SERVICE_UNAVAILABLE, "服务依赖暂不可用，请稍后重试");
        return ResponseEntity.ok().build();
    }

    /**
     * 处理请求了未注册的数据源名称。
     *
     * <p>数据源名称只来自可信配置与后端代码；出现该异常说明代码或配置不一致，
     * 属于服务端错误，明确失败而不回退主库，避免把数据写到错误的位置。</p>
     *
     * @param exception 未找到数据源异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(CannotFindDataSourceException.class)
    public ResponseEntity<Void> handleCannotFindDataSource(CannotFindDataSourceException exception,
            HttpServletRequest request, HttpServletResponse response) {
        log.error("数据源不存在，traceId={}", errorWriter.resolveTraceId(request), exception);
        errorWriter.write(request, response, ErrorCode.SERVICE_UNAVAILABLE, "服务依赖暂不可用，请稍后重试");
        return ResponseEntity.ok().build();
    }

    /**
     * 处理数据库等数据访问异常。
     *
     * <p>连接获取失败映射为 503，其余访问异常按未预期错误处理为 500；
     * 对外只给通用说明，真实原因写入服务端日志。</p>
     *
     * @param exception 数据访问异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Void> handleDataAccess(DataAccessException exception, HttpServletRequest request,
            HttpServletResponse response) {
        String traceId = errorWriter.resolveTraceId(request);
        if (exception instanceof DataAccessResourceFailureException) {
            log.error("数据库连接不可用，traceId={}", traceId, exception);
            errorWriter.write(request, response, ErrorCode.SERVICE_UNAVAILABLE, "服务依赖暂不可用，请稍后重试");
            return ResponseEntity.ok().build();
        }
        log.error("数据访问失败，traceId={}", traceId, exception);
        errorWriter.write(request, response, ErrorCode.INTERNAL_ERROR, "服务器内部错误，请稍后重试");
        return ResponseEntity.ok().build();
    }

    /**
     * 处理显式抛出的依赖不可用异常。
     *
     * @param exception 依赖不可用异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(DependencyUnavailableException.class)
    public ResponseEntity<Void> handleDependencyUnavailable(DependencyUnavailableException exception,
            HttpServletRequest request, HttpServletResponse response) {
        log.error("依赖不可用，traceId={}", errorWriter.resolveTraceId(request), exception);
        errorWriter.write(request, response, exception.getCode(), exception.getMessage());
        return ResponseEntity.ok().build();
    }

    /**
     * 处理未预期的其他异常。
     *
     * @param exception 未预期异常
     * @param request   当前请求
     * @param response  当前响应
     * @return 已写出响应体的空返回
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Void> handleUnexpected(Exception exception, HttpServletRequest request,
            HttpServletResponse response) {
        log.error("未预期异常，traceId={}", errorWriter.resolveTraceId(request), exception);
        errorWriter.write(request, response, ErrorCode.INTERNAL_ERROR, "服务器内部错误，请稍后重试");
        return ResponseEntity.ok().build();
    }

    /**
     * 写出参数校验失败响应，统一使用 400 与 {@code data.fieldErrors} 结构。
     *
     * @param errors   字段错误列表
     * @param request  当前请求
     * @param response 当前响应
     */
    private void writeValidationFailure(List<FieldError> errors, HttpServletRequest request,
            HttpServletResponse response) {
        log.warn("参数校验失败，traceId={}，字段数={}", errorWriter.resolveTraceId(request), errors.size());
        errorWriter.write(request, response, ErrorCode.BAD_REQUEST, ValidationErrorBuilder.MESSAGE,
                ValidationErrorBuilder.data(errors));
    }

    /**
     * 把 Jakarta 约束违规转换为对外字段错误。
     *
     * @param violation 约束违规
     * @return 只含字段名与说明的字段错误
     */
    private FieldError toFieldError(ConstraintViolation<?> violation) {
        return ValidationErrorBuilder.fieldError(violation.getPropertyPath().toString(), violation.getMessage());
    }
}
