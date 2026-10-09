package cn.orangenode.forge.member.response;

/**
 * 激活码批量操作结果。
 *
 * @param succeeded 实际处理的数量
 * @param skipped   因状态不符（如已使用、已作废）或已不存在而跳过的数量
 */
public record MemberActivationBatchResponse(int succeeded, int skipped) {
}
