package cn.orangenode.forge.audit.response;

import java.time.Instant;

/**
 * 操作日志对外响应。
 *
 * <p>只包含审计排障需要的字段，不包含 Entity、请求体与响应体。
 * {@code operatorId} 与 {@code resourceId} 都是身份与对象快照，对外为字符串；
 * {@code createdAt} 使用 {@link Instant}，序列化为带时区的 ISO 8601 字符串。</p>
 *
 * @param id           日志 ID，对外为字符串
 * @param operatorType 操作者类型，{@code ADMIN} 或 {@code SYSTEM}
 * @param operatorId   操作者管理员 ID 快照，系统任务为空
 * @param operatorName 操作者名称快照，系统任务为空
 * @param action       动作代码
 * @param resourceType 对象类型，可能为空
 * @param resourceId   对象 ID 快照，可能为空
 * @param resultCode   业务结果 code，{@code 0} 表示成功
 * @param traceId      请求追踪编号，可能为空
 * @param createdAt    创建时间（UTC）
 */
public record OperationLogResponse(String id, String operatorType, String operatorId, String operatorName,
        String action, String resourceType, String resourceId, int resultCode, String traceId,
        Instant createdAt) {
}
