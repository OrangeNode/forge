package cn.orangenode.forge.audit.service;

import cn.orangenode.forge.audit.request.LoginLogQueryRequest;
import cn.orangenode.forge.audit.request.OperationLogQueryRequest;
import cn.orangenode.forge.audit.response.LoginLogResponse;
import cn.orangenode.forge.audit.response.OperationLogResponse;
import cn.orangenode.forge.core.page.PageResponse;

/**
 * 审计日志查询用例。
 *
 * <p>提供登录日志与操作日志的分页查询，返回的对外结构不含 Entity、查询包装器或 {@code IPage}。
 * 时间范围入参为带时区的 ISO 8601 字符串，由本用例统一解析，格式非法或区间颠倒返回
 * {@code body.code=400}，避免在各 Controller 重复日期校验。</p>
 *
 * <p>查询是只读用例，不需要审计注解；返回结果按创建时间倒序，最新记录在前。</p>
 */
public interface AuditLogQueryService {

    /**
     * 分页查询登录日志。
     *
     * @param request 分页与筛选入参
     * @return 登录日志分页结果
     */
    PageResponse<LoginLogResponse> pageLoginLogs(LoginLogQueryRequest request);

    /**
     * 分页查询操作日志。
     *
     * @param request 分页与筛选入参
     * @return 操作日志分页结果
     */
    PageResponse<OperationLogResponse> pageOperationLogs(OperationLogQueryRequest request);
}
