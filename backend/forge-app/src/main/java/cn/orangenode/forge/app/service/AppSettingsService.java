package cn.orangenode.forge.app.service;

import cn.orangenode.forge.app.request.AppFeatureRequest;
import cn.orangenode.forge.app.request.AppHotSearchRequest;
import cn.orangenode.forge.app.request.AppMarqueeRequest;
import cn.orangenode.forge.app.response.AppFeatureResponse;
import cn.orangenode.forge.app.response.AppHotSearchResponse;
import cn.orangenode.forge.app.response.AppMarqueeResponse;

/**
 * 后台 App 全局设置用例：功能开关、热搜与播放页滚动文字。
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

    /**
     * 读取播放页滚动文字。
     */
    AppMarqueeResponse marquee();

    /**
     * 修改播放页滚动文字，App 下一次打开播放页即生效。
     */
    AppMarqueeResponse updateMarquee(AppMarqueeRequest request);
}
