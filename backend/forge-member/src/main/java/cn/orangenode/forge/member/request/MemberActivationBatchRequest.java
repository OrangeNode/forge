package cn.orangenode.forge.member.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/**
 * 激活码批量操作入参；单次数量上限由 {@code forge.member.activation.batch-max} 配置，在用例中校验。
 *
 * @param ids 激活码记录 ID
 */
public record MemberActivationBatchRequest(
        @NotEmpty(message = "请至少选择一个激活码")
        List<String> ids) {
}
