package cn.orangenode.forge.video.service;

import java.util.List;
import cn.orangenode.forge.core.page.PageResponse;
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

/**
 * SourceService业务用例契约。
 */
public interface SourceService {
    /**
     * 查询资源站列表。
     */
    List<SourceResponse> list();

    /**
     * 维护来源配置，不自动启动采集。
     */
    SourceResponse save(Long id, SourceRequest request);

    /**
     * 删除无引用来源。
     */
    void delete(Long id);

    /**
     * 读取本地缓存的来源分类。
     */
    List<SourceCategoryResponse> categories(Long id);

    /**
     * 通过公开接口读取来源分类并保留已有映射。
     */
    List<SourceCategoryResponse> refresh(Long id);

    /**
     * 将来源分类关联到本地分类。
     */
    void map(Long id, MappingRequest request);

    /**
     * 匹配未绑定分类，可创建缺少分类，保留已有人工绑定。
     */
    cn.orangenode.forge.video.response.AutoMappingResponse autoMap(Long id, boolean createMissing);
}
