package cn.orangenode.forge.system.response;

/**
 * 角色下拉选项。
 *
 * <p>只包含表单所需的 ID、代码与名称：下拉列表不需要说明与授权明细，
 * 返回整条角色详情会让列表接口承担额外查询成本。</p>
 *
 * @param id   角色 ID，对外为字符串
 * @param code 角色代码
 * @param name 角色名称
 */
public record RoleOptionResponse(String id, String code, String name) {
}
