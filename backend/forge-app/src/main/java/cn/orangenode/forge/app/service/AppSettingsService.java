package cn.orangenode.forge.app.service;

import cn.orangenode.forge.app.request.AppFeatureRequest;
import cn.orangenode.forge.app.request.AppHotSearchRequest;
import cn.orangenode.forge.app.response.AppFeatureResponse;
import cn.orangenode.forge.app.response.AppHotSearchResponse;

/**
 * 后台 App 全局设置用例：功能开关与热搜。
 */
public interface AppSettingsService {

    /**
     * 读取功能开关。
     */
    AppFeatureResponse features();

    /**
     * 修改功能开关，下一次请求即生效。
     */
    AppFeatureResponse updateFeatures(AppFeatureRequest request);

    /**
     * 读取热搜设置。
     */
    AppHotSearchResponse hotSearch();

    /**
     * 修改热搜设置。
     */
    AppHotSearchResponse updateHotSearch(AppHotSearchRequest request);
}
