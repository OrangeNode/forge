package cn.orangenode.forge.framework.audit;

/**
 * 登录日志记录端口。
 *
 * <p>由登录用例调用，由审计模块实现持久化；登录用例不直接依赖审计模块的实现类。</p>
 */
public interface LoginLogRecorder {

    /**
     * 保存一条登录日志。
     *
     * @param record 登录日志记录
     */
    void record(LoginLogRecord record);
}
