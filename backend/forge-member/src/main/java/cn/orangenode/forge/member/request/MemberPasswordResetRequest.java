package cn.orangenode.forge.member.request;

import cn.orangenode.forge.member.support.MemberValues;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 邮箱验证码找回密码入参；成功后该账号全部令牌失效。
 *
 * @param email       账号邮箱
 * @param code        邮箱验证码
 * @param newPassword 新密码
 */
public record MemberPasswordResetRequest(
        @NotBlank(message = "请输入邮箱")
        @Email(message = "邮箱格式不正确")
        @Size(max = MemberValues.EMAIL_MAX_LENGTH, message = "邮箱长度不符合要求")
        String email,

        @NotBlank(message = "请输入验证码")
        @Size(max = 16, message = "验证码长度不符合要求")
        String code,

        @NotBlank(message = "请输入新密码")
        @Size(min = MemberValues.PASSWORD_MIN_LENGTH, max = MemberValues.PASSWORD_MAX_LENGTH,
                message = "密码长度需为 6 到 64 个字符")
        String newPassword) {
}
