package cn.orangenode.forge.member.request;

import cn.orangenode.forge.member.support.MemberValues;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 后台重置普通用户密码入参；重置后该用户全部令牌失效。
 *
 * @param password 新密码
 */
public record MemberAdminPasswordRequest(
        @NotBlank(message = "请输入新密码")
        @Size(min = MemberValues.PASSWORD_MIN_LENGTH, max = MemberValues.PASSWORD_MAX_LENGTH,
                message = "密码长度需为 6 到 64 个字符")
        String password) {
}
