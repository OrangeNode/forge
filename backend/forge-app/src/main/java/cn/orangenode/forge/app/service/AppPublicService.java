package cn.orangenode.forge.app.service;

import cn.orangenode.forge.app.response.AppHomeResponse;
import cn.orangenode.forge.app.response.AppHotWordsResponse;
import cn.orangenode.forge.app.response.AppLaunchResponse;
import cn.orangenode.forge.app.response.AppMarqueeResponse;
import cn.orangenode.forge.framework.storage.StoredFileContent;

/**
 * App 端读取用例：启动配置、首页数据、热搜与配置引用的图片。
 */
public interface AppPublicService {

    /**
     * 读取启动配置：当前生效的开屏与公开开关。
     */
    AppLaunchResponse launch();

    /**
     * 读取首页轮播与推荐行。
     */
    AppHomeResponse home();

    /**
     * 读取已排除屏蔽词的热搜词。
     */
    AppHotWordsResponse hotWords();

    /**
     * 读取播放页滚动文字；未启用或文字为空时返回空。
     */
    AppMarqueeResponse playerMarquee();

    /**
     * 打开开屏或轮播引用的图片；未被引用或不是图片返回 404。
     */
    StoredFileContent image(Long fileId);
}
