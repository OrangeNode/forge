package cn.orangenode.forge.file.support;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.web.util.UriUtils;

/**
 * 文件公开外链规则：随机令牌生成与公开下载路径拼装。
 *
 * <p>公开路径不含域名，由调用方按访问入口拼接；令牌是 18 字节随机数的 URL 安全 Base64（24 个字符），
 * 不可由文件 ID 推算。路径末尾的文件名只用于让浏览器与下载器得到正确的文件名，查找只看令牌。</p>
 */
public final class FilePublicLinks {

    /**
     * 公开下载接口路径前缀，与匿名放行配置 {@code forge.security.permit-all-paths} 保持一致。
     */
    public static final String PATH_PREFIX = "/api/public/v1/files/";

    /**
     * 令牌随机字节数。
     */
    private static final int TOKEN_BYTES = 18;

    /**
     * 令牌随机源。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 工具类不允许实例化。
     */
    private FilePublicLinks() {
    }

    /**
     * 生成新的公开令牌。
     *
     * @return URL 安全的随机令牌
     */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 拼装公开下载路径。
     *
     * @param token        公开令牌，为空时表示未公开
     * @param originalName 原始文件名
     * @return 相对路径，未公开时为 {@code null}
     */
    public static String path(String token, String originalName) {
        if (token == null) {
            return null;
        }
        return PATH_PREFIX + token + "/" + UriUtils.encodePathSegment(originalName, StandardCharsets.UTF_8);
    }
}
