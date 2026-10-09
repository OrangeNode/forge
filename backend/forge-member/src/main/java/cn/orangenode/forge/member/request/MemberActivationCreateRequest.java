package cn.orangenode.forge.member.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 批量生成注册激活码入参；数量上限由 {@code forge.member.activation.batch-max} 配置，在用例中校验。
 *
 * <p>激活码不设固定截止日期，会员时长从用户用该码注册成功时开始计算。</p>
 *
 * @param count   生成数量
 * @param vipDays 注册成功后开通的 VIP 天数，如 30、90、180、365
 * @param remark  备注，可为空
 */
public record MemberActivationCreateRequest(
        @NotNull(message = "请输入生成数量")
        @Min(value = 1, message = "生成数量至少为 1")
        Integer count,

        @NotNull(message = "请选择会员时长")
        @Min(value = 1, message = "会员时长至少为 1 天")
        @Max(value = 3650, message = "会员时长不能超过 3650 天")
        Integer vipDays,

        @Size(max = 255, message = "备注不能超过 255 个字符")
        String remark) {
}
