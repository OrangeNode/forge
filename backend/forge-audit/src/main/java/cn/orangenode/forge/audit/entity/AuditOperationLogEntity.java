package cn.orangenode.forge.audit.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 操作日志实体。
 *
 * <p>对应表 {@code audit_operation_log}，由 framework 的操作审计切面通过 {@code AuditRecorder} 端口写入。
 * 保存的是操作者身份快照而不是外键引用，因此账号改名或停用不会改变历史记录。</p>
 *
 * <p>审计表只追加：本实体没有逻辑删除字段，也没有修改入口，历史记录只按保留期限清理。
 * 不保存请求体、响应体与文件内容；{@code createdAt} 按 UTC 语义读写。</p>
 */
@Getter
@Setter
@TableName("audit_operation_log")
public class AuditOperationLogEntity {

    /**
     * 主键，数据库自增。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 操作者类型：{@code ADMIN} 管理员，{@code SYSTEM} 系统任务。
     */
    private String operatorType;

    /**
     * 操作者管理员 ID，系统任务为空。
     */
    private Long operatorId;

    /**
     * 操作者名称快照，系统任务为空。
     */
    private String operatorName;

    /**
     * 动作代码，例如 {@code system:admin:create}。
     */
    private String action;

    /**
     * 对象类型，例如 {@code admin}、{@code storage-config}。
     */
    private String resourceType;

    /**
     * 对象 ID 快照，对外字符串形式。
     */
    private String resourceId;

    /**
     * 业务结果 code，{@code 0} 表示成功。
     */
    private int resultCode;

    /**
     * 请求追踪编号，用于关联服务端日志。
     */
    private String traceId;

    /**
     * 创建时间（UTC）。
     */
    private LocalDateTime createdAt;
}
