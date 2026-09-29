package cn.orangenode.forge.system.request;

/**
 * 权限列表筛选条件。
 *
 * <p>代码与名称都按包含关系模糊匹配，便于按模块前缀查找权限。</p>
 *
 * @param code 权限代码关键字，允许为空表示不筛选
 * @param name 权限名称关键字，允许为空表示不筛选
 */
public record PermissionQuery(String code, String name) {
}
