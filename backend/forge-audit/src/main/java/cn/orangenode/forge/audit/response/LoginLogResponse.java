package cn.orangenode.forge.audit.response;

import java.time.Instant;

/**
 * 登录日志对外响应。
 *
 * <p>只包含排障需要的字段，不包含 Entity、密码、令牌或请求体。
 * {@code createdAt} 使用 {@link Instant}，序列化为带时区的 ISO 8601 字符串（UTC 以 {@code Z} 结尾）。</p>
 *
 * @param id        日志 ID，对外为字符串
 * @param username  登录尝试使用的用户名
 * @param result    登录结果，{@code success} 或 {@code failure}
 * @param reason    失败原因分类，成功时为空
 * @param clientIp  来源地址，可能为空
 * @param traceId   请求追踪编号，可能为空
 * @param createdAt 创建时间（UTC）
 */
public record LoginLogResponse(String id, String username, String result, String reason, String clientIp,
        String traceId, Instant createdAt) {
}
