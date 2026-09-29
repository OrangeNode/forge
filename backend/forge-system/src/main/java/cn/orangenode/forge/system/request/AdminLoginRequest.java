package cn.orangenode.forge.system.request;

import cn.orangenode.forge.system.credential.AdminCredentialPolicy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员登录入参。
 *
 * <p>只接收用户名与密码，不接收客户端指定身份、权限或令牌有效期；
 * 密码只做非空与长度上限校验：长度下限属于创建与改密规则，
 * 历史密码不应因为后来的强度规则而无法登录。</p>
 *
 * @param username 登录用户名，登录时按账号规范化规则去空格并转小写后查询
 * @param password 登录密码明文，只用于本次校验，不写入日志与响应
 */
public record AdminLoginRequest(
        @NotBlank(message = "请输入用户名")
        @Size(max = AdminCredentialPolicy.USERNAME_MAX_LENGTH, message = "用户名长度不符合要求")
        String username,

        @NotBlank(message = "请输入密码")
        @Size(max = AdminCredentialPolicy.PASSWORD_MAX_LENGTH, message = "密码长度不符合要求")
        String password) {
}
