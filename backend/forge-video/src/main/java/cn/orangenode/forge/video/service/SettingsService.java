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
 * SettingsService业务用例契约。
 */
public interface SettingsService {
    /**
     * 读取全局采集规则。
     */
    SettingsResponse get();

    /**
     * 保存全局规则，不重新合并历史数据。
     */
    SettingsResponse save(SettingsRequest request);
}
