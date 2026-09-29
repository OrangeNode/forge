package cn.orangenode.forge.core.exception;

import cn.orangenode.forge.core.response.ErrorCode;

/**
 * 业务异常。
 *
 * <p>业务规则、授权归属与状态流转等可预期失败通过本异常表达，
 * 由 Web 层统一转换为 HTTP 200 + 对应 {@code body.code}；调用方不得把异常转成成功响应。</p>
 *
 * <p>message 面向调用者，只说明业务结论，不携带堆栈、SQL、连接信息或凭据；
 * 服务端排查依赖同一响应中的 traceId 关联日志。</p>
 */
public class BusinessException extends RuntimeException {

    /**
     * 序列化标识；异常只在进程内传递，声明固定值以满足序列化契约。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 对外失败结果码，取值来自 {@link ErrorCode}。
     */
    private final int code;

    /**
     * 构造业务异常。
     *
     * @param code    失败结果码，使用 {@link ErrorCode} 中的数值
     * @param message 面向调用者的中文提示
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造业务异常并保留原始原因。
     *
     * <p>原始原因只用于服务端日志关联，不写入对外响应。</p>
     *
     * @param code    失败结果码，使用 {@link ErrorCode} 中的数值
     * @param message 面向调用者的中文提示
     * @param cause   原始异常
     */
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 以指定结果码创建业务异常。
     *
     * @param code    失败结果码，使用 {@link ErrorCode} 中的数值
     * @param message 面向调用者的中文提示
     * @return 业务异常实例
     */
    public static BusinessException of(int code, String message) {
        return new BusinessException(code, message);
    }

    /**
     * 取得对外失败结果码。
     *
     * @return 失败结果码
     */
    public int getCode() {
        return code;
    }
}
