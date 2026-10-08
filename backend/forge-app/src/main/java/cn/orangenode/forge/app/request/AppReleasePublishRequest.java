package cn.orangenode.forge.app.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发布新版本入参：APK 先通过文件管理上传，版本号、版本名、包名与 SHA-256 由服务端解析。
 *
 * @param fileId    APK 文件 ID
 * @param changelog 更新说明
 * @param force     是否强制更新：是则最低可用版本等于本版本，旧版本必须更新后才能使用
 */
public record AppReleasePublishRequest(
        @NotBlank(message = "请先上传 APK") String fileId,
        @Size(max = 1000, message = "更新说明不能超过 1000 个字符") String changelog,
        @NotNull(message = "请选择是否强制更新") Boolean force) {
}
