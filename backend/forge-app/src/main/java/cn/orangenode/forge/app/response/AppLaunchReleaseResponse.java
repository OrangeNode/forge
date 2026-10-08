package cn.orangenode.forge.app.response;

/**
 * App 启动时读取的最新版本：当前版本号低于 {@code minVersionCode} 必须更新，低于 {@code versionCode} 提示可跳过的更新。
 *
 * @param versionCode    最新版本号
 * @param versionName    最新版本名
 * @param minVersionCode 最低可用版本号，为空表示不强制
 * @param apkUrl         APK 公开下载路径（相对接口基础地址）
 * @param sha256         APK 的 SHA-256（小写十六进制），安装前校验
 * @param sizeBytes      APK 字节数
 * @param changelog      更新说明
 */
public record AppLaunchReleaseResponse(int versionCode, String versionName, Integer minVersionCode, String apkUrl,
        String sha256, long sizeBytes, String changelog) {
}
