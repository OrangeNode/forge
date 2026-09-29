package cn.orangenode.forge.audit.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 登录日志实体。
 *
 * <p>对应表 {@code audit_login_log}，记录每一次登录尝试（含失败尝试），由登录用例通过
 * {@code LoginLogRecorder} 端口写入。审计表只追加：本实体没有逻辑删除字段，也不提供修改入口。</p>
 *
 * <p>不保存密码、令牌或请求体，只保存用户名、结果、失败原因分类、来源地址与追踪编号。
 * {@code createdAt} 按 UTC 语义读写，对外的带时区时间由转换器显式换算。</p>
 */
@Getter
@Setter
@TableName("audit_login_log")
public class AuditLoginLogEntity {

    /**
     * 主键，数据库自增。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 登录尝试使用的用户名，按账号规范化规则保存。
     */
    private String username;

    /**
     * 登录结果：{@code success} 成功，{@code failure} 失败。
     */
    private String result;

    /**
     * 失败原因分类：{@code credentials} 凭据错误，{@code disabled} 账号停用，{@code rate_limited} 触发限流。
     */
    private String reason;

    /**
     * 来源地址，IPv4 或 IPv6。
     */
    private String clientIp;

    /**
     * 请求追踪编号，用于关联服务端日志。
     */
    private String traceId;

    /**
     * 创建时间（UTC）。
     */
    private LocalDateTime createdAt;
}
