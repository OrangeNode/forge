package cn.orangenode.forge.system.response;

import java.time.Instant;
import java.util.List;

/**
 * 管理员详情。
 *
 * <p>列表项与详情共用同一结构，避免前端为同一实体维护两套类型。响应只包含展示字段：
 * 不返回密码编码结果、令牌或任何凭据；时间按 UTC 语义以带时区的时刻输出。</p>
 *
 * @param id          管理员 ID，对外为字符串
 * @param username    登录用户名
 * @param displayName 显示名称
 * @param status      账号状态代码，{@code enabled} 或 {@code disabled}
 * @param roles       当前拥有的角色摘要，没有角色时为空列表
 * @param lastLoginAt 最近一次登录成功时间，从未登录时为空
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 */
public record AdminDetailResponse(String id, String username, String displayName, String status,
        List<RoleOptionResponse> roles, Instant lastLoginAt, Instant createdAt, Instant updatedAt) {
}
