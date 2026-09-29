package cn.orangenode.forge.framework.audit;

/**
 * 一条登录日志记录。
 *
 * <p>登录成功与失败都记录，用于排查异常登录；不包含密码、令牌或会话内容。</p>
 *
 * @param username 规范化后的登录用户名
 * @param result   结果，{@code success} 或 {@code failure}
 * @param reason   失败原因分类，成功时为空
 * @param clientIp 来源地址，可为空
 * @param traceId  请求追踪编号，可为空
 */
public record LoginLogRecord(String username, String result, String reason, String clientIp, String traceId) {
}
