package cn.orangenode.forge.member.request;

import java.time.Instant;

import jakarta.validation.constraints.Size;

/**
 * 后台直接设置 VIP 到期时间入参；到期时间为空表示取消 VIP。
 *
 * @param expireAt 到期时间（带时区的 ISO 8601），为空表示取消
 * @param remark   备注，可为空
 */
public record MemberVipSetRequest(
        Instant expireAt,

        @Size(max = 255, message = "备注不能超过 255 个字符")
        String remark) {
}
