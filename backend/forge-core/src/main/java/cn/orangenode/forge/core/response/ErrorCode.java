package cn.orangenode.forge.core.response;

/**
 * 统一失败结果码。
 *
 * <p>应用可处理的响应统一使用 HTTP 200，失败原因通过响应体的 {@code code} 表达。
 * 后续阶段按需扩展，未使用的取值不提前加入。</p>
 */
public final class ErrorCode {

    /**
     * 请求参数或业务状态不满足要求。
     */
    public static final int BAD_REQUEST = 400;

    /**
     * 未认证、令牌无效或令牌过期。
     */
    public static final int UNAUTHORIZED = 401;

    /**
     * 已认证但不具备该操作权限。
     */
    public static final int FORBIDDEN = 403;

    /**
     * 目标资源不存在或调用方不可见。
     */
    public static final int NOT_FOUND = 404;

    /**
     * 服务端未预期的异常。
     */
    public static final int INTERNAL_ERROR = 500;

    /**
     * 依赖的基础设施不可用，例如 Redis 故障。
     */
    public static final int SERVICE_UNAVAILABLE = 503;

    /**
     * 工具类不允许实例化。
     */
    private ErrorCode() {
    }
}
