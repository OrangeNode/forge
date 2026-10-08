package cn.orangenode.forge.member.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 后台启停普通用户入参。
 *
 * @param status 目标状态 ENABLED 或 DISABLED
 */
public record MemberStatusRequest(
        @NotBlank(message = "请选择账号状态")
        @Pattern(regexp = "ENABLED|DISABLED", message = "账号状态只能是 ENABLED 或 DISABLED")
        String status) {
}
