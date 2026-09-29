package cn.orangenode.forge.system.request;

import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * 管理员角色分配入参。
 *
 * <p>全量替换语义：提交的集合即该管理员最终拥有的角色，未提交的角色会被解除。
 * 空集合表示解除全部角色；服务端仍会校验角色存在性，并拒绝不存在的角色 ID。</p>
 *
 * @param roleIds 角色 ID 集合，允许为空表示不分配任何角色
 */
public record AdminRoleAssignRequest(
        @Size(max = 50, message = "一次最多分配 50 个角色")
        List<String> roleIds) {
}
