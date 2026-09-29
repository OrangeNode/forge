package cn.orangenode.forge.file.storage;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.file.entity.FileStorageConfigEntity;
import cn.orangenode.forge.file.policy.FileStoragePolicy;
import cn.orangenode.forge.framework.config.ForgeFileProperties;
import cn.orangenode.forge.framework.crypto.CredentialCipher;
import cn.orangenode.forge.framework.storage.StorageProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * 存储适配工厂。
 *
 * <p>按存储配置版本构造 {@link StorageProvider}：本地存储使用环境配置的根目录加配置里的相对目录，
 * 对象存储使用 {@link CredentialCipher} 解密后的凭据。解密失败、配置不完整或类型不受支持时，
 * 都给出明确的中文业务错误，不把凭据与主密钥信息写进日志。</p>
 *
 * <p>构造结果按配置版本 ID 缓存并记录生成它的目标签名：一个目标对应一个适配实例，
 * 避免每次上传都新建对象存储客户端；目标或凭据变化、配置被修改或删除时替换并释放旧实例，
 * 因此缓存不会返回已经失效的目标。</p>
 */
@Slf4j
@Component
public class StorageProviderFactory {

    /**
     * 文件与存储配置，提供本地存储根目录。
     */
    private final ForgeFileProperties properties;

    /**
     * 凭据加解密端口，用于读取对象存储凭据。
     */
    private final CredentialCipher credentialCipher;

    /**
     * 已构造的存储适配实例，键为存储配置版本 ID。
     */
    private final Map<Long, CachedStorageProvider> providers = new ConcurrentHashMap<>();

    /**
     * 构造存储适配工厂。
     *
     * @param properties       文件与存储配置
     * @param credentialCipher 凭据加解密端口
     */
    public StorageProviderFactory(ForgeFileProperties properties, CredentialCipher credentialCipher) {
        this.properties = properties;
        this.credentialCipher = credentialCipher;
    }

