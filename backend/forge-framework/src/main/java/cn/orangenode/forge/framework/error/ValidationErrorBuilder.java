package cn.orangenode.forge.framework.error;

import java.util.List;

import org.springframework.validation.BindingResult;

import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.core.response.FieldError;

/**
 * 参数校验失败错误响应构建器。
 *
 * <p>把 Spring 的字段错误转换为协议约定的 {@code data.fieldErrors}，每项只含字段名与中文说明。
 * 失败响应不回显被拒绝的原值，也不包含类名、SQL 或堆栈。</p>
 *
 * <p>校验失败必须终止原业务调用：参数绑定或方法校验阶段即抛错并进入全局异常处理，
 * 业务服务不会被调用。</p>
 */
public final class ValidationErrorBuilder {

    /**
     * 校验失败响应的统一中文提示。
     */
    public static final String MESSAGE = "请求参数校验失败";

    /**
     * 工具类不允许实例化。
     */
    private ValidationErrorBuilder() {
    }

    /**
     * 依据 Spring 绑定结果构建字段错误列表。
     *
     * @param bindingResult 参数绑定与校验结果
     * @return 字段错误列表，可能为空但不会为 {@code null}
     */
    public static List<FieldError> fromBindingResult(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(item -> new FieldError(item.getField(), item.getDefaultMessage()))
                .toList();
    }

    /**
     * 依据字段名与说明构建单个字段错误。
     *
     * @param field   字段名或参数名
     * @param message 中文说明
     * @return 字段错误
     */
    public static FieldError fieldError(String field, String message) {
        return new FieldError(field, message);
    }

    /**
     * 构建校验失败响应体中的数据部分。
     *
     * @param errors 字段错误列表
     * @return 与 {@code ApiResponse.data} 对应的结构
     */
    public static ValidationErrorData data(List<FieldError> errors) {
        return new ValidationErrorData(errors);
    }

    /**
     * 构建使用指定追踪编号的校验失败响应。
     *
     * @param errors  字段错误列表
     * @param traceId 当前请求追踪编号
     * @return 校验失败响应
     */
    public static ApiResponse<ValidationErrorData> response(List<FieldError> errors, String traceId) {
        return ApiResponse.failureWithTrace(ErrorCode.BAD_REQUEST, MESSAGE, data(errors), traceId);
    }

    /**
     * 校验失败响应体中的数据部分。
     *
     * @param fieldErrors 字段错误列表
     */
    public record ValidationErrorData(List<FieldError> fieldErrors) {

        /**
         * 规范化字段错误列表，保证对外结构始终是数组而不是 {@code null}。
         *
         * @param fieldErrors 字段错误列表，允许为 {@code null}
         */
        public ValidationErrorData {
            fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        }
    }
}
