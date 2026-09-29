package cn.orangenode.forge.core.response;

/**
 * 统一失败结果码。
 *
 * <p>应用可处理的响应统一使用 HTTP 200，失败原因通过响应体的 {@code code} 表达。
 * 取值与[接口设计规范]一致，只保留协议规定的状态语义，不按模块分配六位错误码。
 * 同类错误通过中文 message 区分，程序分支只判断 code。</p>
 */
public final class ErrorCode {

    /**
     * 请求参数、ID、分页、排序或 JSON 格式不合法。
     */
    public static final int BAD_REQUEST = 400;

    /**
     * 未认证、令牌缺失无效或登录凭据错误。
     */
    public static final int UNAUTHORIZED = 401;

    /**
     * 已认证但身份端不符或缺少权限。
     */
    public static final int FORBIDDEN = 403;

    /**
     * 请求路径或调用方可见的资源不存在。
     */
    public static final int NOT_FOUND = 404;

    /**
     * 请求路径存在但不支持当前 HTTP 方法。
     */
    public static final int METHOD_NOT_ALLOWED = 405;

    /**
     * 唯一约束冲突或业务状态冲突。
     */
    public static final int CONFLICT = 409;

    /**
     * 上传内容超过允许的大小上限。
     */
    public static final int PAYLOAD_TOO_LARGE = 413;

    /**
     * 请求媒体类型不被支持。
     */
    public static final int UNSUPPORTED_MEDIA_TYPE = 415;

    /**
     * 请求触发限流，需要稍后重试。
     */
    public static final int TOO_MANY_REQUESTS = 429;

    /**
     * 服务端未预期的内部异常。
     */
    public static final int INTERNAL_ERROR = 500;

    /**
     * 数据库、Redis 或存储等必要依赖暂不可用。
     */
    public static final int SERVICE_UNAVAILABLE = 503;

    /**
     * 工具类不允许实例化。
     */
    private ErrorCode() {
    }
}
