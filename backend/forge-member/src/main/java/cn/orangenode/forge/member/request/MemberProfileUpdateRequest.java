package cn.orangenode.forge.member.request;

import cn.orangenode.forge.member.support.MemberValues;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改个人资料入参，只允许昵称与头像地址。
 *
 * @param nickname  昵称
 * @param avatarUrl 头像地址，可为空；只接受 http/https
 */
public record MemberProfileUpdateRequest(
        @NotBlank(message = "请输入昵称")
        @Size(max = MemberValues.NICKNAME_MAX_LENGTH, message = "昵称不能超过 32 个字符")
        String nickname,

        @Size(max = 512, message = "头像地址不能超过 512 个字符")
        @Pattern(regexp = "^(https?://\\S+)?$", message = "头像地址必须是 http 或 https 地址")
        String avatarUrl) {
}
