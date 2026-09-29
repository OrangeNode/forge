package cn.orangenode.forge.framework.audit;

/**
 * 一条操作审计记录。
 *
 * <p>只包含身份快照、动作、对象、结果与追踪编号：不包含请求体、响应体、密码、令牌或文件内容。</p>
 *
 * @param operatorType 操作者类型，{@code ADMIN} 或 {@code SYSTEM}
 * @param operatorId   操作者管理员 ID，系统任务为空
 * @param operatorName 操作者名称快照，系统任务为空
 * @param action       动作代码
 * @param resourceType 对象类型，可为空
 * @param resourceId   对象 ID 快照，可为空
 * @param resultCode   业务结果 code，0 表示成功
 * @param traceId      请求追踪编号，可为空
 */
public record AuditOperationRecord(String operatorType, Long operatorId, String operatorName, String action,
        String resourceType, String resourceId, int resultCode, String traceId) {
}
