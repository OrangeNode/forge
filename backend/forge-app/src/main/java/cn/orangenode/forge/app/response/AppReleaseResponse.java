package cn.orangenode.forge.app.response;

/**
 * 后台版本更新信息；未发布时版本相关字段均为空。
 *
 * @param versionCode     最新版本号
 * @param versionName     最新版本名
 * @param minVersionCode  最低可用版本号，为空表示不强制
 * @param packageName     APK 包名
 * @param apkFileId       APK 文件 ID
 * @param apkUrl          APK 公开下载路径（不含域名）
 * @param sha256          APK 的 SHA-256
 * @param sizeBytes       APK 字节数
 * @param changelog       更新说明
 * @param updatedAt       最后修改时间
 * @param expectedPackage 服务端要求的包名
 */
public record AppReleaseResponse(Integer versionCode, String versionName, Integer minVersionCode, String packageName,
        String apkFileId, String apkUrl, String sha256, Long sizeBytes, String changelog, String updatedAt,
        String expectedPackage) {
}
