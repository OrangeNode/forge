package cn.orangenode.forge.framework.catalog;

import java.util.List;
import java.util.Optional;

/**
 * 影片目录只读端口。
 *
 * <p>由视频模块实现，App 设置模块通过它挑选首页影片、校验轮播跳转目标，
 * 不直接依赖视频模块的实体、Mapper 或服务。只返回已上架且分类可见的影片。</p>
 */
public interface VideoCatalogPort {

    /**
     * 按分类（包含其可见子分类）读取最近更新的公开影片。
     *
     * @param categoryId 分类 ID
     * @param limit      最多返回条数
     * @return 影片卡片，按更新时间倒序
     */
    List<VideoCard> latestByCategory(Long categoryId, int limit);

    /**
     * 按给定顺序读取公开影片，不可用的影片被跳过。
     *
     * @param ids 影片 ID 列表
     * @return 影片卡片，顺序与入参一致
     */
    List<VideoCard> findPublic(List<Long> ids);

    /**
     * 读取未删除影片的名称，供后台展示与校验。
     *
     * @param videoId 影片 ID
     * @return 影片名称，不存在时为空
     */
    Optional<String> videoName(Long videoId);

    /**
     * 读取未删除分类的名称，供后台展示与校验。
     *
     * @param categoryId 分类 ID
     * @return 分类名称，不存在时为空
     */
    Optional<String> categoryName(Long categoryId);
}
