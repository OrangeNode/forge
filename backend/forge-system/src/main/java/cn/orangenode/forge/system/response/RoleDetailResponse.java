package cn.orangenode.forge.system.response;

import java.time.Instant;
import java.util.List;

/**
 * 角色详情。
 *
 * <p>包含已授予的权限与菜单 ID，供授权页面回显勾选状态；ID 全部为字符串，
 * 与路径、请求体中的 ID 口径一致。</p>
 *
 * @param id            角色 ID，对外为字符串
 * @param code          角色代码
 * @param name          角色名称
 * @param description   角色说明，允许为空
 * @param sortNo        展示顺序
 * @param superRole     是否为配置识别的内置超级管理员角色
 * @param permissionIds 已授予的权限 ID，没有授权时为空列表
 * @param menuIds       已授予的菜单 ID，没有授权时为空列表
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 */
public record RoleDetailResponse(String id, String code, String name, String description, Integer sortNo,
        boolean superRole, List<String> permissionIds, List<String> menuIds, Instant createdAt, Instant updatedAt) {
}
