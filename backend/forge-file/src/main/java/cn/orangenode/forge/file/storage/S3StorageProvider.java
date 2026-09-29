package cn.orangenode.forge.file.storage;

import java.io.InputStream;
import java.net.URI;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.storage.StorageProvider;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3 兼容对象存储适配。
 *
 * <p>使用 AWS SDK v2 同步客户端：访问地址、区域、桶名称、path-style 开关与静态凭据全部来自
 * 存储配置版本，桶默认私有，读取一律经过后端授权，不对外返回桶地址。</p>
 *
 * <p>所有可预期的远端失败都转换为业务异常，对外只给出“存储目标不可用”一类结论：
 * 日志只记录 HTTP 状态码与对象存储错误码，不记录访问地址、区域、桶名称与凭据，
 * 异常也不携带原始 SDK 异常，避免 endpoint 或凭据经 500 堆栈外泄。</p>
 */
@Slf4j
public class S3StorageProvider implements StorageProvider, AutoCloseable {

    /**
     * 存储类型代码。
     */
    private static final String PROVIDER_CODE = "s3";

    /**
     * 对象不存在时对象存储返回的 HTTP 状态码。
     */
    private static final int NOT_FOUND_STATUS = 404;

    /**
     * 未配置区域时使用的默认区域。
     *
     * <p>S3 协议要求客户端必须携带区域；MinIO 等兼容服务忽略该值，
     * 因此这里给出协议默认值而不是把区域做成必填，真实对象存储仍应在配置中显式填写。</p>
     */
    private static final String DEFAULT_REGION = "us-east-1";

    /**
     * 桶名称。
     */
    private final String bucket;

    /**
     * 同步对象存储客户端，由本实例持有并在关闭时释放。
     */
    private final S3Client client;

    /**
     * 构造对象存储适配。
     *
     * @param endpoint   访问地址，允许为空表示使用对象存储服务默认地址
     * @param region     区域，允许为空表示使用协议默认区域
     * @param bucket     桶名称
     * @param pathStyle  是否使用 path-style 访问，MinIO 等兼容服务需要
     * @param accessKey  访问凭据明文，只在内存中用于构造客户端
     * @param secretKey  访问密钥明文，只在内存中用于构造客户端
     * @throws BusinessException 配置无法构造客户端时抛出 400
     */
    public S3StorageProvider(String endpoint, String region, String bucket, boolean pathStyle, String accessKey,
            String secretKey) {
        this.bucket = bucket;
        this.client = buildClient(endpoint, region, pathStyle, accessKey, secretKey);
    }

