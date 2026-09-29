package cn.orangenode.forge.audit.task;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import cn.orangenode.forge.audit.entity.AuditLoginLogEntity;
import cn.orangenode.forge.audit.entity.AuditOperationLogEntity;
import cn.orangenode.forge.audit.mapper.AuditLoginLogMapper;
import cn.orangenode.forge.audit.mapper.AuditOperationLogMapper;
import cn.orangenode.forge.framework.config.ForgeAuditProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 审计日志清理任务。
 *
 * <p>按 {@code forge.audit} 配置的保留天数删除过期记录：审计表只追加、不接受普通修改，
 * 唯一的删除路径就是本任务按期限清理。保留天数与执行间隔都来自配置，代码里不写死期限。</p>
 *
 * <p>触发时机是每小时整点的一次轻量检查，真正是否清理由配置的执行间隔小时数决定：
 * 距上次清理不足配置间隔时直接返回，因此改配置即可改变清理频率，不需要重写调度表达式。
 * 删除条件写成带下界的时间比较而不是无条件删除或前缀扫描，一次只删除超过保留期限的记录。</p>
 *
 * <p>开关关闭时任务不做任何数据库访问。删除行数写入日志，便于运维核对清理效果；
 * 调度已由 framework 的 {@code SchedulingConfig} 开启，本类只声明方法级 {@code @Scheduled}。</p>
 */
@Slf4j
@Component
public class AuditLogCleanupTask {

    /**
     * 登录日志数据访问。
     */
    private final AuditLoginLogMapper loginLogMapper;

    /**
     * 操作日志数据访问。
     */
    private final AuditOperationLogMapper operationLogMapper;

    /**
     * 审计保留期限与清理周期配置。
     */
    private final ForgeAuditProperties properties;

    /**
     * 上次成功清理的 UTC 时间；为空表示尚未清理，下一次检查立即执行。
     */
    private final AtomicReference<Instant> lastCleanupAt = new AtomicReference<>();

    /**
     * 构造审计日志清理任务。
     *
     * @param loginLogMapper     登录日志数据访问
     * @param operationLogMapper 操作日志数据访问
     * @param properties         审计保留期限与清理开关配置
     */
    public AuditLogCleanupTask(AuditLoginLogMapper loginLogMapper, AuditOperationLogMapper operationLogMapper,
            ForgeAuditProperties properties) {
        this.loginLogMapper = loginLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.properties = properties;
    }

    /**
     * 按配置的间隔清理超过保留期限的审计日志。
     *
     * <p>每小时整点触发一次检查；未到配置的清理间隔或开关关闭时都直接返回。
     * 清理阈值取当前 UTC 时间减去保留天数，删除严格早于阈值的记录。</p>
     */
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredLogs() {
        if (!properties.isCleanupEnabled()) {
            log.debug("审计日志清理已关闭，跳过本次清理");
            return;
        }
        Instant now = Instant.now();
        if (!isDue(now)) {
            return;
        }
        LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        int loginLogRows = cleanupLoginLogs(nowUtc.minusDays(properties.getLoginLogRetentionDays()));
        int operationLogRows = cleanupOperationLogs(nowUtc.minusDays(properties.getOperationLogRetentionDays()));
        lastCleanupAt.set(now);
        log.info("审计日志清理完成，登录日志删除 {} 条，操作日志删除 {} 条", loginLogRows, operationLogRows);
    }

    /**
     * 判断是否到达配置的清理间隔。
     *
     * @param now 当前 UTC 时间
     * @return 从未清理过或距上次清理超过配置间隔小时数时返回 {@code true}
     */
    private boolean isDue(Instant now) {
        Instant previous = lastCleanupAt.get();
        if (previous == null) {
            return true;
        }
        return Duration.between(previous, now).toHours() >= properties.getCleanupIntervalHours();
    }

    /**
     * 删除超过保留期限的登录日志。
     *
     * @param boundary 保留期限下界，早于该时间的记录被删除
     * @return 删除的记录行数
     */
    private int cleanupLoginLogs(LocalDateTime boundary) {
        LambdaQueryWrapper<AuditLoginLogEntity> condition = new LambdaQueryWrapper<>();
        condition.lt(AuditLoginLogEntity::getCreatedAt, boundary);
        return loginLogMapper.delete(condition);
    }

    /**
     * 删除超过保留期限的操作日志。
     *
     * @param boundary 保留期限下界，早于该时间的记录被删除
     * @return 删除的记录行数
     */
    private int cleanupOperationLogs(LocalDateTime boundary) {
        LambdaQueryWrapper<AuditOperationLogEntity> condition = new LambdaQueryWrapper<>();
        condition.lt(AuditOperationLogEntity::getCreatedAt, boundary);
        return operationLogMapper.delete(condition);
    }
}
