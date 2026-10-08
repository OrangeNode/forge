package cn.orangenode.forge.framework.storage;

import java.io.InputStream;

/**
 * 已上传文件的内容与安全元数据，不含对象键与存储位置。
 *
 * @param stream      内容流，由调用方关闭
 * @param contentType 媒体类型，可为空
 * @param sizeBytes   文件大小
 */
public record StoredFileContent(InputStream stream, String contentType, long sizeBytes) {
}
