package cn.orangenode.forge.member.request;

import cn.orangenode.forge.member.support.MemberValues;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码入参；成功后全部令牌失效，需要重新登录。
 *
 * @param oldPassword 原密码
 * @param newPassword 新密码
 */
public record MemberPasswordChangeRequest(
        @NotBlank(message = "请输入原密码")
        @Size(max = MemberValues.PASSWORD_MAX_LENGTH, message = "密码长度不符合要求")
        String oldPassword,

        @NotBlank(message = "请输入新密码")
        @Size(min = MemberValues.PASSWORD_MIN_LENGTH, max = MemberValues.PASSWORD_MAX_LENGTH,
                message = "密码长度需为 6 到 64 个字符")
        String newPassword) {
}
