package cn.orangenode.forge.framework.web.error;

/**
 * 统一错误响应写出失败异常。
 *
 * <p>仅在响应已经开始或容器输出流不可写时抛出。此时无法再生成统一错误响应体，
 * 由容器记录该异常，不能改写成业务成功响应。</p>
 */
public class ErrorResponseWriteException extends RuntimeException {

    /**
     * 序列化标识；异常只在进程内传递，声明固定值以满足序列化契约。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 构造错误响应写出失败异常。
     *
     * @param message 面向服务端排查的中文说明
     * @param cause   原始 IO 异常
     */
    public ErrorResponseWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
