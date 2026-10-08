package cn.orangenode.forge.member.request;

import cn.orangenode.forge.member.support.MemberValues;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 普通用户用户名密码登录入参；密码只校验非空与长度上限。
 *
 * @param username   用户名
 * @param password   登录密码
 * @param deviceCode App 首次安装生成的设备编号
 * @param deviceName 设备名称，仅展示
 */
public record MemberLoginRequest(
        @NotBlank(message = "请输入用户名")
        @Size(max = MemberValues.USERNAME_MAX_LENGTH, message = "用户名或密码错误")
        String username,

        @NotBlank(message = "请输入密码")
        @Size(max = MemberValues.PASSWORD_MAX_LENGTH, message = "密码长度不符合要求")
        String password,

        @NotBlank(message = "缺少设备编号")
        @Pattern(regexp = MemberValues.DEVICE_CODE_PATTERN, message = "设备编号格式不正确")
        String deviceCode,

        @NotBlank(message = "缺少设备名称")
        @Size(max = 64, message = "设备名称不能超过 64 个字符")
        String deviceName) {
}
