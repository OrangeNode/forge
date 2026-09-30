package cn.orangenode.forge.system.request;

import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * 角色授权入参。
 *
 * <p>全量替换语义：提交的菜单集合即角色最终可见的菜单节点，未提交的关系会被解除。
 * 接口权限不再单独提交——它由被授予菜单节点上声明的权限标识决定，
 * 因此一次授权只有一张关系表需要重建。</p>
 *
 * @param menuIds 菜单 ID 集合，允许为空表示解除全部菜单与权限
 */
public record RoleGrantRequest(
        @Size(max = 500, message = "一次最多授予 500 个菜单")
        List<String> menuIds) {
}
