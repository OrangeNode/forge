package cn.orangenode.forge.framework.audit;

/**
 * 操作审计记录端口。
 *
 * <p>由 framework 的切面调用，由审计模块实现持久化；基础模块不依赖审计业务实现。</p>
 */
public interface AuditRecorder {

    /**
     * 保存一条操作审计记录。
     *
     * @param record 审计记录
     */
    void record(AuditOperationRecord record);
}
