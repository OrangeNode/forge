package cn.orangenode.forge.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 审计配置。
 *
 * <p>对应配置前缀 {@code forge.audit}：登录日志与操作日志的保留期限、清理任务开关与执行间隔。
 * 审计表只追加记录，清理只删除超过保留期限的数据，不修改历史记录。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "forge.audit")
public class ForgeAuditProperties {

    /**
     * 登录日志保留天数，超过该天数的记录由清理任务删除。
     */
    @Min(value = 1, message = "登录日志保留天数必须大于 0")
    private int loginLogRetentionDays = 90;

    /**
     * 操作日志保留天数，超过该天数的记录由清理任务删除。
     */
    @Min(value = 1, message = "操作日志保留天数必须大于 0")
    private int operationLogRetentionDays = 180;

    /**
     * 是否启用日志清理任务。
     */
    private boolean cleanupEnabled = true;

    /**
     * 日志清理任务的执行间隔小时数。
     */
    @Min(value = 1, message = "日志清理间隔必须大于 0 小时")
    private long cleanupIntervalHours = 24;
}
