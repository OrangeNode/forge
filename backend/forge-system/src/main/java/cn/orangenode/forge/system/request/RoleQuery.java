package cn.orangenode.forge.system.request;

/**
 * 角色列表筛选条件。
 *
 * <p>名称按包含关系模糊匹配，代码不参与筛选：代码是稳定标识，按代码查找属于详情查询语义。</p>
 *
 * @param name 角色名称关键字，允许为空表示不筛选
 */
public record RoleQuery(String name) {
}
