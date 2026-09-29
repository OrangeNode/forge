package cn.orangenode.forge.system.response;

/**
 * 不含明细的节点结果。
 *
 * <p>用于只需回传对象 ID 的写操作响应，例如菜单增改与密码重置；
 * 接口规范要求对外 ID 为字符串，因此这里不直接返回 Long。</p>
 *
 * @param id 对象 ID，对外为字符串
 */
public record SystemNodeResponse(String id) {
}
