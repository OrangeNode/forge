package cn.orangenode.forge.member.request;

import java.time.Instant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 批量生成注册激活码入参；数量上限由 {@code forge.member.activation.batch-max} 配置，在用例中校验。
 *
 * @param count    生成数量
 * @param expireAt 有效期截止时间（带时区的 ISO 8601），为空表示长期有效
 * @param remark   备注，可为空
 */
public record MemberActivationCreateRequest(
        @NotNull(message = "请输入生成数量")
        @Min(value = 1, message = "生成数量至少为 1")
        Integer count,

        Instant expireAt,

        @Size(max = 255, message = "备注不能超过 255 个字符")
        String remark) {
}
