package cn.orangenode.forge.file.response;

import java.time.Instant;

/**
 * 文件元数据对外响应。
 *
 * <p>只包含调用方需要的元数据：不返回对象键、存储根目录、访问地址与访问凭据，
 * 下载统一通过文件 ID 调用受权限保护的接口。上传者 ID 使用字符串形式。</p>
 *
 * @param id              文件 ID，对外为字符串
 * @param storageConfigId 上传时固定的存储配置版本 ID，对外为字符串
 * @param originalName    原始文件名
 * @param contentType     上传时声明的内容类型，可为空
 * @param sizeBytes       文件大小（字节）
 * @param uploaderId      上传者管理员 ID，对外为字符串
 * @param uploaderName    上传者名称快照，可为空
 * @param createdAt       创建时间（UTC）
 */
public record FileRecordResponse(String id, String storageConfigId, String originalName, String contentType,
        long sizeBytes, String uploaderId, String uploaderName, Instant createdAt) {
}
