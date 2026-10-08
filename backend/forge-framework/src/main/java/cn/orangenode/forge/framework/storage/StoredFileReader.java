package cn.orangenode.forge.framework.storage;

/**
 * 已上传文件读取端口。
 *
 * <p>由文件模块实现，其他业务模块只能按文件 ID 读取元数据与内容，
 * 业务侧负责先确认该文件确实被自己的配置引用，再决定是否对外提供。</p>
 */
public interface StoredFileReader {

    /**
     * 判断文件是否存在且未删除。
     *
     * @param fileId 文件 ID
     * @return 存在时为 {@code true}
     */
    boolean exists(Long fileId);

    /**
     * 打开文件内容，调用方负责关闭流；文件不存在返回 body.code=404。
     *
     * @param fileId 文件 ID
     * @return 文件内容与元数据
     */
    StoredFileContent open(Long fileId);

    /**
     * 确保文件已生成公开外链并返回公开下载路径（不含域名）；已公开时返回原链接。
     *
     * @param fileId 文件 ID
     * @return 公开下载路径
     */
    String publicPath(Long fileId);
}
