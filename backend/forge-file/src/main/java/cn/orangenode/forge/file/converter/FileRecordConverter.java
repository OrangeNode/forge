package cn.orangenode.forge.file.converter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.file.entity.FileRecordEntity;
import cn.orangenode.forge.file.response.FileRecordResponse;
import cn.orangenode.forge.file.response.FileUploadResponse;
import cn.orangenode.forge.file.support.FileIds;

/**
 * 文件记录转换器。
 *
 * <p>Entity 与 Response 分离，逐字段显式赋值：不使用反射批量复制，也不使用映射框架。
 * 对象键、存储配置的访问地址与凭据都不在响应里出现，调用方只能通过文件 ID 访问内容。</p>
 *
 * <p>数据库时间列按 UTC 语义保存，对外统一换算为 {@link Instant}，时区换算集中在本类，
 * 实体与业务代码不做时区推断。</p>
 */
@Component
public class FileRecordConverter {

    /**
     * 把文件记录实体转换为对外元数据响应。
     *
     * @param entity 文件记录实体
     * @return 文件元数据响应
     */
    public FileRecordResponse toResponse(FileRecordEntity entity) {
        return new FileRecordResponse(FileIds.toText(entity.getId()), FileIds.toText(entity.getStorageConfigId()),
                entity.getOriginalName(), entity.getContentType(), sizeOf(entity), FileIds.toText(entity.getUploaderId()),
                entity.getUploaderName(), toInstant(entity.getCreatedAt()));
    }

    /**
     * 把文件记录实体转换为上传结果响应。
     *
     * @param entity 文件记录实体
     * @return 上传结果响应
     */
    public FileUploadResponse toUploadResponse(FileRecordEntity entity) {
        return new FileUploadResponse(FileIds.toText(entity.getId()), entity.getOriginalName(),
                entity.getContentType(), sizeOf(entity), toInstant(entity.getCreatedAt()));
    }

    /**
     * 读取文件大小。
     *
     * @param entity 文件记录实体
     * @return 文件大小（字节），字段为空时返回 0
     */
    private long sizeOf(FileRecordEntity entity) {
        return entity.getSizeBytes() == null ? 0L : entity.getSizeBytes();
    }

    /**
     * 把 UTC 语义的数据库时间换算为对外瞬时时间。
     *
     * @param value 数据库时间，允许为 {@code null}
     * @return 瞬时时间，入参为空时返回 {@code null}
     */
    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
