package cn.orangenode.forge.member.request;

import cn.orangenode.forge.member.support.MemberValues;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 邮箱验证码注册入参；注册成功后直接在当前设备登录。
 *
 * @param email      注册邮箱
 * @param code       邮箱验证码
 * @param password   登录密码
 * @param nickname   昵称，为空时使用邮箱前缀
 * @param deviceCode App 首次安装生成的设备编号
 * @param deviceName 设备名称，仅展示
 */
public record MemberRegisterRequest(
        @NotBlank(message = "请输入邮箱")
        @Email(message = "邮箱格式不正确")
        @Size(max = MemberValues.EMAIL_MAX_LENGTH, message = "邮箱长度不符合要求")
        String email,

        @NotBlank(message = "请输入验证码")
        @Size(max = 16, message = "验证码长度不符合要求")
        String code,

        @NotBlank(message = "请输入密码")
        @Size(min = MemberValues.PASSWORD_MIN_LENGTH, max = MemberValues.PASSWORD_MAX_LENGTH,
                message = "密码长度需为 6 到 64 个字符")
        String password,

        @Size(max = MemberValues.NICKNAME_MAX_LENGTH, message = "昵称不能超过 32 个字符")
        String nickname,

        @NotBlank(message = "缺少设备编号")
        @Pattern(regexp = MemberValues.DEVICE_CODE_PATTERN, message = "设备编号格式不正确")
        String deviceCode,

        @NotBlank(message = "缺少设备名称")
        @Size(max = 64, message = "设备名称不能超过 64 个字符")
        String deviceName) {
}
