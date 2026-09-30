package cn.orangenode.forge.system.response;

import java.time.Instant;

/**
 * 菜单节点声明的接口权限项。
 *
 * <p>权限标识挂在菜单节点上（见 {@code sys_menu.perm_codes}），名称与说明只是它的展示资料，
 * 用于让菜单管理页面直接展示“这个权限是做什么的”，不参与任何授权判断。</p>
 *
 * @param code        权限代码，格式为 模块:资源:动作，是 {@code @PreAuthorize} 的唯一依据
 * @param name        权限名称，用于界面展示
 * @param description 权限说明，可为 {@code null}
 * @param createdAt   创建时间（UTC）
 * @param updatedAt   更新时间（UTC）
 */
public record MenuPermissionResponse(
        String code,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt) {
}
