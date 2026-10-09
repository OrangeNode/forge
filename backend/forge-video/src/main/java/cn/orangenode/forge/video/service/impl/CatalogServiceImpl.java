package cn.orangenode.forge.video.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.video.collector.MacCmsClient;
import cn.orangenode.forge.video.collector.MacCmsParser;
import cn.orangenode.forge.video.collector.RemoteImage;
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
import cn.orangenode.forge.video.response.CollectionBatchResponse;
import cn.orangenode.forge.video.response.VideoResponse;
import cn.orangenode.forge.video.response.VideoDetailResponse;
import cn.orangenode.forge.video.response.AppVideoResponse;
import cn.orangenode.forge.video.response.OriginResponse;
import cn.orangenode.forge.video.response.PlayLineResponse;
import cn.orangenode.forge.video.response.EpisodeResponse;
import cn.orangenode.forge.video.service.CatalogService;
import cn.orangenode.forge.video.support.PinyinInitials;
import cn.orangenode.forge.video.support.VideoValues;
import cn.orangenode.forge.video.support.VideoPaging;
import lombok.RequiredArgsConstructor;

/**
 * 分类、影片维护与公开读取，后台实体不会直接返回给 App。
 */
@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {
    /**
     * App 端可直接播放的地址类型；OTHER 为网页分享地址，电视与手机播放器无法播放。
     */
    private static final List<String> PLAYABLE_URL_TYPES = List.of("M3U8", "MP4");
    /**
     * 只由字母数字组成的关键字同时按片名拼音首字母前缀匹配（电视遥控器字母键盘输入）。
     */
    private static final Pattern INITIALS_KEYWORD = Pattern.compile("[A-Za-z0-9]{1,32}");
    private final VideoStore store;
    private final VideoConverter converter;
    private final VideoPaging paging;
    private final MacCmsParser parser;
    private final TransactionTemplate transactions;
    private final MacCmsClient client;

    /**
     * 按层级构造分类树，公开树过滤停用祖先的后代。
     */
    @Override
    public List<CategoryResponse> categories(boolean publicOnly) {
        List<CategoryEntity> all = store.categories.selectList(Wrappers.<CategoryEntity>lambdaQuery()
                .orderByAsc(CategoryEntity::getSortNo).orderByAsc(CategoryEntity::getId));
        return categoryTree(all, 0L, publicOnly, publicOnly ? null : videoCounts());
    }

    /**
     * 按分类统计未删除影片数量，逻辑删除条件由 MyBatis-Plus 自动附加。
     */
    private Map<Long, Long> videoCounts() {
        return store.videos.selectMaps(Wrappers.<VideoEntity>query()
                        .select("category_id AS categoryId", "COUNT(*) AS total").groupBy("category_id"))
                .stream().filter(row -> column(row, "categoryId") != null)
                .collect(Collectors.toMap(row -> column(row, "categoryId").longValue(),
                        row -> column(row, "total").longValue()));
    }

    /**
     * 按列别名读取统计值，忽略不同数据库对未加引号别名的大小写处理差异。
     */
    private Number column(Map<String, Object> row, String name) {
        return row.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(entry -> (Number) entry.getValue()).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    /**
     * 组装指定父分类的子节点；公开树不返回影片数量。
     */
    private List<CategoryResponse> categoryTree(List<CategoryEntity> all, Long parent, boolean publicOnly,
            Map<Long, Long> counts) {
        return all.stream().filter(e -> VideoValues.same(e.getParentId(), parent))
                .filter(e -> !publicOnly || Boolean.TRUE.equals(e.getEnabled()))
                .map(e -> new CategoryResponse(e.getId().toString(), e.getParentId().toString(), e.getName(),
                        e.getSortNo(), Boolean.TRUE.equals(e.getEnabled()),
                        counts == null ? null : counts.getOrDefault(e.getId(), 0L),
                        categoryTree(all, e.getId(), publicOnly, counts)))
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
                Boolean.TRUE.equals(e.getEnabled()), store.videos.selectCount(Wrappers.<VideoEntity>lambdaQuery()
                        .eq(VideoEntity::getCategoryId, e.getId())), List.of());
    }

    /**
     * 有子分类时阻止删除；分类下影片一并逻辑删除，来源分类映射自动解绑。
     */
    @Override
    @Transactional
    public void deleteCategory(Long id) {
        removeCategory(id);
    }

    /**
     * 先固定目标并按层级从深到浅排序，使同时勾选的父子分类可以一次删除；
     * 每条分类使用独立事务，业务冲突逐条返回，不承诺整批原子性；分类下影片一并逻辑删除。
     */
    @Override
    public CollectionBatchResponse deleteCategories(List<Long> ids) {
        Map<Long, Long> parents = store.categories.selectList(Wrappers.<CategoryEntity>lambdaQuery()).stream()
                .collect(Collectors.toMap(CategoryEntity::getId, CategoryEntity::getParentId));
        List<Long> ordered = ids.stream().distinct()
                .sorted(Comparator.comparingInt((Long id) -> depth(id, parents)).reversed())
                .toList();
        List<CollectionBatchResponse.Failure> failures = new ArrayList<>();
        int succeeded = 0;
        for (Long id : ordered) {
            try {
                transactions.executeWithoutResult(status -> removeCategory(id));
                succeeded++;
            } catch (BusinessException failure) {
                failures.add(new CollectionBatchResponse.Failure(id.toString(), failure.getMessage()));
            }
        }
        return new CollectionBatchResponse(ordered.size(), succeeded, failures);
    }

    /**
     * 计算分类所在层级，不存在的编号按顶级处理并在删除时返回原因；遇到异常循环立即停止。
     */
    private int depth(Long id, Map<Long, Long> parents) {
        int depth = 0;
        Set<Long> visited = new HashSet<>();
        Long cursor = parents.get(id);
        while (cursor != null && cursor != 0L && visited.add(cursor)) {
            depth++;
            cursor = parents.get(cursor);
        }
        return depth;
    }

    /**
     * 执行单个分类删除校验与解绑，并逻辑删除该分类下全部影片（来源关联保留，重采不会恢复），由调用方提供事务。
     */
    private void removeCategory(Long id) {
        store.requireCategory(id);
        if (store.categories.selectCount(Wrappers.<CategoryEntity>lambdaQuery().eq(CategoryEntity::getParentId, id)) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "分类下还有子分类，请先删除或移动子分类");
        }
        store.videos.delete(Wrappers.<VideoEntity>lambdaQuery().eq(VideoEntity::getCategoryId, id));
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
        String text = VideoValues.clean(keyword);
        String initials = text != null && INITIALS_KEYWORD.matcher(text).matches() ? text.toLowerCase(Locale.ROOT) : null;
        LambdaQueryWrapper<VideoEntity> q = Wrappers.<VideoEntity>lambdaQuery()
                .and(text != null, w -> w.like(VideoEntity::getName, text)
                        .or(initials != null, o -> o.likeRight(VideoEntity::getNameInitials, initials)))
                .eq(!publicOnly && category != null && !category.isBlank(), VideoEntity::getCategoryId,
                        category == null || category.isBlank() ? null : VideoValues.id(category))
                .eq(year != null, VideoEntity::getYear, year)
                .eq(VideoValues.clean(area) != null, VideoEntity::getArea, VideoValues.clean(area))
                .eq(VideoValues.clean(language) != null, VideoEntity::getLanguage, VideoValues.clean(language))
                .eq(published != null, VideoEntity::getPublished, published);
        if (publicOnly) {
            // App 按分类筛选时包含其全部可见子分类，选一级分类即可看到二级分类下的影片。
            List<CategoryEntity> all = store.categories.selectList(Wrappers.<CategoryEntity>lambdaQuery());
            Long root = category == null || category.isBlank() ? null : VideoValues.id(category);
            Map<Long, Long> parents = new HashMap<>();
            all.forEach(item -> parents.put(item.getId(), item.getParentId()));
            List<Long> ids = all.stream().map(CategoryEntity::getId).filter(store::categoryVisible)
                    .filter(id -> root == null || underCategory(id, root, parents)).toList();
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
     * 分类是否为给定分类本身或其后代；层级异常成环时按不属于处理。
     */
    private static boolean underCategory(Long id, Long root, Map<Long, Long> parents) {
        Long current = id;
        for (int depth = 0; current != null && current != 0L && depth < parents.size() + 1; depth++) {
            if (current.equals(root)) {
                return true;
            }
            current = parents.get(current);
        }
        return false;
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
     * 按影片当前海报地址代取图片，不缓存、不落盘。
     */
    @Override
    public RemoteImage poster(Long id) {
        VideoEntity video = store.requireVideo(id);
        if (video.getPosterUrl() == null || video.getPosterUrl().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该影片没有海报");
        }
        return client.image(video.getPosterUrl().trim());
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
     * 查询各来源线路，公开调用不返回停用线路、不可播放类型的分集及因此为空的线路。
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
                        .eq(EpisodeEntity::getLineId, line.getId())
                        .in(publicOnly, EpisodeEntity::getUrlType, PLAYABLE_URL_TYPES)
                        .orderByAsc(EpisodeEntity::getSortNo))
                        .stream().map(converter::episode).toList();
                if (publicOnly && episodes.isEmpty()) {
                    continue;
                }
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
        e.setNameInitials(PinyinInitials.of(r.name()));
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
