package cn.orangenode.forge.file.response;

import java.time.Instant;

/**
 * 文件上传结果响应。
 *
 * <p>上传成功后返回稳定的文件 ID 与安全元数据：不返回对象键、本地绝对路径与存储凭据，
 * 调用方后续只能按文件 ID 访问内容。</p>
 *
 * @param id           文件 ID，对外为字符串
 * @param originalName 原始文件名
 * @param contentType  上传时声明的内容类型，可为空
 * @param sizeBytes    文件大小（字节）
 * @param createdAt    创建时间（UTC）
 */
public record FileUploadResponse(String id, String originalName, String contentType, long sizeBytes,
        Instant createdAt) {
}
