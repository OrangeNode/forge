package cn.orangenode.forge.app.request;

import jakarta.validation.constraints.Min;

/**
 * 调整最低可用版本入参。
 *
 * @param minVersionCode 最低可用版本号，为空表示不强制更新；不能高于最新版本号
 */
public record AppReleaseMinVersionRequest(@Min(value = 1, message = "版本号至少为 1") Integer minVersionCode) {
}
