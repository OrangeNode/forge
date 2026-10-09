package cn.orangenode.forge.member.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 已登录用户用激活码续费 VIP 的入参。
 *
 * @param activationCode 激活码，忽略大小写、空格与短横线；与注册共用，一码只能使用一次
 */
public record MemberActivationRedeemRequest(
        @NotBlank(message = "请输入激活码")
        @Size(max = 64, message = "激活码格式不正确")
        String activationCode) {
}
