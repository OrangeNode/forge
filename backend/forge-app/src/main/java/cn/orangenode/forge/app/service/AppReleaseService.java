package cn.orangenode.forge.app.service;

import cn.orangenode.forge.app.request.AppReleaseMinVersionRequest;
import cn.orangenode.forge.app.request.AppReleasePublishRequest;
import cn.orangenode.forge.app.response.AppReleaseResponse;

/**
 * App 版本更新用例：手机与电视共用一个 APK，只有一套版本配置。
 */
public interface AppReleaseService {

    /**
     * 读取当前版本配置。
     *
     * @return 版本配置，未发布时版本字段为空
     */
    AppReleaseResponse current();

    /**
     * 用已上传的 APK 发布新版本：解析包名、版本号与 SHA-256，生成公开外链。
     *
     * @param request    发布入参
     * @param operatorId 操作管理员 ID
     * @return 新的版本配置
     */
    AppReleaseResponse publish(AppReleasePublishRequest request, Long operatorId);

    /**
     * 调整最低可用版本。
     *
     * @param request    入参
     * @param operatorId 操作管理员 ID
     * @return 新的版本配置
     */
    AppReleaseResponse updateMinVersion(AppReleaseMinVersionRequest request, Long operatorId);

    /**
     * 撤销发布，App 不再提示更新。
     *
     * @param operatorId 操作管理员 ID
     * @return 清空后的版本配置
     */
    AppReleaseResponse withdraw(Long operatorId);
}
