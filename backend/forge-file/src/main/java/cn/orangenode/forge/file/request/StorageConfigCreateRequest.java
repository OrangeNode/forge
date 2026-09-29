package cn.orangenode.forge.file.request;

import cn.orangenode.forge.file.policy.FileStoragePolicy;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增存储配置入参。
 *
 * <p>每次新增都创建一个新版本：同代码下版本号由服务端按已有最大版本递增，调用方不能指定版本。
 * 目标字段按存储类型区分：{@code provider=local} 时相对目录必填，{@code provider=s3} 时
 * 访问地址、桶名称、访问凭据与访问密钥必填，服务端按类型校验，不相关的字段会被清空。</p>
 *
 * <p>{@code accessKey} 与 {@code secretKey} 是明文凭据，只在保存时使用：
 * 加密后落库，不写入日志、审计与响应；接口只回显“是否已配置凭据”。</p>
 *
 * @param code              存储方案代码，字母开头，含字母、数字、下划线与连字符，保存时转小写
 * @param name              方案名称
 * @param provider          存储类型，{@code local} 或 {@code s3}
 * @param baseDir           本地存储相对目录（{@code provider=local} 必填），必须位于环境根目录之内
 * @param endpoint          对象存储访问地址（{@code provider=s3} 必填），带 http 或 https
 * @param region            对象存储区域（{@code provider=s3} 可选），为空时使用协议默认区域
 * @param bucket            对象存储桶名称（{@code provider=s3} 必填）
 * @param pathStyle         是否使用 path-style 访问，为空时按启用处理，MinIO 等兼容服务需要
 * @param accessKey         访问凭据明文（{@code provider=s3} 必填），加密保存
 * @param secretKey         访问密钥明文（{@code provider=s3} 必填），加密保存
 * @param maxFileSize       单文件大小上限（字节），不得高于应用上传硬上限
 * @param allowedExtensions 允许的扩展名，逗号分隔，可带前导点；为空表示不限制
 */
public record StorageConfigCreateRequest(
        @NotBlank(message = "存储方案代码不能为空")
        @Size(max = FileStoragePolicy.CODE_MAX_LENGTH, message = "存储方案代码长度不能超过 64")
        @Pattern(regexp = FileStoragePolicy.CODE_PATTERN,
                message = "存储方案代码必须以字母开头，只能包含字母、数字、下划线与连字符")
        String code,

        @NotBlank(message = "存储方案名称不能为空")
        @Size(max = FileStoragePolicy.NAME_MAX_LENGTH, message = "存储方案名称长度不能超过 64")
        String name,

        @NotBlank(message = "存储类型不能为空")
        @Pattern(regexp = FileStoragePolicy.PROVIDER_PATTERN, message = "存储类型只能是 local 或 s3")
        String provider,

        @Size(max = FileStoragePolicy.DIRECTORY_MAX_LENGTH, message = "本地存储相对目录长度不能超过 255")
        String baseDir,

        @Size(max = FileStoragePolicy.ENDPOINT_MAX_LENGTH, message = "对象存储访问地址长度不能超过 255")
        String endpoint,

        @Size(max = FileStoragePolicy.REGION_MAX_LENGTH, message = "对象存储区域长度不能超过 64")
        String region,

        @Size(max = FileStoragePolicy.BUCKET_MAX_LENGTH, message = "对象存储桶名称长度不能超过 128")
        String bucket,

        Boolean pathStyle,

        @Size(max = FileStoragePolicy.ACCESS_KEY_MAX_LENGTH, message = "访问凭据长度不能超过 512")
        String accessKey,

        @Size(max = FileStoragePolicy.SECRET_KEY_MAX_LENGTH, message = "访问密钥长度不能超过 1024")
        String secretKey,

        @NotNull(message = "单文件大小上限不能为空")
        @Min(value = 1, message = "单文件大小上限必须大于 0")
        Long maxFileSize,

        @Size(max = FileStoragePolicy.ALLOWED_EXTENSIONS_MAX_LENGTH, message = "允许扩展名长度不能超过 512")
        String allowedExtensions) {
}
