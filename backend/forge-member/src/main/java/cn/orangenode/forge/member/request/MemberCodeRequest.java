package cn.orangenode.forge.member.request;

import cn.orangenode.forge.member.support.MemberValues;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 发送邮箱验证码入参。
 *
 * @param email   收件邮箱
 * @param purpose 用途代码 REGISTER（注册）或 RESET_PASSWORD（找回密码）
 */
public record MemberCodeRequest(
        @NotBlank(message = "请输入邮箱")
        @Email(message = "邮箱格式不正确")
        @Size(max = MemberValues.EMAIL_MAX_LENGTH, message = "邮箱长度不符合要求")
        String email,

        @NotBlank(message = "请选择验证码用途")
        @Pattern(regexp = "REGISTER|RESET_PASSWORD", message = "验证码用途只能是 REGISTER 或 RESET_PASSWORD")
        String purpose) {
}
