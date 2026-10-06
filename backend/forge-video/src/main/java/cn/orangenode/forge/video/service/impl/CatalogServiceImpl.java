package cn.orangenode.forge.video.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.video.collector.MacCmsParser;
import cn.orangenode.forge.video.converter.VideoConverter;
import cn.orangenode.forge.video.entity.CategoryEntity;
import cn.orangenode.forge.video.entity.SourceCategoryEntity;
import cn.orangenode.forge.video.entity.VideoEntity;
import cn.orangenode.forge.video.entity.OriginEntity;
import cn.orangenode.forge.video.entity.SourceEntity;
import cn.orangenode.forge.video.entity.PlayLineEntity;
import cn.orangenode.forge.video.entity.EpisodeEntity;
import cn.orangenode.forge.video.request.CategoryRequest;
import cn.orangenode.forge.video.request.VideoEditRequest;
import cn.orangenode.forge.video.request.LineEditRequest;
import cn.orangenode.forge.video.response.CategoryResponse;
import cn.orangenode.forge.video.response.VideoResponse;
import cn.orangenode.forge.video.response.VideoDetailResponse;
import cn.orangenode.forge.video.response.AppVideoResponse;
import cn.orangenode.forge.video.response.OriginResponse;
import cn.orangenode.forge.video.response.PlayLineResponse;
import cn.orangenode.forge.video.response.EpisodeResponse;
import cn.orangenode.forge.video.service.CatalogService;
import cn.orangenode.forge.video.support.VideoValues;
import cn.orangenode.forge.video.support.VideoPaging;
import lombok.RequiredArgsConstructor;

/**
 * 分类、影片维护与公开读取，后台实体不会直接返回给 App。
 */