    /**
     * 返回存储类型代码。
     *
     * @return 固定为 {@code s3}
     */
    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    /**
     * 写入对象。
     *
     * @param objectKey     随机生成的对象键
     * @param content       对象内容，由调用者负责关闭
     * @param contentLength 内容长度（字节）
     * @param contentType   内容类型，可为空
     * @throws BusinessException 内容长度不合法时抛出 400，写入失败时抛出 503
     */
    @Override
    public void store(String objectKey, InputStream content, long contentLength, String contentType) {
        if (contentLength < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "对象长度不合法，无法写入对象存储");
        }
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentLength(contentLength);
        if (hasText(contentType)) {
            request.contentType(contentType.strip());
        }
        try {
            client.putObject(request.build(), RequestBody.fromInputStream(content, contentLength));
        } catch (S3Exception failure) {
            log.error("对象存储写入失败，statusCode={}，errorCode={}", failure.statusCode(), errorCodeOf(failure));
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "存储目标不可用，文件写入失败，请稍后重试");
        } catch (SdkException failure) {
            log.error("对象存储写入失败，sdkException={}", failure.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "存储目标不可用，文件写入失败，请稍后重试");
        }
    }

    /**
     * 按对象键读取对象。
     *
     * <p>返回的是对象存储的响应流，调用方关闭该流即释放连接；
     * 本适配实例由存储适配工厂缓存复用，不随单次读取关闭。</p>
     *
     * @param objectKey 随机生成的对象键
     * @return 对象内容，由调用者负责关闭
     * @throws BusinessException 对象不存在时抛出 404，读取失败时抛出 503
     */
    @Override
    public InputStream read(String objectKey) {
        try {
            return client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (S3Exception failure) {
            if (failure.statusCode() == NOT_FOUND_STATUS) {
                log.warn("对象存储中的对象不存在，objectKey={}", objectKey);
                throw new BusinessException(ErrorCode.NOT_FOUND, "文件内容不存在或已被清理");
            }
            log.error("对象存储读取失败，statusCode={}，errorCode={}", failure.statusCode(), errorCodeOf(failure));
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "存储目标不可用，文件读取失败，请稍后重试");
        } catch (SdkException failure) {
            log.error("对象存储读取失败，sdkException={}", failure.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "存储目标不可用，文件读取失败，请稍后重试");
        }
    }

    /**
     * 按对象键删除对象，对象不存在视为删除成功。
     *
     * @param objectKey 随机生成的对象键
     * @throws BusinessException 删除失败时抛出 503
     */
    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (S3Exception failure) {
            if (failure.statusCode() == NOT_FOUND_STATUS) {
                log.debug("对象存储中的对象已不存在，视为删除成功，objectKey={}", objectKey);
                return;
            }
            log.error("对象存储删除失败，statusCode={}，errorCode={}", failure.statusCode(), errorCodeOf(failure));
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "存储目标不可用，文件删除失败，请稍后重试");
        } catch (SdkException failure) {
            log.error("对象存储删除失败，sdkException={}", failure.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "存储目标不可用，文件删除失败，请稍后重试");
        }
    }

    /**
     * 通过查询桶元信息检测存储目标是否可用。
     *
     * <p>桶不存在、凭据无效或地址不可达都会失败，对外统一提示，不区分具体原因，
     * 避免把访问地址与桶名称的存在性暴露给调用方。</p>
     *
     * @throws BusinessException 目标不可用时抛出 503
     */
    @Override
    public void verify() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception failure) {
            log.warn("对象存储连接检测失败，statusCode={}，errorCode={}", failure.statusCode(), errorCodeOf(failure));
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "存储目标不可用，请检查访问地址、桶名称与凭据");
        } catch (SdkException failure) {
            log.warn("对象存储连接检测失败，sdkException={}", failure.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "存储目标不可用，请检查访问地址、桶名称与凭据");
        }
    }

    /**
     * 关闭对象存储客户端并释放其连接资源。
     *
     * <p>由存储适配工厂在目标配置被修改或删除时调用。</p>
     */
    @Override
    public void close() {
        client.close();
    }

    /**
     * 构造同步对象存储客户端。
     *
     * @param endpoint   访问地址，允许为空
     * @param region     区域，允许为空
     * @param pathStyle  是否使用 path-style 访问
     * @param accessKey  访问凭据明文
     * @param secretKey  访问密钥明文
     * @return 已装配的同步客户端
     * @throws BusinessException 配置无法构造客户端时抛出 400
     */
    private static S3Client buildClient(String endpoint, String region, boolean pathStyle, String accessKey,
            String secretKey) {
        try {
            S3ClientBuilder builder = S3Client.builder()
                    .region(Region.of(hasText(region) ? region.strip() : DEFAULT_REGION))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());
            if (hasText(endpoint)) {
                builder.endpointOverride(URI.create(endpoint.strip()));
            }
            return builder.build();
        } catch (RuntimeException failure) {
            log.error("对象存储客户端创建失败，配置明细已省略");
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "存储方案配置不正确，无法创建对象存储客户端，请检查访问地址、区域与桶名称");
        }
    }

    /**
     * 读取对象存储错误码，只用于服务端日志。
     *
     * @param failure 对象存储服务异常
     * @return 错误码，无法取得时返回 {@code unknown}
     */
    private static String errorCodeOf(S3Exception failure) {
        if (failure.awsErrorDetails() == null) {
            return "unknown";
        }
        return failure.awsErrorDetails().errorCode();
    }

    /**
     * 判断文本是否非空。
     *
     * @param value 待判断文本，允许为 {@code null}
     * @return 去空格后非空时返回 {@code true}
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
