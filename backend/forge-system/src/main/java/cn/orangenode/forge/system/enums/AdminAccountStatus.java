package cn.orangenode.forge.system.enums;

/**
 * 管理员账号状态代码。
 *
 * <p>使用稳定的小写代码而不是枚举序号：数据库、接口与前端都只认代码，
 * 新增状态不会因为顺序变化改变既有含义。</p>
 */
public enum AdminAccountStatus {

    /**
     * 启用：允许登录，令牌有效。
     */
    enabled,

    /**
     * 停用：拒绝登录，既有令牌在下一次请求即失效。
     */
    disabled;

    /**
     * 判断状态代码是否表示启用。
     *
     * @param code 数据库中的状态代码
     * @return 启用时返回 {@code true}，其他取值一律视为不可用
     */
    public static boolean isEnabled(String code) {
        return enabled.name().equals(code);
    }
}