@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {
    private final VideoStore store;
    private final VideoConverter converter;
    private final VideoPaging paging;
    private final MacCmsParser parser;

    /**
     * 按层级构造分类树，公开树过滤停用祖先的后代。
     */
    @Override
    public List<CategoryResponse> categories(boolean publicOnly) {
        List<CategoryEntity> all = store.categories.selectList(Wrappers.<CategoryEntity>lambdaQuery()
                .orderByAsc(CategoryEntity::getSortNo).orderByAsc(CategoryEntity::getId));
        return categoryTree(all, 0L, publicOnly);
    }

    /**
     * 组装指定父分类的子节点。
     */
    private List<CategoryResponse> categoryTree(List<CategoryEntity> all, Long parent, boolean publicOnly) {
        return all.stream().filter(e -> VideoValues.same(e.getParentId(), parent))
                .filter(e -> !publicOnly || Boolean.TRUE.equals(e.getEnabled()))
                .map(e -> new CategoryResponse(e.getId().toString(), e.getParentId().toString(), e.getName(),
                        e.getSortNo(), Boolean.TRUE.equals(e.getEnabled()), categoryTree(all, e.getId(), publicOnly)))
                .toList();
    }

    /**
     * 保存分类并防止自身或祖先循环。
     */
    @Override
    @Transactional
    public CategoryResponse saveCategory(Long id, CategoryRequest r) {
        Long parent = r.parentId() == null || r.parentId().isBlank() || "0".equals(r.parentId())
                ? 0L : VideoValues.id(r.parentId());
        Long cursor = parent;
        Set<Long> visited = new HashSet<>();
        while (cursor != 0L) {
            if (cursor.equals(id) || !visited.add(cursor)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "分类不能形成循环");
            }
            cursor = store.requireCategory(cursor).getParentId();
        }
        CategoryEntity e = id == null ? new CategoryEntity() : store.requireCategory(id);
        if (id == null) {
            e.setCreatedAt(VideoValues.now());
            e.setDeleted(0);
        }
        e.setParentId(parent);
        e.setName(r.name().strip());
        e.setSortNo(r.sortNo());
        e.setEnabled(r.enabled());
        e.setUpdatedAt(VideoValues.now());
        if (id == null) {
            store.categories.insert(e);
        } else {
            store.categories.updateById(e);
        }
        return new CategoryResponse(e.getId().toString(), parent.toString(), e.getName(), e.getSortNo(),
                Boolean.TRUE.equals(e.getEnabled()), List.of());
    }

    /**
     * 有子分类或影片时阻止删除；来源分类映射只是绑定配置，删除时自动解绑。
     */
    @Override
    @Transactional
    public void deleteCategory(Long id) {
        store.requireCategory(id);
        if (store.categories.selectCount(Wrappers.<CategoryEntity>lambdaQuery().eq(CategoryEntity::getParentId, id)) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "分类下还有子分类，请先删除或移动子分类");
        }
        if (store.videos.selectCount(Wrappers.<VideoEntity>lambdaQuery().eq(VideoEntity::getCategoryId, id)) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "分类下还有影片，请先移动或删除影片");
        }
        store.sourceCategories.update(null, Wrappers.<SourceCategoryEntity>lambdaUpdate()
                .set(SourceCategoryEntity::getCategoryId, null)
                .set(SourceCategoryEntity::getUpdatedAt, VideoValues.now())
                .eq(SourceCategoryEntity::getCategoryId, id));
        store.categories.deleteById(id);
    }

    /**
     * 构造具体业务筛选，不拼接用户排序 SQL。
     */
    private LambdaQueryWrapper<VideoEntity> filter(String keyword, String category, Integer year,
            String area, String language, Boolean published, boolean publicOnly) {
        LambdaQueryWrapper<VideoEntity> q = Wrappers.<VideoEntity>lambdaQuery()
                .like(VideoValues.clean(keyword) != null, VideoEntity::getName, VideoValues.clean(keyword))
                .eq(category != null && !category.isBlank(), VideoEntity::getCategoryId,
                        category == null || category.isBlank() ? null : VideoValues.id(category))
                .eq(year != null, VideoEntity::getYear, year)
                .eq(VideoValues.clean(area) != null, VideoEntity::getArea, VideoValues.clean(area))
                .eq(VideoValues.clean(language) != null, VideoEntity::getLanguage, VideoValues.clean(language))
                .eq(published != null, VideoEntity::getPublished, published);
        if (publicOnly) {
            List<Long> ids = store.categories.selectList(Wrappers.<CategoryEntity>lambdaQuery()).stream()
                    .map(CategoryEntity::getId).filter(store::categoryVisible).toList();
            if (ids.isEmpty()) {
                q.eq(VideoEntity::getId, -1L);
            } else {
                q.in(VideoEntity::getCategoryId, ids);
            }
            q.eq(VideoEntity::getPublished, true);
        }
        return q.orderByDesc(VideoEntity::getUpdatedAt).orderByDesc(VideoEntity::getId);
    }

    /**
     * 管理端分页允许查看下架影片。
     */
    @Override
    public PageResponse<VideoResponse> videos(Integer number, Integer size, String keyword, String category,
            Integer year, String area, String language, Boolean published) {
        Page<VideoEntity> p = store.videos.selectPage(paging.page(number, size),
                filter(keyword, category, year, area, language, published, false));
        return new PageResponse<>(p.getRecords().stream().map(converter::video).toList(),
                p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    /**
     * 公开分页只返回已上架且分类可用的影片，不含播放分集。
     */
    @Override
    public PageResponse<AppVideoResponse> appVideos(Integer number, Integer size, String keyword, String category,
            Integer year, String area, String language) {
        Page<VideoEntity> p = store.videos.selectPage(paging.page(number, size),
                filter(keyword, category, year, area, language, true, true));
        return new PageResponse<>(p.getRecords().stream().map(converter::appVideo).toList(),
                p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    /**
     * 读取管理端详情，来源资料与线路均结构化返回。
     */
    @Override
    public VideoDetailResponse detail(Long id) {
        VideoEntity video = store.requireVideo(id);
        List<OriginResponse> origins = store.origins.selectList(Wrappers.<OriginEntity>lambdaQuery()
                .eq(OriginEntity::getVideoId, id).orderByAsc(OriginEntity::getId)).stream()
                .map(e -> converter.origin(e, sourceName(e.getSourceId()))).toList();
        return new VideoDetailResponse(converter.video(video), origins, lines(id, false));
    }

    /**
     * 读取来源展示名称，已停用来源仍可显示历史名称。
     */
    private String sourceName(Long id) {
        SourceEntity source = store.sources.selectById(id);
        return source == null ? "历史来源" : source.getName();
    }

    /**
     * 校验公开访问资格，隐藏下架或停用分类的影片。
     */
    private VideoEntity publicVideo(Long id) {
        VideoEntity e = store.requireVideo(id);
        if (!Boolean.TRUE.equals(e.getPublished()) || !store.categoryVisible(e.getCategoryId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "影片不存在或暂不可用");
        }
        return e;
    }

    /**
     * 输出 App 详情，去掉资料锁定与来源管理字段。
     */
    @Override
    public AppVideoResponse appDetail(Long id) {
        return converter.appVideo(publicVideo(id));
    }

    /**
     * 查询各来源线路，公开调用不返回停用线路。
     */
    @Override
    public List<PlayLineResponse> lines(Long id, boolean publicOnly) {
        if (publicOnly) {
            publicVideo(id);
        } else {
            store.requireVideo(id);
        }
        List<PlayLineResponse> result = new ArrayList<>();
        for (OriginEntity origin : store.origins.selectList(Wrappers.<OriginEntity>lambdaQuery()
                .eq(OriginEntity::getVideoId, id).orderByAsc(OriginEntity::getId))) {
            for (PlayLineEntity line : store.playLines.selectList(Wrappers.<PlayLineEntity>lambdaQuery()
                    .eq(PlayLineEntity::getOriginId, origin.getId())
                    .eq(publicOnly, PlayLineEntity::getEnabled, true)
                    .orderByAsc(PlayLineEntity::getSortNo).orderByAsc(PlayLineEntity::getId))) {
                List<EpisodeResponse> episodes = store.episodes.selectList(Wrappers.<EpisodeEntity>lambdaQuery()
                        .eq(EpisodeEntity::getLineId, line.getId()).orderByAsc(EpisodeEntity::getSortNo))
                        .stream().map(converter::episode).toList();
                result.add(new PlayLineResponse(line.getId().toString(), origin.getId().toString(),
                        sourceName(origin.getSourceId()), line.getCode(), line.getName(), line.getSortNo(),
                        Boolean.TRUE.equals(line.getEnabled()), episodes.size(), episodes));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 手工编辑默认锁定资料，但不改变发布状态或来源线路。
     */
    @Override
    @Transactional
    public VideoResponse edit(Long id, VideoEditRequest r) {
        VideoEntity e = store.lockVideo(id);
        Long category = VideoValues.id(r.categoryId());
        store.requireCategory(category);
        e.setName(r.name().strip());
        e.setMatchName(VideoValues.normalize(r.name()));
        e.setCategoryId(category);
        e.setYear(r.year());
        e.setArea(VideoValues.clean(r.area()));
        e.setLanguage(VideoValues.clean(r.language()));
        e.setDirector(VideoValues.clean(r.director()));
        e.setActors(VideoValues.clean(r.actors()));
        e.setPosterUrl(VideoValues.clean(r.posterUrl()));
        e.setDescription(parser.plain(r.description()));
        e.setRemarks(VideoValues.clean(r.remarks()));
        e.setDeclaredTotal(r.declaredTotal());
        e.setEnded(r.ended());
        e.setMetadataLocked(r.metadataLocked() == null || r.metadataLocked());
        e.setUpdatedAt(VideoValues.now());
        store.videos.updateById(e);
        return converter.video(e);
    }

    /**
     * 主动上架必须有可用分类与至少一条启用的非空线路。
     */
    @Override
    @Transactional
    public void publish(Long id, boolean enabled) {
        VideoEntity e = store.lockVideo(id);
        if (enabled && (!store.categoryVisible(e.getCategoryId())
                || lines(id, false).stream().noneMatch(l -> l.enabled() && l.availableEpisodeCount() > 0))) {
            throw new BusinessException(ErrorCode.CONFLICT, "上架需要有效分类及非空的启用线路");
        }
        e.setPublished(enabled);
        e.setUpdatedAt(VideoValues.now());
        store.videos.updateById(e);
    }

    /**
     * 删除只改变影片逻辑状态，来源关联保留以阻止采集自动重建。
     */
    @Override
    @Transactional
    public void deleteVideo(Long id) {
        store.lockVideo(id);
        store.videos.deleteById(id);
    }

    /**
     * 主要来源必须属于该影片。
     */
    @Override
    @Transactional
    public void primary(Long id, Long originId) {
        VideoEntity e = store.lockVideo(id);
        if (!id.equals(store.requireOrigin(originId).getVideoId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "主要来源必须属于该影片");
        }
        e.setPrimaryOriginId(originId);
        e.setUpdatedAt(VideoValues.now());
        store.videos.updateById(e);
    }

    /**
     * 修改线路管理属性，采集不会恢复管理员停用的线路。
     */
    @Override
    @Transactional
    public void editLine(Long id, LineEditRequest r) {
        PlayLineEntity e = store.requirePlayLine(id);
        e.setName(r.name().strip());
        e.setSortNo(r.sortNo());
        e.setEnabled(r.enabled());
        e.setUpdatedAt(VideoValues.now());
        store.playLines.updateById(e);
    }
}
