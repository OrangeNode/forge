package cn.orangenode.forge.member.request;

import cn.orangenode.forge.member.support.MemberValues;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 后台新建普通用户入参；关闭公开注册后账号由管理员创建。
 *
 * @param username 用户名，4—20 位字母、数字或下划线，不区分大小写唯一
 * @param password 初始密码
 * @param nickname 昵称，为空时使用用户名
 * @param vipDays  同时开通的 VIP 天数，为空表示不开通
 */
public record MemberCreateRequest(
        @NotBlank(message = "请输入用户名")
        @Pattern(regexp = MemberValues.USERNAME_PATTERN, message = "用户名需为 4 到 20 位字母、数字或下划线")
        String username,

        @NotBlank(message = "请输入初始密码")
        @Size(min = MemberValues.PASSWORD_MIN_LENGTH, max = MemberValues.PASSWORD_MAX_LENGTH,
                message = "密码长度需为 6 到 64 个字符")
        String password,

        @Size(max = MemberValues.NICKNAME_MAX_LENGTH, message = "昵称不能超过 32 个字符")
        String nickname,

        @Min(value = 1, message = "VIP 天数至少为 1 天")
        @Max(value = 3650, message = "VIP 天数不能超过 3650 天")
        Integer vipDays) {
}
