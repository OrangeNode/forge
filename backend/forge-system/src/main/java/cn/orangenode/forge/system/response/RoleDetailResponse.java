package cn.orangenode.forge.system.response;

import java.time.Instant;
import java.util.List;

/**
 * 角色详情。
 *
 * <p>授权以菜单为单位：{@code menuIds} 是已授予的菜单节点，供授权页面回显勾选状态；
 * {@code permissionCodes} 是这些菜单节点声明的接口权限标识合并去重后的结果，
 * 供界面展示“这个角色实际能调哪些接口”，不作为可独立编辑的字段——它由菜单授权推导。
 * ID 全部为字符串，与路径、请求体中的 ID 口径一致。</p>
 *
 * @param id              角色 ID，对外为字符串
 * @param code            角色代码
 * @param name            角色名称
 * @param description     角色说明，允许为空
 * @param sortNo          展示顺序
 * @param superRole       是否为配置识别的内置超级管理员角色
 * @param permissionCodes 已授予菜单声明的权限标识，没有授权时为空列表
 * @param menuIds         已授予的菜单 ID，没有授权时为空列表
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 */
public record RoleDetailResponse(String id, String code, String name, String description, Integer sortNo,
        boolean superRole, List<String> permissionCodes, List<String> menuIds, Instant createdAt, Instant updatedAt) {
}
