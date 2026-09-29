package cn.orangenode.forge.audit.service.impl;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.orangenode.forge.audit.converter.AuditLogConverter;
import cn.orangenode.forge.audit.entity.AuditLoginLogEntity;
import cn.orangenode.forge.audit.entity.AuditOperationLogEntity;
import cn.orangenode.forge.audit.mapper.AuditLoginLogMapper;
import cn.orangenode.forge.audit.mapper.AuditOperationLogMapper;
import cn.orangenode.forge.audit.request.LoginLogQueryRequest;
import cn.orangenode.forge.audit.request.OperationLogQueryRequest;
import cn.orangenode.forge.audit.response.LoginLogResponse;
import cn.orangenode.forge.audit.response.OperationLogResponse;
import cn.orangenode.forge.audit.service.AuditLogQueryService;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.page.PageResponses;

/**
 * 审计日志查询用例实现。
 *
 * <p>时间范围入参是字符串，解析集中在 {@link #toUtcBoundary}：只接受带时区的 ISO 8601
 * （例如 {@code 2026-09-29T10:00:00Z} 或 {@code 2026-09-29T18:00:00+08:00}），
 * 解析后统一换算为 UTC 的 {@link LocalDateTime} 与数据库时间列比较；
 * 缺少时区、格式非法或起止颠倒都抛 {@code body.code=400} 的业务异常。</p>
 *
 * <p>过滤条件全部走类型安全的查询条件构造器，排序列写在后端代码中，不接受调用方传入排序字段，
 * 因此不存在 SQL 片段拼接。返回结果经转换器映射为不含 Entity 的响应结构。</p>
 */
@Service
public class AuditLogQueryServiceImpl implements AuditLogQueryService {

    /**
     * 时间范围解析失败的统一中文提示；不把解析细节回显给调用方。
     */
    private static final String TIME_RANGE_INVALID_MESSAGE = "时间范围格式不正确，请使用带时区的 ISO 8601 时间";

    /**
     * 起止时间颠倒时的中文提示。
     */
    private static final String TIME_RANGE_REVERSED_MESSAGE = "起始时间不能晚于结束时间";

    /**
     * 登录日志数据访问。
     */
    private final AuditLoginLogMapper loginLogMapper;

    /**
     * 操作日志数据访问。
     */
    private final AuditOperationLogMapper operationLogMapper;

    /**
     * 审计日志实体到对外响应的转换器。
     */
    private final AuditLogConverter converter;

    /**
     * 构造审计日志查询用例实现。
     *
     * @param loginLogMapper     登录日志数据访问
     * @param operationLogMapper 操作日志数据访问
     * @param converter          审计日志转换器
     */
    public AuditLogQueryServiceImpl(AuditLoginLogMapper loginLogMapper, AuditOperationLogMapper operationLogMapper,
            AuditLogConverter converter) {
        this.loginLogMapper = loginLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.converter = converter;
    }

    /**
     * 分页查询登录日志。
     *
     * @param request 分页、用户名、结果与时间范围筛选入参
     * @return 登录日志分页结果，按创建时间倒序
     * @throws BusinessException 时间范围格式非法或起止颠倒时抛出 400
     */
    @Override
    public PageResponse<LoginLogResponse> pageLoginLogs(LoginLogQueryRequest request) {
        LocalDateTime[] range = resolveRange(request.getStartTime(), request.getEndTime());
        LambdaQueryWrapper<AuditLoginLogEntity> condition = new LambdaQueryWrapper<>();
        condition.like(hasText(request.getUsername()), AuditLoginLogEntity::getUsername, request.getUsername());
        condition.eq(hasText(request.getResult()), AuditLoginLogEntity::getResult, request.getResult());
        condition.ge(range[0] != null, AuditLoginLogEntity::getCreatedAt, range[0]);
        condition.le(range[1] != null, AuditLoginLogEntity::getCreatedAt, range[1]);
        condition.orderByDesc(AuditLoginLogEntity::getCreatedAt);
        condition.orderByDesc(AuditLoginLogEntity::getId);
        Page<AuditLoginLogEntity> page = new Page<>(request.getPageNum(), request.getPageSize());
        return PageResponses.from(loginLogMapper.selectPage(page, condition), converter::toLoginLogResponse);
    }

