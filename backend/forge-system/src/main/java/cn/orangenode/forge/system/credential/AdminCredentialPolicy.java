package cn.orangenode.forge.system.credential;

import java.util.Locale;

/**
 * 管理员用户名与密码的统一规则。
 *
 * <p>用户名在保存与查询前统一去空格并转为小写，因此登录不区分大小写，
 * 也不会出现 {@code Admin} 与 {@code admin} 两个账号；密码只校验长度范围，
 * 强度要求由后续密码策略任务补充，不在各处 Controller 自行定义。</p>
 *
 * <p>规则集中在此类，Validation 注解、初始管理员引导与登录用例引用同一组常量，
 * 避免同一约束出现多个来源。</p>
 */
public final class AdminCredentialPolicy {

    /**
     * 用户名最小长度。
     */
    public static final int USERNAME_MIN_LENGTH = 4;

    /**
     * 用户名最大长度，与数据库列长度保持一致。
     */
    public static final int USERNAME_MAX_LENGTH = 32;

    /**
     * 密码最小长度，只用于创建与修改密码。
     */
    public static final int PASSWORD_MIN_LENGTH = 8;

    /**
     * 密码最大长度，与数据库编码结果列长度和登录校验保持一致。
     */
    public static final int PASSWORD_MAX_LENGTH = 64;

    /**
     * 工具类不允许实例化。
     */
    private AdminCredentialPolicy() {
    }

    /**
     * 规范化用户名：去首尾空格并转为小写。
     *
     * @param username 原始用户名，允许为 {@code null}
     * @return 规范化后的用户名，入参为空时返回 {@code null}
     */
    public static String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * 判断密码长度是否满足创建账号的要求。
     *
     * @param password 原始密码，允许为 {@code null}
     * @return 满足长度范围时返回 {@code true}
     */
    public static boolean isValidNewPassword(String password) {
        if (password == null) {
            return false;
        }
        return password.length() >= PASSWORD_MIN_LENGTH && password.length() <= PASSWORD_MAX_LENGTH;
    }

    /**
     * 判断用户名长度是否满足创建账号的要求。
     *
     * @param username 规范化后的用户名，允许为 {@code null}
     * @return 满足长度范围时返回 {@code true}
     */
    public static boolean isValidUsername(String username) {
        if (username == null) {
            return false;
        }
        return username.length() >= USERNAME_MIN_LENGTH && username.length() <= USERNAME_MAX_LENGTH;
    }
}
