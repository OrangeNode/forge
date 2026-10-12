package cn.orangenode.forge.video.service;

import java.util.List;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.video.collector.RemoteImage;
import cn.orangenode.forge.video.request.CategoryRequest;
import cn.orangenode.forge.video.request.SourceRequest;
import cn.orangenode.forge.video.request.MappingRequest;
import cn.orangenode.forge.video.request.VideoEditRequest;
import cn.orangenode.forge.video.request.LineEditRequest;
import cn.orangenode.forge.video.request.SettingsRequest;
import cn.orangenode.forge.video.response.CategoryResponse;
import cn.orangenode.forge.video.response.SourceResponse;
import cn.orangenode.forge.video.response.SourceCategoryResponse;
import cn.orangenode.forge.video.response.VideoResponse;
import cn.orangenode.forge.video.response.VideoDetailResponse;
import cn.orangenode.forge.video.response.AppVideoResponse;
import cn.orangenode.forge.video.response.PlayLineResponse;
import cn.orangenode.forge.video.response.SettingsResponse;
import cn.orangenode.forge.video.response.CollectionBatchResponse;

/**
 * CatalogService业务用例契约。
 */
public interface CatalogService {
    /**
     * 查询分类树。
     */
    List<CategoryResponse> categories(boolean publicOnly);

    /**
     * 维护分类并拒绝循环。
     */
    CategoryResponse saveCategory(Long id, CategoryRequest request);

    /**
     * 拒绝删除被引用分类。
     */
    void deleteCategory(Long id);

    /**
     * 批量删除分类，子分类先于父分类处理，逐条返回未删除原因。
     */
    CollectionBatchResponse deleteCategories(List<Long> ids);

    /**
     * 查询管理端影片分页。
     */
    PageResponse<VideoResponse> videos(Integer pageNum, Integer pageSize, String keyword, String categoryId, Integer year, String area, String language, Boolean published);

    /**
     * 查询公开影片分页。
     */
    PageResponse<AppVideoResponse> appVideos(Integer pageNum, Integer pageSize, String keyword, String categoryId, Integer year, String area, String language);

    /**
     * 查询管理端影片及播放数据。
     */
    VideoDetailResponse detail(Long id);

    /**
     * 代取影片海报图片，供后台按模板生成轮播图；影片不存在 404，没有海报 400，远程失败 503。
     */
    RemoteImage poster(Long id);

    /**
     * 查询公开影片详情。
     */
    AppVideoResponse appDetail(Long id);

    /**
     * 查询结构化播放线路及分集；公开查询的线路名只给中文展示名，来源名置空。
     */
    List<PlayLineResponse> lines(Long id, boolean publicOnly);

    /**
     * 编辑影片并默认锁定资料。
     */
    VideoResponse edit(Long id, VideoEditRequest request);

    /**
     * 管理员主动上下架。
     */
    void publish(Long id, boolean enabled);

    /**
     * 逻辑删除影片并保留来源防止重采恢复。
     */
    void deleteVideo(Long id);

    /**
     * 更换影片主要来源。
     */
    void primary(Long id, Long originId);

    /**
     * 维护线路显示顺序与启停。
     */
    void editLine(Long id, LineEditRequest request);
}
