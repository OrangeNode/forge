package cn.orangenode.forge.system.request;

import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * 角色授权入参。
 *
 * <p>全量替换语义：提交的集合即角色最终拥有的权限与可见菜单，未提交的关系会被解除。
 * 角色与权限、角色与菜单是两张关系表，同一次请求内一起重建。</p>
 *
 * @param permissionIds 权限 ID 集合，允许为空表示解除全部权限
 * @param menuIds       菜单 ID 集合，允许为空表示解除全部菜单可见性
 */
public record RoleGrantRequest(
        @Size(max = 500, message = "一次最多授予 500 个权限")
        List<String> permissionIds,

        @Size(max = 500, message = "一次最多授予 500 个菜单")
        List<String> menuIds) {
}
