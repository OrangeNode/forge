package cn.orangenode.forge.app.service;

import java.util.List;

import cn.orangenode.forge.app.request.AppBannerRequest;
import cn.orangenode.forge.app.request.AppSectionRequest;
import cn.orangenode.forge.app.request.AppSplashRequest;
import cn.orangenode.forge.app.response.AppBannerResponse;
import cn.orangenode.forge.app.response.AppSectionResponse;
import cn.orangenode.forge.app.response.AppSplashResponse;

/**
 * 后台 App 首页内容维护用例：开屏、轮播与推荐位。
 */
public interface AppContentService {

    /**
     * 查询全部开屏，按顺序排列。
     */
    List<AppSplashResponse> splashes();

    /**
     * 新增或修改开屏，id 为空表示新增；图片文件不存在返回 400。
     */
    AppSplashResponse saveSplash(Long id, AppSplashRequest request);

    /**
     * 逻辑删除开屏。
     */
    void deleteSplash(Long id);

    /**
     * 查询全部轮播，按顺序排列。
     */
    List<AppBannerResponse> banners();

    /**
     * 新增或修改轮播，跳转目标不存在返回 400。
     */
    AppBannerResponse saveBanner(Long id, AppBannerRequest request);

    /**
     * 逻辑删除轮播。
     */
    void deleteBanner(Long id);

    /**
     * 查询全部推荐位，按顺序排列。
     */
    List<AppSectionResponse> sections();

    /**
     * 新增或修改推荐位，分类或影片不存在返回 400。
     */
    AppSectionResponse saveSection(Long id, AppSectionRequest request);

    /**
     * 逻辑删除推荐位。
     */
    void deleteSection(Long id);
}
