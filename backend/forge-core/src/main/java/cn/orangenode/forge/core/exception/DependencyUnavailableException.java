package cn.orangenode.forge.core.exception;

import cn.orangenode.forge.core.response.ErrorCode;

/**
 * 必要依赖不可用异常。
 *
 * <p>数据库、Redis、存储等基础设施暂不可用时抛出，统一映射为 body.code=503。
 * 使用该异常说明当前请求确实无法完成，调用方不得因此跳过认证或校验继续执行业务。</p>
 */
public class DependencyUnavailableException extends BusinessException {

    /**
     * 序列化标识；异常只在进程内传递，声明固定值以满足序列化契约。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 以统一依赖不可用错误码构造异常。
     *
     * @param message 面向调用者的中文提示，不包含连接串、密钥或堆栈
     */
    public DependencyUnavailableException(String message) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message);
    }

    /**
     * 以统一依赖不可用错误码构造异常并保留原始原因。
     *
     * @param message 面向调用者的中文提示
     * @param cause   原始异常，仅供服务端记录
     */
    public DependencyUnavailableException(String message, Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message, cause);
    }
}
