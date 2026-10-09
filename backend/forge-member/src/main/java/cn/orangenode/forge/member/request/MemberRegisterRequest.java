package cn.orangenode.forge.member.request;

import cn.orangenode.forge.member.support.MemberValues;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户名注册入参；注册成功后直接在当前设备登录。
 *
 * @param username       用户名，4—20 位字母、数字或下划线，不区分大小写唯一
 * @param password       登录密码
 * @param nickname       昵称，为空时使用用户名
 * @param activationCode 注册激活码，忽略大小写、空格与短横线；一码只能注册一次
 * @param deviceCode     App 首次安装生成的设备编号
 * @param deviceName     设备名称，仅展示
 */
public record MemberRegisterRequest(
        @NotBlank(message = "请输入用户名")
        @Pattern(regexp = MemberValues.USERNAME_PATTERN, message = "用户名需为 4 到 20 位字母、数字或下划线")
        String username,

        @NotBlank(message = "请输入密码")
        @Size(min = MemberValues.PASSWORD_MIN_LENGTH, max = MemberValues.PASSWORD_MAX_LENGTH,
                message = "密码长度需为 6 到 64 个字符")
        String password,

        @Size(max = MemberValues.NICKNAME_MAX_LENGTH, message = "昵称不能超过 32 个字符")
        String nickname,

        @NotBlank(message = "请输入激活码")
        @Size(max = 64, message = "激活码格式不正确")
        String activationCode,

        @NotBlank(message = "缺少设备编号")
        @Pattern(regexp = MemberValues.DEVICE_CODE_PATTERN, message = "设备编号格式不正确")
        String deviceCode,

        @NotBlank(message = "缺少设备名称")
        @Size(max = 64, message = "设备名称不能超过 64 个字符")
        String deviceName) {
}
