package cn.orangenode.forge.framework.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.config.ForgeFileProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 AES-GCM 的凭据加解密实现。
 *
 * <p>每次加密都生成新的随机 nonce（12 字节），与密文一起以 Base64 保存，因此同一明文不会产生相同密文；
 * 主密钥为 Base64 编码的 32 字节值，只从环境变量读取，不进入源码、响应或日志。</p>
 *
 * <p>缺少主密钥或密钥长度不符时抛出可预期的业务异常：本地存储不依赖主密钥，
 * 只有保存或读取 S3 凭据才会失败，错误信息不包含密钥内容。</p>
 */
@Slf4j
@Component
public class AesGcmCredentialCipher implements CredentialCipher {

    /**
     * 加密算法与填充方式。
     */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /**
     * GCM 认证标签长度（位）。
     */
    private static final int TAG_LENGTH_BITS = 128;

    /**
     * 随机 nonce 长度（字节），AES-GCM 推荐 12 字节。
     */
    private static final int NONCE_LENGTH = 12;

    /**
     * 主密钥要求的字节数，对应 AES-256。
     */
    private static final int KEY_LENGTH = 32;

    /**
     * 文件与存储配置，提供主密钥。
     */
    private final ForgeFileProperties properties;

    /**
     * 随机数发生器，用于生成 nonce。
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 构造凭据加解密实现。
     *
     * @param properties 文件与存储配置
     */
    public AesGcmCredentialCipher(ForgeFileProperties properties) {
        this.properties = properties;
    }

    /**
     * 加密明文凭据。
     *
     * @param plainText 明文，允许为空
     * @return Base64 编码的 nonce + 密文，明文为空时返回 {@code null}
     */
    @Override
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, resolveSecretKey(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (BusinessException failure) {
            throw failure;
        } catch (Exception failure) {
            log.error("凭据加密失败", failure);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "凭据加密失败，请检查主密钥配置");
        }
    }

    /**
     * 解密密文凭据。
     *
     * @param cipherText Base64 编码的 nonce + 密文，允许为空
     * @return 明文，密文为空时返回 {@code null}
     */
    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            if (combined.length <= NONCE_LENGTH) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "凭据密文格式不正确，无法解密");
            }
            byte[] nonce = new byte[NONCE_LENGTH];
            System.arraycopy(combined, 0, nonce, 0, NONCE_LENGTH);
            byte[] encrypted = new byte[combined.length - NONCE_LENGTH];
            System.arraycopy(combined, NONCE_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, resolveSecretKey(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (BusinessException failure) {
            throw failure;
        } catch (IllegalArgumentException failure) {
            log.error("凭据密文不是合法的 Base64 内容", failure);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "凭据密文格式不正确，无法解密");
        } catch (Exception failure) {
            log.error("凭据解密失败，通常是主密钥与密文不匹配", failure);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "凭据解密失败，请检查主密钥配置");
        }
    }

    /**
     * 读取并校验主密钥。
     *
     * @return AES 密钥
     * @throws BusinessException 未配置主密钥或长度不符时抛出
     */
    private SecretKey resolveSecretKey() {
        String configured = properties.getEncryptionKey();
        if (configured == null || configured.isBlank()) {
            log.error("未配置 forge.file.encryption-key，无法加解密存储凭据");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "未配置凭据加密主密钥，无法保存或读取对象存储凭据");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException failure) {
            log.error("凭据加密主密钥不是合法的 Base64 内容", failure);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "凭据加密主密钥格式不正确");
        }
        if (keyBytes.length != KEY_LENGTH) {
            log.error("凭据加密主密钥长度不正确，期望 {} 字节，实际 {} 字节", KEY_LENGTH, keyBytes.length);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "凭据加密主密钥长度不正确");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
