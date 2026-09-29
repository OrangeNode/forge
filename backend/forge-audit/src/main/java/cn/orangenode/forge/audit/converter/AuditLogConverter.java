package cn.orangenode.forge.audit.converter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.audit.entity.AuditLoginLogEntity;
import cn.orangenode.forge.audit.entity.AuditOperationLogEntity;
import cn.orangenode.forge.audit.response.LoginLogResponse;
import cn.orangenode.forge.audit.response.OperationLogResponse;

/**
 * 审计日志转换器。
 *
 * <p>Entity、Response 分离，逐字段显式赋值：不使用反射批量复制，
 * 也不使用映射框架，避免将来新增内部字段时被意外带出到接口。</p>
 *
 * <p>数据库时间列按 UTC 语义保存为 {@link LocalDateTime}，对外统一换算为 {@link Instant}，
 * 时区换算集中在这里，实体与查询代码不做时区推断。</p>
 */
@Component
public class AuditLogConverter {

    /**
     * 把登录日志实体转换为对外响应。
     *
     * @param entity 登录日志实体
     * @return 登录日志响应
     */
    public LoginLogResponse toLoginLogResponse(AuditLoginLogEntity entity) {
        return new LoginLogResponse(String.valueOf(entity.getId()), entity.getUsername(), entity.getResult(),
                entity.getReason(), entity.getClientIp(), entity.getTraceId(), toInstant(entity.getCreatedAt()));
    }

    /**
     * 把操作日志实体转换为对外响应。
     *
     * @param entity 操作日志实体
     * @return 操作日志响应
     */
    public OperationLogResponse toOperationLogResponse(AuditOperationLogEntity entity) {
        return new OperationLogResponse(String.valueOf(entity.getId()), entity.getOperatorType(),
                toIdText(entity.getOperatorId()), entity.getOperatorName(), entity.getAction(),
                entity.getResourceType(), entity.getResourceId(), entity.getResultCode(), entity.getTraceId(),
                toInstant(entity.getCreatedAt()));
    }

    /**
     * 把 UTC 语义的数据库时间换算为对外瞬时时间。
     *
     * @param createdAt 数据库创建时间，允许为 {@code null}
     * @return 瞬时时间，入参为空时返回 {@code null}
     */
    private Instant toInstant(LocalDateTime createdAt) {
        return createdAt == null ? null : createdAt.toInstant(ZoneOffset.UTC);
    }

    /**
     * 把可空的 ID 快照转换为对外字符串。
     *
     * @param id 数据库 ID，系统任务记录为 {@code null}
     * @return 字符串 ID，入参为空时返回 {@code null}
     */
    private String toIdText(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
