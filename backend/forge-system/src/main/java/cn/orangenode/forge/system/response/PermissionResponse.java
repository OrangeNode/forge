package cn.orangenode.forge.system.response;

import java.time.Instant;

/**
 * 权限信息。
 *
 * <p>权限代码用于前端隐藏无权限的入口，后端每个受保护接口仍独立执行权限校验；
 * 权限代码不允许修改，因此本结构同时用于创建、修改与列表响应。</p>
 *
 * @param id          权限 ID，对外为字符串
 * @param code        权限代码，格式为 {@code 模块:资源:动作}
 * @param name        权限名称
 * @param description 权限说明，允许为空
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 */
public record PermissionResponse(String id, String code, String name, String description, Instant createdAt,
        Instant updatedAt) {
}
