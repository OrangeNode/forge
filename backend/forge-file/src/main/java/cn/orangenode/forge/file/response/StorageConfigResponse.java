package cn.orangenode.forge.file.response;

import java.time.Instant;
import java.util.List;

/**
 * 存储配置对外响应。
 *
 * <p>只包含管理界面需要的字段：ID 与时间对外使用字符串与带时区的 ISO 8601，
 * 凭据只回显“是否已配置”，绝不下发密文或明文；本地相对目录是相对路径而不是绝对路径，
 * 对象存储访问地址可用于排查但不包含任何凭据。</p>
 *
 * @param id                   配置版本 ID，对外为字符串
 * @param code                 存储方案代码
 * @param version              版本号，从 1 开始
 * @param name                 方案名称
 * @param provider             存储类型，{@code local} 或 {@code s3}
 * @param baseDir              本地存储相对目录，对象存储时为空
 * @param endpoint             对象存储访问地址，本地存储时为空
 * @param region               对象存储区域，可为空
 * @param bucket               对象存储桶名称，本地存储时为空
 * @param pathStyle            是否使用 path-style 访问
 * @param credentialConfigured 是否已保存访问凭据，不表示凭据可用
 * @param maxFileSize          单文件大小上限（字节）
 * @param allowedExtensions    允许的扩展名列表，空列表表示不限制
 * @param defaultConfig        是否为当前默认存储方案
 * @param createdAt            创建时间（UTC）
 * @param updatedAt            更新时间（UTC）
 */
public record StorageConfigResponse(String id, String code, Integer version, String name, String provider,
        String baseDir, String endpoint, String region, String bucket, boolean pathStyle,
        boolean credentialConfigured, long maxFileSize, List<String> allowedExtensions, boolean defaultConfig,
        Instant createdAt, Instant updatedAt) {
}
