package cn.orangenode.forge.member.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 后台开通或延长 VIP 入参：未开通或已过期时从当前时刻起算，未过期时在原到期时间上顺延。
 *
 * @param days   延长天数
 * @param remark 备注，可为空
 */
public record MemberVipExtendRequest(
        @NotNull(message = "请输入延长天数")
        @Min(value = 1, message = "延长天数至少为 1 天")
        @Max(value = 3650, message = "单次延长不能超过 3650 天")
        Integer days,

        @Size(max = 255, message = "备注不能超过 255 个字符")
        String remark) {
}
