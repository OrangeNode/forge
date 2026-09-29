package cn.orangenode.forge.audit.service.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;

import cn.orangenode.forge.audit.entity.AuditLoginLogEntity;
import cn.orangenode.forge.audit.entity.AuditOperationLogEntity;
import cn.orangenode.forge.audit.mapper.AuditLoginLogMapper;
import cn.orangenode.forge.audit.mapper.AuditOperationLogMapper;
import cn.orangenode.forge.audit.service.AuditLogRecorderService;
import cn.orangenode.forge.framework.audit.AuditOperationRecord;
import cn.orangenode.forge.framework.audit.LoginLogRecord;

import lombok.extern.slf4j.Slf4j;

/**
 * 审计日志写入用例实现。
 *
 * <p>登录日志与操作日志都写到只追加的表里，时间统一取 UTC 的 {@link LocalDateTime} 写入
 * {@code DATETIME(3)} 列，与数据库“按 UTC 读写”的约定一致；对外展示的时区换算由查询侧转换器完成。
 * 时间来源集中在 {@link #nowUtc()} 一处，将来需要注入可控时钟时只改该方法。</p>
 *
 * <p>写入不抛业务异常：审计属于旁路能力，切面与登录用例已按“审计失败不改业务结果”处理，
 * 这里只保证不制造空值、不写入请求体。登录日志缺少用户名时退化为空字符串保存并记调试日志，
 * 保留调用方线索，而不是静默丢弃一条登录尝试。</p>
 */
@Slf4j
@Service
public class AuditLogRecorderServiceImpl implements AuditLogRecorderService {

    /**
     * 登录成功结果代码，与表注释和前端的稳定取值一致。
     */
    private static final String RESULT_SUCCESS = "success";

    /**
     * 登录失败结果代码。
     */
    private static final String RESULT_FAILURE = "failure";

    /**
     * 端口未提供操作者类型时的兜底取值：视为系统任务，不假造管理员身份。
     */
    private static final String OPERATOR_TYPE_SYSTEM = "SYSTEM";

    /**
     * 登录日志数据访问。
     */
    private final AuditLoginLogMapper loginLogMapper;

    /**
     * 操作日志数据访问。
     */
    private final AuditOperationLogMapper operationLogMapper;

    /**
     * 构造审计日志写入用例实现。
     *
     * @param loginLogMapper     登录日志数据访问
     * @param operationLogMapper 操作日志数据访问
     */
    public AuditLogRecorderServiceImpl(AuditLoginLogMapper loginLogMapper,
            AuditOperationLogMapper operationLogMapper) {
        this.loginLogMapper = loginLogMapper;
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 保存一条操作审计记录。
     *
     * @param record 操作审计记录，来自 framework 的操作审计切面；为空时跳过写入并记警告
     */
    @Override
    public void record(AuditOperationRecord record) {
        if (record == null) {
            log.warn("操作审计记录为空，跳过写入");
            return;
        }
        AuditOperationLogEntity entity = new AuditOperationLogEntity();
        entity.setOperatorType(record.operatorType() == null ? OPERATOR_TYPE_SYSTEM : record.operatorType());
        entity.setOperatorId(record.operatorId());
        entity.setOperatorName(record.operatorName());
        entity.setAction(record.action() == null ? "" : record.action());
        entity.setResourceType(record.resourceType());
        entity.setResourceId(record.resourceId());
        entity.setResultCode(record.resultCode());
        entity.setTraceId(record.traceId());
        entity.setCreatedAt(nowUtc());
        operationLogMapper.insert(entity);
    }

    /**
     * 保存一条登录日志。
     *
     * @param record 登录日志记录，来自登录用例；为空时跳过写入并记警告
     */
    @Override
    public void record(LoginLogRecord record) {
        if (record == null) {
            log.warn("登录日志记录为空，跳过写入");
            return;
        }
        if (record.username() == null || record.username().isBlank()) {
            log.debug("登录日志缺少用户名，按空用户名保存，result={}", record.result());
        }
        AuditLoginLogEntity entity = new AuditLoginLogEntity();
        entity.setUsername(record.username() == null ? "" : record.username());
        entity.setResult(normalizeResult(record.result()));
        entity.setReason(record.reason());
        entity.setClientIp(record.clientIp());
        entity.setTraceId(record.traceId());
        entity.setCreatedAt(nowUtc());
        loginLogMapper.insert(entity);
    }

    /**
     * 规范化登录结果取值，避免写入表约束之外的值。
     *
     * <p>只有明确为 {@code success} 的记录才记为成功，其余一律按失败保存：宁可把成功记成失败，
     * 也不能把失败尝试记成成功而掩盖异常登录。</p>
     *
     * @param result 端口传入的结果代码，允许为 {@code null}
     * @return 表中允许的结果代码
     */
    private String normalizeResult(String result) {
        return RESULT_SUCCESS.equals(result) ? RESULT_SUCCESS : RESULT_FAILURE;
    }

    /**
     * 取得当前 UTC 时间，供数据库时间列写入。
     *
     * <p>不依赖 {@code Clock} 之类的容器装配：项目尚无统一时钟组件，直接取系统 UTC 时间，
     * 避免为单个模块引入无人使用的公共抽象。</p>
     *
     * @return UTC 语义的当前时间
     */
    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    }
}