    /**
     * 分页查询操作日志。
     *
     * @param request 分页、操作者名称、动作、结果码与时间范围筛选入参
     * @return 操作日志分页结果，按创建时间倒序
     * @throws BusinessException 时间范围格式非法或起止颠倒时抛出 400
     */
    @Override
    public PageResponse<OperationLogResponse> pageOperationLogs(OperationLogQueryRequest request) {
        LocalDateTime[] range = resolveRange(request.getStartTime(), request.getEndTime());
        LambdaQueryWrapper<AuditOperationLogEntity> condition = new LambdaQueryWrapper<>();
        condition.like(hasText(request.getOperatorName()), AuditOperationLogEntity::getOperatorName,
                request.getOperatorName());
        condition.eq(hasText(request.getAction()), AuditOperationLogEntity::getAction, request.getAction());
        // 结果码为空时必须跳过转换：eq 的条件参数会被立刻求值，直接传转换结果会在未筛选时抛参数错误
        if (hasText(request.getResultCode())) {
            condition.eq(AuditOperationLogEntity::getResultCode, toResultCode(request.getResultCode()));
        }
        condition.ge(range[0] != null, AuditOperationLogEntity::getCreatedAt, range[0]);
        condition.le(range[1] != null, AuditOperationLogEntity::getCreatedAt, range[1]);
        condition.orderByDesc(AuditOperationLogEntity::getCreatedAt);
        condition.orderByDesc(AuditOperationLogEntity::getId);
        Page<AuditOperationLogEntity> page = new Page<>(request.getPageNum(), request.getPageSize());
        return PageResponses.from(operationLogMapper.selectPage(page, condition), converter::toOperationLogResponse);
    }

    /**
     * 解析并校验时间范围。
     *
     * @param startTime 起始时间字符串，允许为空表示不设下界
     * @param endTime   结束时间字符串，允许为空表示不设上界
     * @return 长度为 2 的 UTC 边界数组，元素可为 {@code null}
     * @throws BusinessException 格式非法或起始时间晚于结束时间时抛出 400
     */
    private LocalDateTime[] resolveRange(String startTime, String endTime) {
        LocalDateTime start = toUtcBoundary(startTime);
        LocalDateTime end = toUtcBoundary(endTime);
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, TIME_RANGE_REVERSED_MESSAGE);
        }
        return new LocalDateTime[] {start, end};
    }

    /**
     * 把带时区的 ISO 8601 时间字符串解析为 UTC 的数据库比较边界。
     *
     * @param text 时间字符串，允许为空
     * @return UTC 语义的时间，入参为空时返回 {@code null}
     * @throws BusinessException 缺少时区或格式非法时抛出 400
     */
    private LocalDateTime toUtcBoundary(String text) {
        if (!hasText(text)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text.strip()).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeException failure) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, TIME_RANGE_INVALID_MESSAGE, failure);
        }
    }

    /**
     * 把结果码字符串转换为查询使用的整数。
     *
     * <p>入参校验已把取值限制为不超过 6 位数字，转换分支只用于防御性兜底：
     * 万一出现越界取值，按参数错误返回 400，原始异常只交给全局处理器记录一次。</p>
     *
     * @param resultCode 结果码字符串
     * @return 结果码整数
     * @throws BusinessException 数值超出整型范围时抛出 400
     */
    private int toResultCode(String resultCode) {
        try {
            return Integer.parseInt(resultCode);
        } catch (NumberFormatException failure) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结果码超出允许范围", failure);
        }
    }

    /**
     * 判断筛选条件是否有效。
     *
     * @param value 筛选条件值，允许为 {@code null}
     * @return 去空格后非空时返回 {@code true}
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
