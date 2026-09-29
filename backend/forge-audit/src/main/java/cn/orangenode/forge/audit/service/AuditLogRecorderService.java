package cn.orangenode.forge.audit.service;

import cn.orangenode.forge.framework.audit.AuditOperationRecord;
import cn.orangenode.forge.framework.audit.AuditRecorder;
import cn.orangenode.forge.framework.audit.LoginLogRecord;
import cn.orangenode.forge.framework.audit.LoginLogRecorder;

/**
 * 审计日志写入用例。
 *
 * <p>实现 framework 的两个记录端口，供操作审计切面与登录用例调用：调用方只依赖 framework 的端口，
 * 不感知审计模块的实体与 Mapper，因此不会形成“安全依赖审计、审计依赖安全”的循环。</p>
 *
 * <p>写入内容只有身份快照、动作、对象、结果 code、来源地址与 traceId：不记录密码、令牌、
 * 请求体与文件内容。审计表只追加，本用例不提供修改方法。</p>
 */
public interface AuditLogRecorderService extends AuditRecorder, LoginLogRecorder {

    /**
     * 保存一条操作审计记录。
     *
     * @param record 操作审计记录
     */
    @Override
    void record(AuditOperationRecord record);

    /**
     * 保存一条登录日志。
     *
     * @param record 登录日志记录
     */
    @Override
    void record(LoginLogRecord record);
}
