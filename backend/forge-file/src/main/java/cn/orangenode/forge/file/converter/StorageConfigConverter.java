package cn.orangenode.forge.file.converter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.file.entity.FileStorageConfigEntity;
import cn.orangenode.forge.file.policy.FileStoragePolicy;
import cn.orangenode.forge.file.response.StorageConfigResponse;
import cn.orangenode.forge.file.response.StorageTestResponse;
import cn.orangenode.forge.file.support.FileIds;

/**
 * 存储配置转换器。
 *
 * <p>Entity 与 Response 分离，逐字段显式赋值：不使用反射批量复制，也不使用映射框架，
 * 避免将来新增内部字段时被意外带出。</p>
 *
 * <p>凭据只转换为“是否已配置”的布尔值：{@code accessKey} 与 {@code secretKey} 的密文与明文
 * 都不进入响应；数据库时间列按 UTC 语义保存，对外统一换算为 {@link Instant}。</p>
 */
@Component
public class StorageConfigConverter {

    /**
     * path-style 开启时数据库列的取值。
     */
    private static final int PATH_STYLE_ENABLED = 1;

    /**
     * 把存储配置实体转换为对外响应。
     *
     * @param entity        存储配置实体
     * @param defaultConfig 是否为当前默认方案
     * @return 存储配置响应
     */
    public StorageConfigResponse toResponse(FileStorageConfigEntity entity, boolean defaultConfig) {
        List<String> allowedExtensions = List.copyOf(FileStoragePolicy.parseAllowedExtensions(
                entity.getAllowedExtensions()));
        return new StorageConfigResponse(FileIds.toText(entity.getId()), entity.getCode(), entity.getVersion(),
                entity.getName(), entity.getProvider(), entity.getBaseDir(), entity.getEndpoint(), entity.getRegion(),
                entity.getBucket(), isPathStyle(entity), hasText(entity.getAccessKey()),
                entity.getMaxFileSize() == null ? 0L : entity.getMaxFileSize(), allowedExtensions, defaultConfig,
                toInstant(entity.getCreatedAt()), toInstant(entity.getUpdatedAt()));
    }

    /**
     * 把连接检测结果组装为对外响应。
     *
     * @param entity        被检测的存储配置实体
     * @param elapsedMillis 检测耗时（毫秒）
     * @param available     目标是否可用
     * @param message       面向使用者的检测结论，不包含访问地址与凭据
     * @return 连接检测响应
     */
    public StorageTestResponse toTestResponse(FileStorageConfigEntity entity, long elapsedMillis, boolean available,
            String message) {
        return new StorageTestResponse(FileIds.toText(entity.getId()), entity.getProvider(), available,
                elapsedMillis, message);
    }

    /**
     * 判断配置是否启用 path-style 访问。
     *
     * @param entity 存储配置实体
     * @return 启用时返回 {@code true}
     */
    private boolean isPathStyle(FileStorageConfigEntity entity) {
        return entity.getPathStyle() != null && entity.getPathStyle() == PATH_STYLE_ENABLED;
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

    /**
     * 判断文本是否非空。
     *
     * @param value 待判断文本，允许为 {@code null}
     * @return 去空格后非空时返回 {@code true}
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
