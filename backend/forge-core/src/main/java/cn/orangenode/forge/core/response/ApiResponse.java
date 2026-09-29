package cn.orangenode.forge.core.response;

import java.util.UUID;

/**
 * 统一接口响应体。
 *
 * <p>应用可以处理的响应统一使用 HTTP 200，调用方依据 {@code code} 判断业务结果：
 * 成功为 {@link #SUCCESS_CODE}，失败使用 {@link ErrorCode} 中的数值。</p>
 *
 * @param code    业务结果码，0 表示成功
 * @param message 面向调用方的提示信息
 * @param data    业务数据，失败时为 {@code null}
 * @param traceId 请求追踪编号，用于关联服务端日志
 * @param <T>     业务数据类型
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    /**
     * 成功结果码。
     */
    public static final int SUCCESS_CODE = 0;

    /**
     * 成功响应使用的默认提示信息。
     */
    public static final String SUCCESS_MESSAGE = "操作成功";

    /**
     * 生成带默认成功码与提示信息的成功响应。
     *
     * @param data 业务数据，允许为 {@code null}
     * @param <T>  业务数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> ok(T data) {
        return success(data);
    }

    /**
     * 生成带默认成功码与提示信息的成功响应，适用于无业务数据的用例。
     *
     * @param <T> 业务数据类型
     * @return 不含业务数据的成功响应
     */
    public static <T> ApiResponse<T> ok() {
        return success(null);
    }

    /**
     * 生成成功响应，并在构造时分配新的追踪编号。
     *
     * @param data 业务数据，允许为 {@code null}
     * @param <T>  业务数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data, newTraceId());
    }

    /**
     * 生成成功响应，允许自定义提示信息。
     *
     * @param message 面向调用方的成功提示，空白时使用默认提示
     * @param data    业务数据，允许为 {@code null}
     * @param <T>     业务数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        String text = (message == null || message.isBlank()) ? SUCCESS_MESSAGE : message;
        return new ApiResponse<>(SUCCESS_CODE, text, data, newTraceId());
    }

    /**
     * 生成失败响应，不携带业务数据。
     *
     * @param code    失败结果码，使用 {@link ErrorCode} 中的数值
     * @param message 面向调用方的失败提示
     * @param <T>     业务数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> failure(int code, String message) {
        return new ApiResponse<>(code, message, null, newTraceId());
    }

    /**
     * 判断给定结果码是否表示成功。
     *
     * <p>实现为静态方法而不是实例访问器：record 的实例访问器会被 Jackson 当作属性输出，
     * 会给对外结构额外加一个 `success` 字段。保持 core 不依赖任何 JSON 注解库。</p>
     *
     * @param code 结果码
     * @return 等于 {@link #SUCCESS_CODE} 时返回 {@code true}
     */
    public static boolean isSuccess(int code) {
        return code == SUCCESS_CODE;
    }

    /**
     * 生成 32 位无连字符的随机追踪编号。
     *
     * <p>追踪编号只用于日志关联，不承载身份或权限信息。</p>
     *
     * @return 追踪编号字符串
     */
    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
