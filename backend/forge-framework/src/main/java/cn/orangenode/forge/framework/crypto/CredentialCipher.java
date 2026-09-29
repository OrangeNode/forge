package cn.orangenode.forge.framework.crypto;

/**
 * 凭据加解密端口。
 *
 * <p>用于保存对象存储凭据等敏感配置：写入前加密、读取时解密，主密钥只在运行环境中。</p>
 */
public interface CredentialCipher {

    /**
     * 加密明文凭据。
     *
     * @param plainText 明文，允许为空
     * @return 密文，明文为空时返回 {@code null}
     */
    String encrypt(String plainText);

    /**
     * 解密密文凭据。
     *
     * @param cipherText 密文，允许为空
     * @return 明文，密文为空时返回 {@code null}
     */
    String decrypt(String cipherText);
}
