package cn.orangenode.forge.framework.storage;

import java.io.InputStream;

/**
 * 存储适配端口。
 *
 * <p>只负责对象读写与连接能力，不决定业务权限：调用者必须先完成业务授权，
 * 再把已经确定的对象键交给实现。实现由文件模块按存储配置版本创建，一个配置版本对应一个实例。</p>
 *
 * <p>实现不得把本地绝对路径、endpoint 凭据或存储密钥写入异常信息与日志。</p>
 */
public interface StorageProvider {

    /**
     * 返回实现对应的存储类型代码。
     *
     * @return {@code local} 或 {@code s3}
     */
    String providerCode();

    /**
     * 写入对象。
     *
     * @param objectKey     随机生成的对象键
     * @param content       对象内容，由调用者负责关闭
     * @param contentLength 内容长度（字节）
     * @param contentType   内容类型，可为空
     */
    void store(String objectKey, InputStream content, long contentLength, String contentType);

    /**
     * 按对象键读取对象。
     *
     * @param objectKey 对象键
     * @return 对象内容，由调用者负责关闭
     */
    InputStream read(String objectKey);

    /**
     * 按对象键删除对象；对象不存在时视为删除成功。
     *
     * @param objectKey 对象键
     */
    void delete(String objectKey);

    /**
     * 检测存储目标是否可用。
     *
     * <p>用于管理端的连接检测：目标不可达或凭据无效时抛出可预期的业务异常，
     * 由统一错误出口转换为对应结果码。</p>
     */
    void verify();
}