    /**
     * 按存储配置版本取得适配实例。
     *
     * @param config 存储配置版本
     * @return 可直接用于读写的存储适配
     * @throws BusinessException 配置不完整、凭据无法解密或类型不受支持时抛出
     */
    public StorageProvider create(FileStorageConfigEntity config) {
        if (config == null || config.getId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "存储配置版本不完整，无法创建存储目标");
        }
        String signature = targetSignature(config);
        CachedStorageProvider cached = providers.get(config.getId());
        if (cached != null && cached.targetSignature().equals(signature)) {
            return cached.provider();
        }
        StorageProvider provider = build(config);
        providers.put(config.getId(), new CachedStorageProvider(signature, provider));
        closeQuietly(cached);
        return provider;
    }

    /**
     * 释放指定配置版本缓存的适配实例。
     *
     * <p>配置被修改或删除后调用：旧实例持有的对象存储客户端与连接需要及时释放，
     * 也保证下一次读取使用新的目标。</p>
     *
     * @param configId 存储配置版本 ID，允许为 {@code null}
     */
    public void evict(Long configId) {
        if (configId == null) {
            return;
        }
        closeQuietly(providers.remove(configId));
    }

    /**
     * 按存储类型构造适配实例。
     *
     * @param config 存储配置版本
     * @return 存储适配
     * @throws BusinessException 类型不受支持或配置不完整时抛出
     */
    private StorageProvider build(FileStorageConfigEntity config) {
        String provider = FileStoragePolicy.normalizeProvider(config.getProvider());
        if (FileStoragePolicy.PROVIDER_LOCAL.equals(provider)) {
            return buildLocal(config);
        }
        if (FileStoragePolicy.PROVIDER_S3.equals(provider)) {
            return buildS3(config);
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "存储方案类型不受支持，无法创建存储目标");
    }

    /**
     * 构造本地存储适配。
     *
     * @param config 存储配置版本
     * @return 本地存储适配
     * @throws BusinessException 根目录未配置或路径不合法时抛出
     */
    private StorageProvider buildLocal(FileStorageConfigEntity config) {
        String localRoot = properties.getLocalRoot();
        if (localRoot == null || localRoot.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "未配置本地存储根目录，无法创建存储目标");
        }
        try {
            return new LocalStorageProvider(Path.of(localRoot.strip()), config.getBaseDir());
        } catch (InvalidPathException failure) {
            log.error("本地存储根目录配置不是合法路径");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "本地存储根目录配置不正确，无法创建存储目标");
        }
    }

    /**
     * 构造对象存储适配。
     *
     * @param config 存储配置版本
     * @return 对象存储适配
     * @throws BusinessException 配置不完整、凭据无法解密或客户端无法创建时抛出
     */
    private StorageProvider buildS3(FileStorageConfigEntity config) {
        if (!hasText(config.getEndpoint()) || !hasText(config.getBucket())
                || !hasText(config.getAccessKey()) || !hasText(config.getSecretKey())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "对象存储方案配置不完整，无法创建存储目标");
        }
        return new S3StorageProvider(config.getEndpoint(), config.getRegion(), config.getBucket(),
                isPathStyle(config), decryptCredential(config.getAccessKey(), "访问凭据"),
                decryptCredential(config.getSecretKey(), "访问密钥"));
    }

    /**
     * 解密存储凭据。
     *
     * @param cipherText 凭据密文
     * @param subject    凭据用途的中文说明，用于错误提示
     * @return 凭据明文
     * @throws BusinessException 主密钥缺失或密文无法解密时抛出
     */
    private String decryptCredential(String cipherText, String subject) {
        try {
            String plainText = credentialCipher.decrypt(cipherText);
            if (plainText == null || plainText.isEmpty()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "存储方案" + subject + "为空，无法创建存储目标");
            }
            return plainText;
        } catch (BusinessException failure) {
            log.error("存储方案凭据解密失败，配置明细已省略");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "存储方案" + subject + "解密失败，请检查凭据加密主密钥配置", failure);
        }
    }

    /**
     * 判断配置是否启用 path-style 访问。
     *
     * @param config 存储配置版本
     * @return 配置值为 1 时返回 {@code true}
     */
    private static boolean isPathStyle(FileStorageConfigEntity config) {
        return config.getPathStyle() != null && config.getPathStyle() != 0;
    }

    /**
     * 计算目标签名，用于判断缓存的适配实例是否仍然对应当前配置。
     *
     * <p>签名只参与内存比较，不写日志、不进响应；凭据使用密文而不是明文参与计算。</p>
     *
     * @param config 存储配置版本
     * @return 目标签名
     */
    private static String targetSignature(FileStorageConfigEntity config) {
        return String.join("|",
                text(config.getProvider()),
                text(config.getBaseDir()),
                text(config.getEndpoint()),
                text(config.getRegion()),
                text(config.getBucket()),
                text(config.getPathStyle()),
                text(config.getAccessKey()),
                text(config.getSecretKey()));
    }

    /**
     * 关闭缓存中的适配实例。
     *
     * @param cached 缓存条目，允许为 {@code null}
     */
    private static void closeQuietly(CachedStorageProvider cached) {
        if (cached == null) {
            return;
        }
        if (cached.provider() instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception failure) {
                log.warn("存储适配实例关闭失败，provider={}", cached.provider().providerCode());
            }
        }
    }

    /**
     * 把可空值转换为签名片段。
     *
     * @param value 配置值，允许为 {@code null}
     * @return 字符串片段，入参为空时返回空串
     */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    /**
     * 缓存中的存储适配实例与生成它的目标签名。
     *
     * @param targetSignature 目标签名，配置变化时与缓存的签名不一致
     * @param provider        存储适配实例
     */
    private record CachedStorageProvider(String targetSignature, StorageProvider provider) {
    }
}
